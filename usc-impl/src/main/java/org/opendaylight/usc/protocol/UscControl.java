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
import io.netty.buffer.Unpooled;

/**
 * An USC control packet.
 */
public class UscControl extends UscFrame {

	private final static int PAYLOAD_LENGTH = 2;

	private final int operationCode;

	/**
	 * Constructs a new UscControl
	 * 
	 * @param port
	 *            the port number of the service on the device
	 * @param sessionId
	 *            the session ID
	 * @param operationCode
	 *            the control message operation code
	 */
	public UscControl(int port, int sessionId, int operationCode) {
		super(OperationType.CONTROL, port, sessionId, PAYLOAD_LENGTH);
		this.operationCode = operationCode;
	}

	@Override
	public ByteBuf getPayload() {
		return Unpooled.copyShort(operationCode);
	}

}
