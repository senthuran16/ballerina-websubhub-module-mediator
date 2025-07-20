package org.wso2.carbon.apimgt;

import io.ballerina.runtime.api.Module;

public class JMSWebSubHubModuleHolder extends AbstractWebSubHubModuleHolder {


    @Override
    protected Module generateModule() {
        return new Module("wso2", "jmshub", "0");
    }

    @Override
    protected Module generateConfigModule() {
        return new Module("wso2", "jmshub.config", "0");
    }

    @Override
    protected String getConfigSpaceName() {
        return "wso2.jmshub";
    }
}
