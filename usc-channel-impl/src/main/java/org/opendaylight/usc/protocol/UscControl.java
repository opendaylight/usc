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

	public static enum ControlCode {
		OTHER(0),
		TERMINATION_REQUEST(1),
		TERMINATION_RESPONSE(2);
		
		private int code;
		
		private ControlCode(int code) {
			this.code = code;
		}
		
		public int getCode() {
			return code;
		}

		@Override
		public String toString() {
			return this.name() + "(" + this.code + ")";
		}
		
	}
	
	private final static int PAYLOAD_LENGTH = 2;

	private final ControlCode controlCode;;

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
		
		ControlCode cc = ControlCode.OTHER;
		for(ControlCode c : ControlCode.values()) {
			if(c.getCode() == operationCode) {
				cc = c;
				break;
			}
		}
		
		this.controlCode = cc;
	}
	

	public ControlCode getControlCode() {
		return controlCode;
	}



	@Override
	public ByteBuf getPayload() {
		return Unpooled.copyShort(controlCode.getCode());
	}


	@Override
	public String toString() {
		return "UscControl [controlCode = " + controlCode + "]";
	}

}
