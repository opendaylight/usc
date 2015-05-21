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
import io.netty.channel.Channel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.usc.agent.UscAgentTcp;
import org.opendaylight.usc.plugin.UscPlugin;
import org.opendaylight.usc.plugin.UscPluginTcp;
import org.opendaylight.usc.plugin.UscSessionIdManager;
import org.opendaylight.usc.plugin.exception.UscSessionException;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.protocol.UscError;

import com.google.common.util.concurrent.SettableFuture;

/**
 * Test suite for USC TCP plugin.
 */
public class UscPluginTcpTest extends UscPluginTest {

    @Before
    public void initConfig() {
    	UscSessionIdManager.getInstance().clear();
    }
    
    @Override
    protected AutoCloseable startEchoServer(boolean enableEncryption) {
        EchoServerTcp echoServer = new EchoServerTcp(enableEncryption);
        Executors.newSingleThreadExecutor().submit(echoServer);
        return echoServer;
    }

    @Override
    protected AutoCloseable startAgent(boolean callHome) throws IOException, InterruptedException {
        UscAgentTcp agent = new UscAgentTcp(callHome);
        if (!callHome) {
            Executors.newSingleThreadExecutor().submit(agent);
        }
        return agent;
    }

    @Override
    protected UscPlugin getPlugin() {
        return new UscPluginTcp();
    }

    @Test
    public void testNoAgent() throws Exception {
        // start the Node.js echo server

        try (AutoCloseable echoServer = startEchoServer(true); 
        		UscPlugin plugin = getPlugin()) {
    
        	Channel clientChannel = null;
			try {
				clientChannel = plugin.connect(clientBootstrap, new InetSocketAddress(HOST, PORT)).sync().channel();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			MyInboundHandler myHandler = new MyInboundHandler();
			clientChannel.pipeline().addLast(myHandler);
			
			String message = "test1\r\n";
			clientChannel.writeAndFlush(message);
			assertEquals(message, myHandler.promise.get(TIMEOUT, TimeUnit.MILLISECONDS));
			
			clientChannel.close();
		}
        
    }

    @Test
    public void testMultipleChannelsDifferentAgents() throws Exception {
        // start the Node.js echo server
        // start the Node.js USC Agent

        try (AutoCloseable echoServer = startEchoServer(false);
                UscPlugin plugin = getPlugin();
                AutoCloseable agent = startAgent(false)) {

            final int NUM_CHANNELS = 10;

            ArrayList<MyInboundHandler> myHandlers = new ArrayList<MyInboundHandler>();
            ArrayList<String> messages = new ArrayList<String>();

            ArrayList<Channel> clientChannels = new ArrayList<Channel>();
            for (int i = 0; i < NUM_CHANNELS; ++i) {
                MyInboundHandler handler = new MyInboundHandler();
                myHandlers.add(handler);

                String host = "127.0.0." + (i + 1);
                Channel clientChannel = plugin.connect(clientBootstrap, new InetSocketAddress(host, PORT)).sync()
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
                // all session IDs should be 1 since there should be different agent channels
                assertEquals(1, session.getSessionId());
            }

            for (Channel clientChannel : clientChannels) {
                clientChannel.close();
            }
        }
    }

    @Test
    public void testNoEchoServer() throws Exception {
        // start the Node.js USC Agent

        try (UscPlugin plugin = getPlugin(); AutoCloseable agent = startAgent(false)) {

            final MyInboundHandler myHandler = new MyInboundHandler();
            Channel clientChannel = plugin.connect(clientBootstrap, new InetSocketAddress(HOST, PORT)).sync().channel();
            clientChannel.pipeline().addLast(myHandler);

            String message = "test1\n";
            clientChannel.writeAndFlush(message);

            // wait for response
            // TCP agent should return a ECONNREFUSED immediately
            try {
                myHandler.promise.get(TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                assertEquals(UscSessionException.class, e.getCause().getClass());
                assertEquals(UscError.ErrorCode.ECONNREFUSED, ((UscSessionException) e.getCause()).getErrorCode());
            } catch (Exception e) {
                assertTrue(false);
            }

            UscSessionImpl session = clientChannel.attr(UscPlugin.SESSION).get().get();
            assertEquals(1, session.getSessionId());

            clientChannel.close();

        }
    }
    
	@Test
	public void testControlMessage() throws Exception {
		try (AutoCloseable echoServer = startEchoServer(false);
				AutoCloseable agent = startAgent(false);
				UscPlugin plugin = getPlugin()) {

			MyInboundHandler myHandler = new MyInboundHandler();
			Channel clientChannel = plugin.connect(clientBootstrap, new InetSocketAddress(HOST, PORT)).sync().channel();
			clientChannel.pipeline().addLast(myHandler);

			// close the connection
			SettableFuture<Boolean> status = plugin.closeAgentInternalConnection(clientChannel);
			try {
				assertEquals(true, status.get(TIMEOUT, TimeUnit.MILLISECONDS));
			} catch (InterruptedException e) {
				;
			}

			// close the connection
			SettableFuture<Boolean> status2 = ((UscAgentTcp) agent).closeClientInternalConnection(clientChannel);
			try {
				assertEquals(true, status2.get(TIMEOUT, TimeUnit.MILLISECONDS));
			} catch (InterruptedException e) {
				;
			} 
		}
	}


	@Test
	public void testCallHomeControlMessage() throws Exception {
		try (AutoCloseable echoServer = startEchoServer(false);
				UscPlugin plugin = getPlugin();
				AutoCloseable agent = startAgent(true)) {

			Thread.sleep(300);
			
			MyInboundHandler handler = new MyInboundHandler();

			Channel clientChannel = plugin
					.connect(clientBootstrap, new InetSocketAddress(HOST, PORT))
					.sync().channel();
			clientChannel.pipeline().addLast(handler);

			// close the connection
			SettableFuture<Boolean> status = plugin.closeAgentInternalConnection(clientChannel);
			try {
				assertEquals(true, status.get(TIMEOUT, TimeUnit.MILLISECONDS));
			} catch (InterruptedException e) {
				;
			} 

			// close the connection
			SettableFuture<Boolean> status2 = ((UscAgentTcp) agent).closeClientInternalConnection(clientChannel);
			try {
				assertEquals(true, status2.get(TIMEOUT, TimeUnit.MILLISECONDS));
			} catch (InterruptedException e) {
				;
			}
		}
	}
}
