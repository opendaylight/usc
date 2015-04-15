/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import org.opendaylight.usc.manager.monitor.UscMonitorTargetAdapter;
import org.opendaylight.usc.plugin.model.UscChannel;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.plugin.model.UscChannelImpl;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.protocol.UscControl;
import org.opendaylight.usc.protocol.UscError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;

/**
 * This is the base class for all UscPlugin classes. This handles common connection setup and channel/session management
 * capabilities.
 */
public abstract class UscPlugin extends UscMonitorTargetAdapter implements AutoCloseable {

    /**
     * Constant used for setting the UscChannel attribute on a netty channel.
     */
    public static final AttributeKey<UscChannelImpl> CHANNEL = AttributeKey.valueOf("channel");

    /**
     * Constant used for setting the UscSession attribute on a netty channel.
     */

	public static final AttributeKey<SettableFuture<UscSessionImpl>> SESSION = AttributeKey.valueOf("session");
    
    /**
     * Constant used for setting the client channel attribute on a server channel 
     */
    public static final AttributeKey<Channel> CLIENT_CHANNEL = AttributeKey.valueOf("client_channel");
    
    /**
     * Constant used for setting the next direct channel between the plugin and device 
     */
    public static final AttributeKey<Channel> DIRECT_CHANNEL = AttributeKey.valueOf("direct_channel");
    public static final AttributeKey<LocalChannel> LOCAL_SERVER_CHANNEL = AttributeKey.valueOf("local_server_channel");
    

    private static final Logger LOG = LoggerFactory.getLogger(UscPlugin.class);
    private static final LocalAddress localServerAddr = new LocalAddress("usc-local-server");
    private final UscExceptionHandler uscExceptionHandler = new UscExceptionHandler(this);

    /**
     * Map from client SocketAddress to serverChildChannel
     */
    private final ConcurrentMap<SocketAddress, SettableFuture<LocalChannel>> serverChannels = new ConcurrentHashMap<>();
    private final ConcurrentMap<Channel, SettableFuture<Boolean>> closeFuture = new ConcurrentHashMap<>();
    private final UscConnectionManager connectionManager = new UscConnectionManager(this);
    private final EventLoopGroup localGroup = new LocalEventLoopGroup();
    private final UscDemultiplexer demuxer = new UscDemultiplexer(this);
    private final Demultiplexer dmpx = new Demultiplexer(this);
    private final UscMultiplexer muxer = new UscMultiplexer(this);

    protected UscPlugin() {
    	LOG.debug("UscPlugin " + this + "started");

        final ServerBootstrap localServerBootstrap = new ServerBootstrap();
        localServerBootstrap.group(localGroup);
        localServerBootstrap.channel(LocalServerChannel.class);
        // serverBootstrap.handler(new LoggingHandler(LogLevel.TRACE));
        localServerBootstrap.childHandler(new ChannelInitializer<LocalChannel>() {
            @Override

            public void initChannel(final LocalChannel serverChannel) throws Exception {
                LOG.debug("localServerBootstrap - initChannel: serverChannel = " + serverChannel);
                ChannelPipeline p = serverChannel.pipeline();
                // p.addLast(new LoggingHandler(LogLevel.TRACE));

                // call this first so that the attribute will be visible to the outside once the localAddress is set
                serverChannel.attr(SESSION).setIfAbsent(SettableFuture.<UscSessionImpl> create());

                // register the child channel by address for lookup outside
                LocalAddress localAddress = serverChannel.remoteAddress();
                serverChannels.putIfAbsent(localAddress, SettableFuture.<LocalChannel> create());
                serverChannels.get(localAddress).set(serverChannel);

                p.addLast(new LoggingHandler("localServerBootstrp Handler 2", LogLevel.TRACE));
                p.addLast(getMultiplexer());
                p.addLast(new LoggingHandler("localServerBootstrp Handler 1", LogLevel.TRACE));

            }
        });

        // Start the server.
        final ChannelFuture serverChannelFuture = localServerBootstrap.bind(localServerAddr);
        LOG.debug("serverChannel: " + serverChannelFuture);
    }

    protected void initAgentPipeline(ChannelPipeline p, ChannelHandler securityHandler) {

        p.addLast(new LoggingHandler("UscPlugin LoggingHandler 5", LogLevel.TRACE));

        // security handler
        p.addLast("securityHandler", securityHandler);
        p.addLast(new LoggingHandler("UscPlugin LoggingHandler 4", LogLevel.TRACE));

        // Encoders
        // UscFrameEncoder is Sharable
        p.addLast("frameEncoder", getFrameEncoder());
        p.addLast(new LoggingHandler("UscPlugin LoggingHandler 3", LogLevel.TRACE));

        // Decoders
        // UscFrameDecoderUdp is Sharable
        p.addLast("frameDecoder", getFrameDecoder());
        p.addLast(new LoggingHandler("UscPlugin LoggingHandler 2", LogLevel.TRACE));

        // UscDemultiplexer
        p.addLast(getDemultiplexer());

        p.addLast(new LoggingHandler("UscPlugin LoggingHandler 1", LogLevel.TRACE));
    }
    
