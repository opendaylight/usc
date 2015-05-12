package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.impl.rev150101;

import org.opendaylight.usc.UscProvider;

public class UscChannelImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.impl.rev150101.AbstractUscChannelImplModule {
    public UscChannelImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public UscChannelImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.impl.rev150101.UscChannelImplModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        UscProvider provider = new UscProvider(getDataBrokerDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
