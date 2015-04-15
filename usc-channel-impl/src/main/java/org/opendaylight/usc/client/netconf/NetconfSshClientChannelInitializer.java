package org.opendaylight.usc.client.netconf;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;

import java.io.IOException;

import org.opendaylight.controller.netconf.client.NetconfClientSession;
import org.opendaylight.controller.netconf.client.NetconfClientSessionListener;
import org.opendaylight.controller.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.controller.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.controller.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.controller.netconf.nettyutil.handler.ssh.client.AsyncSshHandler;
import org.opendaylight.protocol.framework.SessionListenerFactory;

final class NetconfSshClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> {

    private final AuthenticationHandler authenticationHandler;
    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    public NetconfSshClientChannelInitializer(final AuthenticationHandler authHandler,
            final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final NetconfClientSessionListener sessionListener) {
        this.authenticationHandler = authHandler;
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
    }

    @Override
    public void initialize(final Channel ch, final Promise<NetconfClientSession> promise) {
        try {
            // ssh handler has to be the first handler in pipeline
            ch.pipeline().addFirst(AsyncSshHandler.createForNetconfSubsystem(authenticationHandler));
            super.initialize(ch, promise);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void initializeSessionNegotiator(final Channel ch, final Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
                negotiatorFactory.getSessionNegotiator(new SessionListenerFactory<NetconfClientSessionListener>() {
                    @Override
                    public NetconfClientSessionListener getSessionListener() {
                        return sessionListener;
                    }
                }, ch, promise));
    }
}
