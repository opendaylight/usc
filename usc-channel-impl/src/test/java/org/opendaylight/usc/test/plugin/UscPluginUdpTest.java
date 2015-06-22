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
import io.netty.handler.timeout.TimeoutException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.usc.agent.UscAgentUdp;
import org.opendaylight.usc.plugin.UscPlugin;
import org.opendaylight.usc.plugin.UscPluginUdp;
import org.opendaylight.usc.plugin.exception.UscSessionException;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.protocol.UscError;

import com.google.common.util.concurrent.SettableFuture;

/**
 * Test suite for USC UDP plugin.
 */
@Ignore
public class UscPluginUdpTest extends UscPluginTest {

    @Before
    public void initConfig() {
    }

    @Override
    protected AutoCloseable startEchoServer(boolean enableEncryption) {
        EchoServerUdp echoServer = new EchoServerUdp(enableEncryption);
        Executors.newSingleThreadExecutor().submit(echoServer);
        return echoServer;
    }

    @Override
    protected AutoCloseable startAgent(boolean callHome) throws IOException, InterruptedException {
        UscAgentUdp agent = new UscAgentUdp(callHome);
        if (!callHome) {
            Executors.newSingleThreadExecutor().submit(agent);
        }
        return agent;
    }

    @Override
    protected UscPlugin getPlugin() {
        return new UscPluginUdp();
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
			
			String message = "test1\n";
			clientChannel.writeAndFlush(message);
			assertEquals(message, myHandler.promise.get(TIMEOUT, TimeUnit.MILLISECONDS));

			clientChannel.close();
		}    
    }
    
    @Test
    public void testNoEchoServer() throws Exception {
        // start the Node.js USC Agent

        try (AutoCloseable agent = startAgent(false); UscPlugin plugin = getPlugin()) {

            final MyInboundHandler myHandler = new MyInboundHandler();
            Channel clientChannel = plugin.connect(clientBootstrap, new InetSocketAddress(HOST, PORT)).sync().channel();
            clientChannel.pipeline().addLast(myHandler);

            String message = "test1\n";
            clientChannel.writeAndFlush(message);

            // wait for response
            // UDP agent can only detect DNS errors, so response should time out
            try {
                myHandler.promise.get(TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                assertEquals(UscSessionException.class, e.getCause().getClass());
                assertEquals(UscError.ErrorCode.ENETUNREACH, ((UscSessionException) e.getCause()).getErrorCode());
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
			SettableFuture<Boolean> status2 = ((UscAgentUdp) agent).closeClientInternalConnection(clientChannel);
			try {
				assertEquals(true, status2.get(TIMEOUT, TimeUnit.MILLISECONDS));
			} catch (InterruptedException e) {
				;
			}
		}
	}

	@Test
	public void testCallhomeControlMessage() throws Exception {
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
			SettableFuture<Boolean> status2 = ((UscAgentUdp) agent).closeClientInternalConnection(clientChannel);
			try {
				assertEquals(true, status2.get(TIMEOUT, TimeUnit.MILLISECONDS));
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (TimeoutException e) {
				e.printStackTrace();
			} catch(ExecutionException e) {
				e.printStackTrace();
			}
		}
	}
}
