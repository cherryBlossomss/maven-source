/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugin.internal;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Print warnings if deprecated mojo or parameters of plugin are used in configuration.
 *
 * @author Slawomir Jaranowski
 */
@Component(role = MavenPluginConfigurationValidator.class)
class DeprecatedPluginValidator extends AbstractMavenPluginParametersValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeprecatedPluginValidator.class);

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    protected String getParameterLogReason(Parameter parameter) {
        return "is deprecated: " + parameter.getDeprecated();
    }

    @Override
    public void validate(
            MojoDescriptor mojoDescriptor,
            PlexusConfiguration pomConfiguration,
            ExpressionEvaluator expressionEvaluator) {
        if (!LOGGER.isWarnEnabled()) {
            return;
        }

        if (mojoDescriptor.getDeprecated() != null) {
            logDeprecatedMojo(mojoDescriptor);
        }

        if (mojoDescriptor.getParameters() == null) {
            return;
        }

        mojoDescriptor.getParameters().stream()
                .filter(parameter -> parameter.getDeprecated() != null)
                .filter(Parameter::isEditable)
                .forEach(parameter -> checkParameter(parameter, pomConfiguration, expressionEvaluator));
    }

    private void checkParameter(
            Parameter parameter, PlexusConfiguration pomConfiguration, ExpressionEvaluator expressionEvaluator) {
        PlexusConfiguration config = pomConfiguration.getChild(parameter.getName(), false);

        if (isValueSet(config, expressionEvaluator)) {
            logParameter(parameter);
        }
    }

    private void logDeprecatedMojo(MojoDescriptor mojoDescriptor) {
        String message = MessageUtils.buffer()
                .warning("Goal '")
                .warning(mojoDescriptor.getGoal())
                .warning("' is deprecated: ")
                .warning(mojoDescriptor.getDeprecated())
                .toString();

        LOGGER.warn(message);
    }
}
