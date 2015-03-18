/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.protocol;

import org.opendaylight.usc.protocol.UscHeader.OperationType;

import io.netty.buffer.ByteBuf;

/**
 * An USC data packet.
 */
public class UscData extends UscFrame {

	private final ByteBuf payload;

	/**
	 * Constructs a new UscData
	 * 
	 * @param port
	 *            the port number of the service on the device
	 * @param sessionId
	 *            the session ID
	 * @param payload
	 *            the raw byte stream payload
	 */
	public UscData(int port, int sessionId, ByteBuf payload) {
		super(OperationType.DATA, port, sessionId, payload.readableBytes());
		this.payload = payload;
	}

	@Override
	public ByteBuf getPayload() {
		return payload;
	}

	@Override
	public String toString() {
		return "UscData(" + getHeader().getApplicationPort() + ", " + getHeader().getSessionId() + ", " + payload + ")";
	}

}
