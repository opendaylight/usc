package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101;

import org.opendaylight.usc.UscProvider;
import org.opendaylight.usc.manager.UscConfigurationManager;
import org.opendaylight.usc.manager.UscManager;
import org.opendaylight.usc.manager.api.UscConfiguration;
/**
 * 
 * Module class, generated from yang model at initialization phase but need modify as need
 */
public class UscImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.AbstractUscImplModule {
    public UscImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public UscImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.UscImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        UscProvider provider = new UscProvider();
        getBrokerDependency().registerProvider(provider);
        UscManager.getInstance().init(getDataBrokerDependency());
        //initialize configuration properties
        UscConfigurationManager.getInstance().initProperty(UscConfiguration.USC_AGENT_PORT, this.getUscAgentPort());
        UscConfigurationManager.getInstance().initProperty(UscConfiguration.USC_PLUGIN_PORT, this.getUscPluginPort());
        UscConfigurationManager.getInstance().initProperty(UscConfiguration.USC_MAX_ERROR_NUMER, this.getMaxErrorNumber());
        UscConfigurationManager.getInstance().initProperty(UscConfiguration.USC_MAX_THREAD_NUMBER, this.getMaxThreadNumber());
        UscConfigurationManager.getInstance().initProperty(UscConfiguration.SECURITY_FILES_ROOT, this.getCertificatesRoot());
        return provider;
    }

}
