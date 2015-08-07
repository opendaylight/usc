/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.usc.manager.UscConfigurationServiceImpl;
import org.opendaylight.usc.manager.api.UscEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionTransactionEvent;
import org.opendaylight.usc.plugin.UscPlugin;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.test.AbstractUscTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.slf4j.impl.SimpleLogger;

import com.google.common.util.concurrent.SettableFuture;

/**
 * Core test suite for all south bound protocols including TCP, UDP, etc.
 */
public abstract class UscPluginTest extends AbstractUscTest {

    static final Logger log = LoggerFactory.getLogger(UscPluginTest.class);

    final static String NODE_SRC_DIR = "src/main/node/";

    static final int AGENT_INIT_TIME = 200;
    static final int TIMEOUT = 500;

    final Bootstrap clientBootstrap = new Bootstrap();
    private final EventLoopGroup localGroup = new LocalEventLoopGroup();

    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "2007"));

    public static class MyInboundHandler extends SimpleChannelInboundHandler<String> {

        public final SettableFuture<String> promise = SettableFuture.create();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        	log.trace("MyInboundHandler - channelRead0: " + msg);
            promise.set(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            promise.setException(cause);
            // super.exceptionCaught(ctx, cause);
        }

    }

    protected abstract AutoCloseable startEchoServer(boolean enableEncryption);

    protected abstract AutoCloseable startAgent(boolean callHome) throws IOException, InterruptedException;

    protected abstract UscPlugin getPlugin();

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        UscConfigurationServiceImpl.setDefaultPropertyFilePath("src/test/resources/etc/usc/usc.properties");
        
        /*Logger root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root instanceof SimpleLogger) {
            SimpleLogger.setLevel(SimpleLogger.TRACE);
        }*/
        // set up client bootstrap
        clientBootstrap.group(localGroup);
        clientBootstrap.channel(LocalChannel.class);
        clientBootstrap.handler(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new LoggingHandler("UscPluginTest client", LogLevel.TRACE));

                // Decoders
                p.addLast("frameDecoder", new DelimiterBasedFrameDecoder(80, false, Delimiters.lineDelimiter()));
                p.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8));

                // Encoder
                p.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8));

            }
        });

    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        localGroup.shutdownGracefully();
        
        // allow some time for all ports to close;
        Thread.sleep(200);
    }

    @Test
    public void testOneChannel() throws Exception {
        // start the Node.js echo server
        // start the Node.js USC Agent

        try (AutoCloseable echoServer = startEchoServer(false);
                AutoCloseable agent = startAgent(false);
                UscPlugin plugin = getPlugin()) {

            final MyInboundHandler myHandler = new MyInboundHandler();
            Channel clientChannel = plugin.connect(clientBootstrap, new InetSocketAddress(HOST, PORT)).sync().channel();
            clientChannel.pipeline().addLast(myHandler);

            String message = "test1\n";
            clientChannel.writeAndFlush(message);

            // wait for response
            assertEquals(message, myHandler.promise.get(TIMEOUT, TimeUnit.MILLISECONDS));

            UscSessionImpl session = clientChannel.attr(UscPlugin.SESSION).get().get();
            assertEquals(1, session.getSessionId());

            clientChannel.close();

        }
    }

    @Test
    public void testCallHome() throws Exception {

        // start the Node.js echo server
        try (AutoCloseable echoServer = startEchoServer(false); UscPlugin plugin = getPlugin()) {
            try (AutoCloseable agent = startAgent(true)) {
                Thread.sleep(300);

                // start the Node.js USC Agent

                final MyInboundHandler myHandler = new MyInboundHandler();
                Channel clientChannel = plugin.connect(clientBootstrap, new InetSocketAddress(HOST, PORT)).sync()
                        .channel();
                clientChannel.pipeline().addLast(myHandler);

                String message = "test1\n";
                clientChannel.writeAndFlush(message);

                // wait for response
                assertEquals(message, myHandler.promise.get(TIMEOUT, TimeUnit.MILLISECONDS));

                UscSessionImpl session = clientChannel.attr(UscPlugin.SESSION).get().get();
                assertEquals(1, session.getSessionId());
                assertTrue(session.getChannel().isCallHome());

                clientChannel.close();
            }

        }
    }

    @Test
    public void testMultipleChannelsSameAgent() throws Exception {
        // start the Node.js echo server
        // start the Node.js USC Agent

        try (AutoCloseable echoServer = startEchoServer(false);
                AutoCloseable agent = startAgent(false);
                UscPlugin plugin = getPlugin()) {

            final int NUM_CHANNELS = 10;

            ArrayList<MyInboundHandler> myHandlers = new ArrayList<MyInboundHandler>();
            ArrayList<String> messages = new ArrayList<String>();

            ArrayList<Channel> clientChannels = new ArrayList<Channel>();
            for (int i = 0; i < NUM_CHANNELS; ++i) {
                MyInboundHandler handler = new MyInboundHandler();
                myHandlers.add(handler);

                Channel clientChannel = plugin.connect(clientBootstrap, new InetSocketAddress(HOST, PORT)).sync()
                        .channel();
                clientChannel.pipeline().addLast(handler);
                clientChannels.add(clientChannel);
            }

            for (int i = 0; i < NUM_CHANNELS; ++i) {
                Channel clientChannel = clientChannels.get(i);
                String message = "test " + i + " " + clientChannel.hashCode() + "\n";
                messages.add(message);
                clientChannel.writeAndFlush(message);
            }

            for (int i = 0; i < NUM_CHANNELS; ++i) {
                // wait for response
                MyInboundHandler myHandler = myHandlers.get(i);
                assertEquals(messages.get(i), myHandler.promise.get(TIMEOUT, TimeUnit.MILLISECONDS));

                UscSessionImpl session = clientChannels.get(i).attr(UscPlugin.SESSION).get().get();
                assertEquals(i + 1, session.getSessionId());
            }

            for (Channel clientChannel : clientChannels) {
                clientChannel.close();
            }
        }
    }

    public void testConnectionEvents() throws Exception {
        // start the Node.js echo server
        // start the Node.js USC Agent

        final HashMap<Class<? extends UscEvent>, UscEvent> events = new HashMap<>();
        final AtomicLong bytesIn = new AtomicLong();
        final AtomicLong bytesOut = new AtomicLong();

        final InetSocketAddress address = new InetSocketAddress(HOST, PORT);
        final UscDevice device = new UscDevice(address.getAddress());

        String message = "test1\n";
        try (AutoCloseable echoServer = startEchoServer(false);
                AutoCloseable agent = startAgent(false);
                UscPlugin plugin = getPlugin()) {

//            plugin.addMonitorEventListener(new UscMonitor() {
//
//                @Override
//                public void onEvent(UscEvent event) {
//                    events.put(event.getClass(), event);
//
//                    if (event instanceof UscSessionTransactionEvent) {
//                        UscSessionTransactionEvent txnEvent = (UscSessionTransactionEvent) event;
//                        bytesIn.addAndGet(txnEvent.getBytesIn());
//                        bytesOut.addAndGet(txnEvent.getBytesOut());
//                    }
//                }
//            });

            final MyInboundHandler myHandler = new MyInboundHandler();
            Channel clientChannel = plugin.connect(clientBootstrap, address).sync().channel();
            clientChannel.pipeline().addLast(myHandler);

            clientChannel.writeAndFlush(message);

            // wait for response
            assertEquals(message, myHandler.promise.get(TIMEOUT, TimeUnit.MILLISECONDS));

            UscSessionImpl session = clientChannel.attr(UscPlugin.SESSION).get().get();
            assertEquals(1, session.getSessionId());
//            assertEquals(3, events.size());

            assertTrue(events.containsKey(UscChannelCreateEvent.class));
            assertEquals(device.toString(),
                    "/" + ((UscChannelCreateEvent) events.get(UscChannelCreateEvent.class)).getDeviceId());

            assertTrue(events.containsKey(UscSessionCreateEvent.class));
            assertEquals(device.toString(),
                    "/" + ((UscSessionCreateEvent) events.get(UscSessionCreateEvent.class)).getDeviceId());
            assertEquals("1", ((UscSessionCreateEvent) events.get(UscSessionCreateEvent.class)).getSessionId());

            assertTrue(events.containsKey(UscSessionTransactionEvent.class));

            clientChannel.close();

        }

        // give some time for shutdown to complete
        Thread.sleep(200);

        assertEquals(5, events.size());
        assertTrue(events.containsKey(UscSessionCloseEvent.class));
        assertTrue(events.containsKey(UscChannelCloseEvent.class));
        assertEquals(device.toString(), "/" + ((UscChannelCloseEvent) events.get(UscChannelCloseEvent.class)).getDeviceId());
        assertEquals(device.toString(), "/" + ((UscSessionCloseEvent) events.get(UscSessionCloseEvent.class)).getDeviceId());
        assertEquals("1", ((UscSessionCloseEvent) events.get(UscSessionCloseEvent.class)).getSessionId());

        assertEquals(message.length(), bytesIn.get());
        assertEquals(message.length(), bytesOut.get());

    }

}
