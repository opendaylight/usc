package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.netconf.client.dispatcher.rev150325;

import org.opendaylight.usc.client.netconf.UscNetconfClientDispatcherImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UscNetconfClientDispatcherModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.netconf.client.dispatcher.rev150325.AbstractUscNetconfClientDispatcherModule {
    private static final Logger LOG = LoggerFactory.getLogger(UscNetconfClientDispatcherModule.class);
    public UscNetconfClientDispatcherModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
        LOG.trace("constructor 1 ");
    }

    public UscNetconfClientDispatcherModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.netconf.client.dispatcher.rev150325.UscNetconfClientDispatcherModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
        LOG.trace("constructor 2");
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        UscNetconfClientDispatcherImpl result;
        LOG.info("createInstance start");
        try {
            result = new UscNetconfClientDispatcherImpl(getBossThreadGroupDependency(),
                    getWorkerThreadGroupDependency(), getTimerDependency());
        } finally {
            LOG.info("createInstance finish");
        }
        return result;
    }

}
