package org.opendaylight.usc.test.manager;


import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.usc.manager.UscConfigurationServiceImpl;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.test.AbstractUscTest;

public class UscConfigurationTest extends AbstractUscTest{
    UscConfigurationServiceImpl configService = UscConfigurationServiceImpl.getInstance();
    
    @Test
    public void test(){
        Assert.assertEquals(configService.getConfigIntValue(UscConfigurationService.USC_PLUGIN_PORT), 1069);
        Assert.assertEquals(configService.getConfigIntValue(UscConfigurationService.USC_AGENT_PORT), 1068);
        Assert.assertEquals(configService.getConfigIntValue(UscConfigurationService.USC_MAX_ERROR_NUMER), 100);
        Assert.assertEquals(configService.getConfigIntValue(UscConfigurationService.USC_MAX_THREAD_NUMBER), 100);
        Assert.assertEquals(configService.getConfigStringValue(UscConfigurationService.SECURITY_FILES_ROOT), "src/test/resources/etc/usc/certificates");
        Assert.assertEquals(configService.getConfigStringValue(UscConfigurationService.TRUST_CERTIFICATE_CHAIN_FILE), "rootCA.pem");
        Assert.assertEquals(configService.getConfigStringValue(UscConfigurationService.PUBLIC_CERTIFICATE_CHAIN_FILE), "client.pem");
        Assert.assertEquals(configService.getConfigStringValue(UscConfigurationService.PRIVATE_KEY_FILE), "client.key.pem");
        Assert.assertEquals(configService.getConfigStringValue(UscConfigurationService.AKKA_CLUSTER_FILE), "src/test/resources/etc/usc/akka.conf");
        Assert.assertEquals(configService.isConfigAsTure(UscConfigurationService.USC_LOG_ERROR_EVENT), true);
    }
}
