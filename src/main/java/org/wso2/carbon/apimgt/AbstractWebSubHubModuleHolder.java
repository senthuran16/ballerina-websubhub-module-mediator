/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt;

import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.internal.configurable.VariableKey;
import io.ballerina.runtime.internal.types.BStringType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractWebSubHubModuleHolder {
    protected Module module;
    protected Module configModule;
    protected String configContent;
    protected List<VariableKey> configKeysList = new ArrayList<>();

    protected void init(Map<String, Object> hubProperties) {
        module = generateModule();
        configModule = generateConfigModule();
        configContent = populateAndGetConfigContent(hubProperties);
    }

    protected String populateAndGetConfigContent(Map<String, Object> hubProperties) {
        StringBuilder configContent = new StringBuilder();
        configContent.append("[");
        configContent.append(getConfigSpaceName());
        configContent.append("]\n");
        for (Map.Entry<String, Object> propertyEntry : hubProperties.entrySet()) {
            configKeysList.add(
                    new VariableKey(configModule, propertyEntry.getKey(),
                            new BStringType("string", module), false));
            configContent.append(propertyEntry.getKey())
                    .append(" = \"")
                    .append(propertyEntry.getValue())
                    .append("\"\n");
        }
        return configContent.toString();
    }

    protected abstract Module generateModule();

    public Module getModule() {
        return module;
    }

    protected abstract Module generateConfigModule();

    public Module getConfigModule() {
        return configModule;
    }

    public String getConfigContent() {
        return configContent;
    }

    abstract protected String getConfigSpaceName();

    public List<VariableKey> getConfigKeysList() {
        return configKeysList;
    }
}
