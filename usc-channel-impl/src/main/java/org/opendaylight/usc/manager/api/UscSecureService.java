/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.api;

import javax.net.ssl.SSLException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

/**
 * define all of security interface which USC related
 */
public interface UscSecureService {

    /**
     * get TCP type client security handler using the specified channel(ch)
     * 
     * @param ch
     *            specified channel
     * @return security USC channel handler
     * @throws SSLException
     */
    public ChannelHandler getTcpClientHandler(Channel ch) throws SSLException;

    /**
     * get TCP type Server security handler using the specified channel(ch)
     * 
     * @param ch
     *            specified channel
     * @return security USC channel handler
     * @throws SSLException
     */
    public ChannelHandler getTcpServerHandler(Channel ch) throws SSLException;

    /**
     * get UDP type client security handler using the specified channel(ch)
     * 
     * @param ch
     *            specified channel
     * @return security USC channel handler
     */
    public ChannelHandler getUdpClientHandler(Channel ch);

    /**
     * get UDP type client security handler using the specified channel(ch)
     * 
     * @param ch
     *            specified channel
     * @return security USC channel handler
     */
    public ChannelHandler getUdpServerHandler(Channel ch);

}