    protected void initDirectPipeline(ChannelPipeline p, ChannelHandler securityHandler) {

        p.addLast(new LoggingHandler("UscPlugin LoggingHandler 3", LogLevel.TRACE));

        // security handler
        p.addLast("securityHandler", securityHandler);
        p.addLast(new LoggingHandler("UscPlugin LoggingHandler 2", LogLevel.TRACE));

        // demultiplexer
        p.addLast(getDmpx());

        p.addLast(new LoggingHandler("UscPlugin LoggingHandler 1", LogLevel.TRACE));
    }

    protected ChannelInboundHandler getMultiplexer() {
        return muxer;
    }

    protected UscDemultiplexer getDemultiplexer() {
        return demuxer;
    }

    
    protected Demultiplexer getDmpx() {
		return dmpx;
	}

	protected UscConnectionManager getConnectionManager() {
        return connectionManager;
    }

    protected abstract ChannelOutboundHandler getFrameEncoder();

    protected abstract ChannelInboundHandler getFrameDecoder();

    /**
     * Initiates a client session to a device service as specified by the address parameter.
     * 
     * @param clientBootstrap
     *            the Netty bootstrap to use to create the session
     * @param address
     *            the IP address and port of the device service
     * @return the Netty ChannelFuture that can be used to communicate with the device service
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public ChannelFuture connect(Bootstrap clientBootstrap, final InetSocketAddress address)
            throws InterruptedException, ExecutionException, Exception {

        LOG.warn("Attempt to connect to " + address);

        // Connect to USC Agent to the device if one's not already created
        UscChannelImpl connection = null;
        Channel directChannel = null;
        
        try {
            connection = connectionManager.getConnection(new UscDevice(address.getAddress(), address.getPort()), getChannelType());
        } catch (Exception e) {
        	directChannel = connectToDeviceDirectly(new UscDevice(address.getAddress(), address.getPort()));
        }
        
        final ChannelFuture clientChannelFuture = clientBootstrap.connect(localServerAddr);

        clientChannelFuture.channel().pipeline().addLast(uscExceptionHandler);

        // sync to ensure that localAddress is not null
        final Channel clientChannel = clientChannelFuture.sync().channel();
        SocketAddress localAddress = clientChannel.localAddress();
        serverChannels.putIfAbsent(localAddress, SettableFuture.<LocalChannel> create());

        // wait for the peer to populate
        LocalChannel serverChannel = serverChannels.get(localAddress).get();
        
        LOG.trace("connect: serverChannel = " + serverChannel);
        
        assert serverChannel != null;
        // remove the entry from the map as its purpose is complete
        serverChannels.remove(localAddress);

        if(connection != null) {
        	UscSessionImpl session = connection.addSession(address.getPort(), serverChannel);

        	LOG.warn("clientChannel set session " + session);
        	// these attributes are used by unit test cases
        	clientChannel.attr(SESSION).setIfAbsent(SettableFuture.<UscSessionImpl> create());
        	clientChannel.attr(SESSION).get().set(session);

        	// these attributes are used by UscMultiplexer
        	serverChannel.attr(SESSION).get().set(session);

        	//this attribute is used by UscDemultiplexer
        	serverChannel.attr(CLIENT_CHANNEL).set(clientChannel);
        }
        else if(directChannel != null){
        	serverChannel.attr(DIRECT_CHANNEL).set(directChannel);
        	directChannel.attr(LOCAL_SERVER_CHANNEL).set(serverChannel);
        }
        
        return clientChannelFuture;
    }

    protected abstract ChannelType getChannelType();

    protected abstract Channel connectToAgent(UscDevice device) throws InterruptedException, Exception;
    protected abstract Channel connectToDeviceDirectly(UscDevice device) throws InterruptedException, Exception;

    @Override
    public void close() {
        localGroup.shutdownGracefully();

        LOG.debug("UscPlugin " + this + "closed");
    }

    protected void addCallHomeConnection(InetSocketAddress address, Channel channel) {
        final UscDevice device = new UscDevice(address.getAddress());
        connectionManager.addConnection(device, channel, true, getChannelType());
    }

    
    protected ConcurrentMap<Channel, SettableFuture<Boolean>> getCloseFuture() {
		return closeFuture;
	}

	/**
     * 
     * @param clientChannel
     * @return close status
     */
    public SettableFuture<Boolean> closeAgentInternalConnection(Channel clientChannel) {
    	closeFuture.remove(clientChannel);
    	closeFuture.putIfAbsent(clientChannel, SettableFuture.<Boolean> create());
    	
		try {
			UscSessionImpl session = clientChannel.attr(SESSION).get().get();
			Channel outboundChannel = session.getChannel().getChannel();

			UscControl data = new UscControl(session.getPort(),
					session.getSessionId(), 1);
			outboundChannel.writeAndFlush(data);

			LOG.trace("UscPlugin closeAgentInternalConnection port#: "
					+ session.getPort() + " ,session#: "
					+ session.getSessionId());

		} catch (Exception e) {
			e.printStackTrace();
		}

		return closeFuture.get(clientChannel);
    }
    
    public boolean isChannelAvailable(InetSocketAddress address) {
        try {
            final UscChannelImpl connection = connectionManager.getConnection(new UscDevice(address.getAddress()),
                    getChannelType());
            return connection != null;
        } catch (Exception e) {
        	LOG.warn("Unable to create USC channel to " + address.getAddress());
            return false;
        }
    }

}
