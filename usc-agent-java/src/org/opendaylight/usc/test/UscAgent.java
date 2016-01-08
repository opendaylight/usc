/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test;


import java.net.InetAddress;

import org.opendaylight.usc.agent.UscAgentTcp;
import org.opendaylight.usc.agent.UscAgentUdp;

public class UscAgent {

	public static void main(String[] args) throws Exception {
		int argsNumber = args.length;
        int pos = 0;
        boolean tcpFlag = true;
        boolean callHomeFlag = false;
        InetAddress callhomeHost = InetAddress.getLoopbackAddress();
        while (argsNumber - pos > 0) {
            if (args[pos].equals("-t")) {
                pos++;
                if (pos > argsNumber) {
                    usage();
                    return;
                }
                tcpFlag = Boolean.parseBoolean(args[pos]);
            } else if (args[pos].equals("-c")) {
                pos++;
                if (pos > argsNumber) {
                    usage();
                    return;
                }
                callHomeFlag = Boolean.parseBoolean(args[pos]);
            } else if(args[pos].equalsIgnoreCase("-h")) {
            	pos++;
            	if(pos > argsNumber) {
            		usage();
            		return;
            	}
            	callhomeHost = InetAddress.getByName(args[pos]);
            } else {
                usage();
                return;
            }
            pos++;
        }
        
        InetAddress host = InetAddress.getLoopbackAddress();
        if(callHomeFlag)
			host = callhomeHost;
		if (tcpFlag) {
			try (UscAgentTcp tcpAgent = new UscAgentTcp(callHomeFlag, host, "resources/etc/usc/usc.properties")) {
				tcpAgent.run();
			}
		} else {
			try (UscAgentUdp udpAgent = new UscAgentUdp(callHomeFlag, host, "resources/etc/usc/usc.properties")) {
				udpAgent.run();
			}
		}
	}
	
	private static void usage() {
        System.out.println("Usage: [-t tcpFlag] [ -c callHomeFlag ] [-h callHomeIp]");
        System.out.println("\t tcpFlag: 'true' for tcp (default), 'false' for udp");
        System.out.println("\t callHomeIp: IP address of the ODL controller for callhome");
        System.out.println("\t callHomeFlag: 'true' for opening connection from agent, 'false'(default) for other hand");
    }
}
