/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.crypto.dtls;

import io.netty.channel.socket.DatagramPacket;

public class DtlsPacket {

    public final DatagramPacket packet;

    DtlsPacket(DatagramPacket packet) {
        this.packet = packet;
    }
}
