/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.opendaylight.usc.plugin.exception.UscChannelException;
import org.opendaylight.usc.plugin.exception.UscConnectionException;
import org.opendaylight.usc.plugin.exception.UscException;
import org.opendaylight.usc.plugin.exception.UscSessionException;
import org.opendaylight.usc.plugin.model.UscChannelImpl;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Netty handler is automatically installed in the client channel pipeline
 * to facilitate the throwing of any exceptions that are encountered in the USC
 * session into the client state.
 */
@Sharable
public class UscExceptionHandler extends SimpleChannelInboundHandler<UscException> {

    private static final Logger log = LoggerFactory.getLogger(UscExceptionHandler.class);
    private final UscPlugin plugin;
    
    public UscExceptionHandler(UscPlugin plugin) {
    	this.plugin = plugin;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, UscException ex) throws Exception {
        log.trace("UscExceptionHandler channelRead0" + ex);
        
        final Throwable t = ex.getCause();
        Channel channel = ctx.channel();
        UscSessionImpl session = channel.attr(UscPlugin.SESSION).get().get();
        UscChannelImpl connection = session.getChannel();
        
        // connection is down
        if(t instanceof UscConnectionException) {	
        	plugin.getConnectionManager().removeConnection(connection);
        }else if(t instanceof UscChannelException) {
        	//TODO
        	;
        }else if(t instanceof UscSessionException) {
        	connection.removeSession(session.getSessionId());
        }
        
        throw ex;
    }

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		UscConnectionException ex = new UscConnectionException("The channel is closed.");
        
		throw ex;
	}
    
    
}
