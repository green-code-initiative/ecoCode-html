/*
 * ecoCode HTML plugin - Provides rules to reduce the environmental footprint of your HTML programs
 * Copyright © 2023 Green Code Initiative (https://www.ecocode.io)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.ecocode.html;

import org.sonar.api.SonarProduct;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.measures.Metric;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.html.api.HtmlConstants;
import org.sonar.plugins.html.checks.AbstractPageCheck;
import org.sonar.plugins.html.checks.HtmlIssue;
import org.sonar.plugins.html.checks.PreciseHtmlIssue;
import org.sonar.plugins.html.lex.PageLexer;
import org.sonar.plugins.html.lex.VueLexer;
import org.sonar.plugins.html.visitor.HtmlAstScanner;
import org.sonar.plugins.html.visitor.HtmlSourceCode;

import javax.annotation.Nonnull;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This sensor is based on the SonarQube one.
 * Adapted to work with our custom rules and without metrics already computed by Sonar HTML.
 *
 * @link <a href="https://github.com/SonarSource/sonar-html/blob/master/sonar-html-plugin/src/main/java/org/sonar/plugins/html/core/HtmlSensor.java">HtmlSensor.java</a>
 */
public class HtmlSensor implements Sensor {

    private static final Logger LOG = Loggers.get(HtmlSensor.class);

    private static final String[] OTHER_FILE_SUFFIXES = {"php", "php3", "php4", "php5", "phtml", "inc", "vue"};

    private final Checks<Object> checks;

    private final SonarRuntime sonarRuntime;

    public HtmlSensor(CheckFactory checkFactory, SonarRuntime sonarRuntime) {
        this.checks = checkFactory.create(HtmlRulesDefinition.REPOSITORY_KEY).addAnnotatedChecks(CheckList.getChecks());
        this.sonarRuntime = sonarRuntime;
    }

    @Override
    public void describe(@Nonnull SensorDescriptor descriptor) {
        descriptor.name(HtmlConstants.LANGUAGE_NAME).onlyOnFileType(InputFile.Type.MAIN);
        processesFilesIndependently(descriptor);
    }

    @Override
    public void execute(@Nonnull SensorContext sensorContext) {
        FileSystem fileSystem = sensorContext.fileSystem();

        // configure page scanner and the visitors
        final HtmlAstScanner scanner = setupScanner();

        FilePredicates predicates = fileSystem.predicates();
        Iterable<InputFile> inputFiles = fileSystem.inputFiles(
                predicates.and(
                        predicates.hasType(InputFile.Type.MAIN),
                        predicates.or(
                                predicates.hasLanguages(HtmlConstants.LANGUAGE_KEY, HtmlConstants.JSP_LANGUAGE_KEY),
                                predicates.or(Stream.of(OTHER_FILE_SUFFIXES).map(predicates::hasExtension).toArray(FilePredicate[]::new))
                        )
                ));

        for (InputFile inputFile : inputFiles) {
            if (sensorContext.isCancelled()) {
                return;
            }

            HtmlSourceCode sourceCode = new HtmlSourceCode(inputFile);

            try (Reader reader = new InputStreamReader(inputFile.inputStream(), inputFile.charset())) {
                PageLexer lexer = inputFile.filename().endsWith(".vue") ? new VueLexer() : new PageLexer();
                scanner.scan(lexer.parse(reader), sourceCode);
                saveMetrics(sensorContext, sourceCode);
            } catch (Exception e) {
                LOG.error("Cannot analyze file " + inputFile, e);
                sensorContext.newAnalysisError()
                        .onFile(inputFile)
                        .message(e.getMessage())
                        .save();
            }
        }
    }

    private static void saveMetrics(SensorContext context, HtmlSourceCode sourceCode) {
        InputFile inputFile = sourceCode.inputFile();

        for (Map.Entry<Metric<Integer>, Integer> entry : sourceCode.getMeasures().entrySet()) {
            context.<Integer>newMeasure()
                    .on(inputFile)
                    .forMetric(entry.getKey())
                    .withValue(entry.getValue())
                    .save();
        }

        for (HtmlIssue issue : sourceCode.getIssues()) {
            NewIssue newIssue = context.newIssue()
                    .forRule(issue.ruleKey())
                    .gap(issue.cost());
            NewIssueLocation location = locationForIssue(inputFile, issue, newIssue);
            newIssue.at(location);
            newIssue.save();
        }
    }

    private static NewIssueLocation locationForIssue(InputFile inputFile, HtmlIssue issue, NewIssue newIssue) {
        NewIssueLocation location = newIssue.newLocation()
                .on(inputFile)
                .message(issue.message());
        Integer line = issue.line();
        if (issue instanceof PreciseHtmlIssue) {
            PreciseHtmlIssue preciseHtmlIssue = (PreciseHtmlIssue) issue;
            location.at(inputFile.newRange(issue.line(),
                    preciseHtmlIssue.startColumn(),
                    preciseHtmlIssue.endLine(),
                    preciseHtmlIssue.endColumn()));
        } else if (line != null) {
            location.at(inputFile.selectLine(line));
        }
        return location;
    }

    private void processesFilesIndependently(SensorDescriptor descriptor) {
        if ((sonarRuntime.getProduct() == SonarProduct.SONARLINT)
                || !sonarRuntime.getApiVersion().isGreaterThanOrEqual(Version.create(9, 3))) {
            return;
        }
        descriptor.processesFilesIndependently();
    }

    private HtmlAstScanner setupScanner() {
        HtmlAstScanner scanner = new HtmlAstScanner(Collections.emptyList());

        for (Object check : checks.all()) {
            ((AbstractPageCheck) check).setRuleKey(checks.ruleKey(check));
            scanner.addVisitor((AbstractPageCheck) check);
        }

        return scanner;
    }

}
