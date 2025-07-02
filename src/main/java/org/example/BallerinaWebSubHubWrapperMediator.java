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

package org.example;

import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.async.Callback; // TODO For Old JDK versions
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BXml;
import org.apache.axiom.om.OMElement;
//import org.apache.synapse.data.connector.ConnectorResponse;
//import org.apache.synapse.data.connector.DefaultConnectorResponse;
import org.example.ballerina.stdlib.mi.ModuleInfo;
import org.example.ballerina.stdlib.mi.BXmlConverter;
import org.example.ballerina.stdlib.mi.Constants;
import org.example.ballerina.stdlib.mi.OMElementConverter;
//import org.apache.synapse.data.connector.ConnectorResponse;
//import org.apache.synapse.data.connector.DefaultConnectorResponse;
import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.template.TemplateContext;

import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;

import static org.example.ballerina.stdlib.mi.Constants.BOOLEAN;
import static org.example.ballerina.stdlib.mi.Constants.DECIMAL;
import static org.example.ballerina.stdlib.mi.Constants.FLOAT;
import static org.example.ballerina.stdlib.mi.Constants.INT;
import static org.example.ballerina.stdlib.mi.Constants.JSON;
import static org.example.ballerina.stdlib.mi.Constants.STRING;
import static org.example.ballerina.stdlib.mi.Constants.XML;

public class BallerinaWebSubHubWrapperMediator extends AbstractMediator {
    private static volatile Runtime rt = null;
    private static Module module = null;

    public BallerinaWebSubHubWrapperMediator() {
        if (rt == null) {
            synchronized (BallerinaWebSubHubWrapperMediator.class) {
                if (rt == null) {
                    ModuleInfo moduleInfo = new ModuleInfo();
                    init(moduleInfo);
                }
            }
        }
    }

    // This constructor is added to test the mediator
    public BallerinaWebSubHubWrapperMediator(ModuleInfo moduleInfo) {
        init(moduleInfo);
    }

    private static String getResultProperty(MessageContext context) {
        return lookupTemplateParameter(context, Constants.RESPONSE_VARIABLE).toString();
    }

    // TODO Experimenting: Java17 - https://github.com/wso2-extensions/ballerina-mi-module-gen-tool/blob/c79553122812f84963668c52b06acf7dadbdddfe/native/src/main/java/io/ballerina/stdlib/mi/Mediator.java
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
                System.out.println(">>>>>>>>>> RESULT: " + res.getClass() + " - " + res.toString());
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

    // TODO 2201.11.0 - JAVA 21 - WORKS
//    public boolean mediate_2201_11_0(MessageContext context) {
//        String balFunctionReturnType = context.getProperty(Constants.RETURN_TYPE).toString();
//        Object[] args = new Object[Integer.parseInt(context.getProperty(Constants.SIZE).toString())];
//        if (!setParameters(args, context)) {
//            return false;
//        }
//        try {
//            Object result = rt.callFunction(module, context.getProperty(Constants.FUNCTION_NAME).toString(), null, args); // TODO WORKED IN 2201.11.0
//            if (Objects.equals(balFunctionReturnType, XML)) {
//                result = BXmlConverter.toOMElement((BXml) result);
//            } else if (Objects.equals(balFunctionReturnType, DECIMAL)) {
//                result = ((BDecimal) result).value().toString();
//            } else if (Objects.equals(balFunctionReturnType, STRING)) {
//                result = ((BString) result).getValue();
//            } else if (result instanceof BMap) {
//                result = result.toString();
//            }
//            System.out.println(">>>>>>>>>> RESULT: " + result.getClass() + " - " + result.toString());
//
//            ConnectorResponse connectorResponse = new DefaultConnectorResponse();
//            connectorResponse.setPayload(result);
//            context.setProperty(getResultProperty(context) + ".payload", connectorResponse.getPayload());
////            context.setProperty(getResultProperty(context), connectorResponse);
////            context.setVariable(getResultProperty(context), null); // TODO SENTHURAN - Temporary. Get rid of this
//        } catch (BError bError) {
//            handleException(bError.getMessage(), context);
//        }
//        return true;
//    }

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
//        Stack funcStack = (Stack) ctx.getProperty(Constants.SYNAPSE_FUNCTION_STACK);
//        TemplateContext currentFuncHolder = (TemplateContext) funcStack.peek();
//        return currentFuncHolder.getParameterValue(paramName);

        return ctx.getProperty(paramName);
    }

    private BMap getBMapParameter(Object param) {
        if (param instanceof String) {
            return (BMap) JsonUtils.parse((String) param);
        } else {
            return (BMap) JsonUtils.parse(param.toString());
        }
    }

    private void init(ModuleInfo moduleInfo) { // TODO INIT CONSOLIDATOR FIRST
        try { // TODO Remove this big try catch block
            System.out.println(">>>>>>>>>>>>>>>>>> BallerinaWebSubMediator init");
            Class mqFactoryClass = Class.forName("com.ibm.mq.jms.MQQueueConnectionFactory");
            Class jmsInterfaceClass = Class.forName("javax.jms.ConnectionFactory");

            System.out.println(">>>>>>>>>>>>>>>>>> [DEBUG] MQQueueConnectionFactory classloader: " +
                    mqFactoryClass.getClassLoader());
            System.out.println(">>>>>>>>>>>>>>>>>> [DEBUG] ConnectionFactory classloader:       " +
                    jmsInterfaceClass.getClassLoader());

            module = new Module(moduleInfo.getOrgName(), moduleInfo.getModuleName(), moduleInfo.getModuleVersion());
//        module = new Module("wso2", "kafkaHub", "0"); // TODO KAFKA HUB
            module = new Module("wso2", "jmshub", "0");
            rt = Runtime.from(module);
            rt.init();
            rt.start();
        } catch (Exception e) {
            System.err.println(">>>>>>>>>>>>>>>>>> Error initializing BallerinaWebSubHubWrapperMediator: " +
                    e.getMessage());
            e.printStackTrace();
        }
    }

//    private void initConsolidator() {
//        Module consolidatorModule = new Module("wso2", "consolidator", "0");
//        Runtime consolidatorRuntime = Runtime.from(consolidatorModule);
//        consolidatorRuntime.init();
//        consolidatorRuntime.start();
//
//        // Hardcode and mention the function stuff
//        consolidatorRuntime.rt.callFunction(consolidatorModule, "initConsolidator", null, new Object[] {});
//    }
}
