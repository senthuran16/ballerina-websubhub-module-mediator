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
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BXml;
import io.ballerina.runtime.internal.configurable.VariableKey;
import io.ballerina.runtime.internal.launch.LaunchUtils;
import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.ballerina.stdlib.mi.BXmlConverter;
import org.wso2.carbon.apimgt.ballerina.stdlib.mi.Constants;
import org.wso2.carbon.apimgt.ballerina.stdlib.mi.OMElementConverter;
import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.wso2.carbon.apimgt.impl.dto.WebSubHubConfig;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import static org.wso2.carbon.apimgt.ballerina.stdlib.mi.Constants.BOOLEAN;
import static org.wso2.carbon.apimgt.ballerina.stdlib.mi.Constants.DECIMAL;
import static org.wso2.carbon.apimgt.ballerina.stdlib.mi.Constants.FLOAT;
import static org.wso2.carbon.apimgt.ballerina.stdlib.mi.Constants.INT;
import static org.wso2.carbon.apimgt.ballerina.stdlib.mi.Constants.JSON;
import static org.wso2.carbon.apimgt.ballerina.stdlib.mi.Constants.STRING;
import static org.wso2.carbon.apimgt.ballerina.stdlib.mi.Constants.XML;

public class BallerinaWebSubHubWrapperMediator extends AbstractMediator {
    private static final Log log = LogFactory.getLog(BallerinaWebSubHubWrapperMediator.class);

    private static volatile Runtime rt = null;
    private static Module module = null;

    public BallerinaWebSubHubWrapperMediator() {
        if (rt == null) {
            synchronized (BallerinaWebSubHubWrapperMediator.class) {
                if (rt == null) {
                    init();
                }
            }
        }
    }

    private static String getResultProperty(MessageContext context) {
        return lookupTemplateParameter(context, Constants.RESPONSE_VARIABLE).toString();
    }

    public boolean mediate(MessageContext context) {
        String balFunctionReturnType = context.getProperty(Constants.RETURN_TYPE).toString();
        final CountDownLatch latch = new CountDownLatch(1);
        Callback returnCallback = new Callback() {
            public void notifySuccess(Object result) {
                Object res = result;
                if (Objects.equals(balFunctionReturnType, XML)) {
                    res = BXmlConverter.toOMElement((BXml) result);
                } else if (Objects.equals(balFunctionReturnType, DECIMAL)) {
                    res = ((BDecimal) result).value().toString();
                } else if (Objects.equals(balFunctionReturnType, STRING)) {
                    res = ((BString) res).getValue();
                } else if (result instanceof BMap) {
                    res = result.toString();
                }
                if (log.isDebugEnabled()) {
                    log.debug("mediate returned successful result. Class: " + res.getClass() +
                            " , Result: " + res.toString());
                }
//                ConnectorResponse connectorResponse = new DefaultConnectorResponse();
//                connectorResponse.setPayload(result);
//                context.setProperty(getResultProperty(context) + ".payload", connectorResponse.getPayload());
//                context.setProperty(getResultProperty(context) + ".payload", result);
                context.setProperty(getResultProperty(context) + ".payload", res);

                latch.countDown();
            }

            public void notifyFailure(BError result) {
                handleException(result.getMessage(), context);
                latch.countDown();
            }
        };

        Object[] args = new Object[Integer.parseInt(context.getProperty(Constants.SIZE).toString())];
        if (!setParameters(args, context)) {
            return false;
        }
        rt.invokeMethodAsync(context.getProperty(Constants.FUNCTION_NAME).toString(), returnCallback, args);
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
        return true;
    }

    private boolean setParameters(Object[] args, MessageContext context) {
        for (int i = 0; i < args.length; i++) {
            Object param = getParameter(context, "param" + i, "paramType" + i);
            if (param == null) {
                return false;
            }
            args[i] = param;
        }
        return true;
    }

