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

import org.junit.jupiter.api.Test;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HtmlPluginTest {

    @Test
    void extensions() {
        SonarRuntime sonarRuntime = mock(SonarRuntime.class);
        Plugin.Context context = new Plugin.Context(sonarRuntime);
        new HtmlPlugin().define(context);
        assertThat(context.getExtensions()).hasSize(2);
    }

}
