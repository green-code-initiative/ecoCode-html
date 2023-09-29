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
package io.ecocode.html.checks;

import org.sonar.check.Rule;
import org.sonar.plugins.html.checks.AbstractPageCheck;
import org.sonar.plugins.html.node.TagNode;

@Rule(key = AvoidAutoplayCheck.KEY)
public class AvoidAutoplayCheck extends AbstractPageCheck {

    public static final String KEY = "EC8000";

    @Override
    public void startElement(TagNode node) {
        if (isAudioTag(node) && hasAutoplayAttribute(node)) {
            createViolation(node, "Avoid using autoplay attribute in audio element");
        } else if (isVideoTag(node) && hasAutoplayAttribute(node)) {
            createViolation(node, "Avoid using autoplay attribute in video element");
        }
    }

    private static boolean isAudioTag(TagNode node) {
        return "AUDIO".equalsIgnoreCase(node.getNodeName());
    }

    private static boolean isVideoTag(TagNode node) {
        return "VIDEO".equalsIgnoreCase(node.getNodeName());
    }

    private static boolean hasAutoplayAttribute(TagNode node) {
        return node.hasProperty("AUTOPLAY");
    }

}