    private Object getParameter(MessageContext context, String value, String type) {
        String paramName = context.getProperty(value).toString();
        Object param = lookupTemplateParameter(context, paramName);
        if (param == null) {
            log.error("Error in getting the ballerina function parameter: " + paramName);
            return null;
        }
        String paramType = context.getProperty(type).toString();
        switch (paramType) {
            case BOOLEAN:
                return Boolean.parseBoolean((String) param);
            case INT:
                return Long.parseLong((String) param);
            case STRING:
                return StringUtils.fromString((String) param);
            case FLOAT:
                return Double.parseDouble((String) param);
            case DECIMAL:
                return ValueCreator.createDecimalValue((String) param);
            case JSON:
                return getBMapParameter(param);
            case XML:
                return getBXmlParameter(context, value);
            default:
                return null;
        }
    }

    private BXml getBXmlParameter(MessageContext context, String parameterName) {
        OMElement omElement = getOMElement(context, parameterName);
        if (omElement == null) {
            return null;
        }
        return OMElementConverter.toBXml(omElement);
    }

    private OMElement getOMElement(MessageContext ctx, String value) {
        String param = ctx.getProperty(value).toString();
        if (lookupTemplateParameter(ctx, param) != null) {
            return (OMElement) lookupTemplateParameter(ctx, param);
        }
        log.error("Error in getting the OMElement");
        return null;
    }

    public static Object lookupTemplateParameter(MessageContext ctx, String paramName) {
        return ctx.getProperty(paramName);
    }

    private BMap getBMapParameter(Object param) {
        if (param instanceof String) {
            return (BMap) JsonUtils.parse((String) param);
        } else {
            return (BMap) JsonUtils.parse(param.toString());
        }
    }

    private void init() {
        try {
            WebSubHubConfig webSubHubConfig = ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService()
                    .getAPIManagerConfiguration().getWebSubHubConfig();
            if (webSubHubConfig == null || !webSubHubConfig.isEnabled()) {
                log.error("WebSub Hub is not enabled, hence not initializing the mediator.");
                return;
            }

            String hub = webSubHubConfig.getHub();
            AbstractWebSubHubModuleHolder moduleHolder = WebSubModuleHolderResolver.resolveWebSubHubModuleHolder(hub);
            if (moduleHolder == null) {
                log.error("WebSub Hub module holder is not found for the hub: " + hub);
                return;
            }
            moduleHolder.init(webSubHubConfig.getHubProperties());
            module = moduleHolder.getModule();

            // Load the configurations
            Map<Module, VariableKey[]> configData = new HashMap<>();
            VariableKey[] configKeys = moduleHolder.getConfigKeysList().toArray(new VariableKey[0]);
            LaunchUtils.addModuleConfigData(configData, module, configKeys);
            LaunchUtils.initConfigurableVariables(
                    module, configData, new String[0], new Path[0], moduleHolder.getConfigContent());

            // Start the runtime
            rt = Runtime.from(module);
            rt.init();
            rt.start();
            if (log.isDebugEnabled()) {
                log.debug("Started the Ballerina runtime from module: " + module);
            }
            initHub();
        } catch (Exception e) {
            log.error("Failed to initialize the WebSub Hub", e);
        }
    }

    private void initHub() {
        String balFunctionReturnType = "json";
        final CountDownLatch latch = new CountDownLatch(1);
        Callback returnCallback = new Callback() {
            public void notifySuccess(Object result) {
                Object res = result;
                if (Objects.equals(balFunctionReturnType, XML)) {
                    res = BXmlConverter.toOMElement((BXml) result);
                } else if (Objects.equals(balFunctionReturnType, DECIMAL)) {
                    res = ((BDecimal) result).value().toString();
                } else if (Objects.equals(balFunctionReturnType, STRING)) {
                    res = ((BString) res).getValue();
                } else if (result instanceof BMap) {
                    res = result.toString();
                }
                log.info("WebSub Hub initialized successfully. " + res);
                System.out.println("WebSub Hub initialized successfully. " + res);
                latch.countDown();
            }

            public void notifyFailure(BError result) {
                log.error("WebSub Hub initialization failed. " + result.getMessage());
                latch.countDown();
            }
        };

        rt.invokeMethodAsync("init", returnCallback, new Object[0]);
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }
}
