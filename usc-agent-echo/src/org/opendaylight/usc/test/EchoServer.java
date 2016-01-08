/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test;


public class EchoServer {
	
	public static void main(String[] args) throws Exception {
		int argsNumber = args.length;
        int pos = 0;
        int port = 2007;
        boolean tcpFlag = true;
        boolean encryptFlag = false;

        while (argsNumber - pos > 0) {
        	if (args[pos].equals("-t")) {
                pos++;
                if (pos > argsNumber) {
                    usage();
                    return;
                }
                tcpFlag = Boolean.parseBoolean(args[pos]);
            } else if (args[pos].equals("-p")) {
                pos++;
                if (pos > argsNumber) {
                    usage();
                    return;
                }
                port = Integer.parseInt(args[pos]);
            } else if (args[pos].equals("-e")) {
                pos++;
                if (pos > argsNumber) {
                    usage();
                    return;
                }
                encryptFlag = Boolean.parseBoolean(args[pos]);
            } else {
                usage();
                return;
            }
            pos++;
        }
        
		if (tcpFlag) {
			try (EchoServerTcp server = new EchoServerTcp(encryptFlag, port)) {
				server.run();
			}
		} else {
			try (EchoServerUdp server = new EchoServerUdp(encryptFlag, port)) {
				server.run();
			}
		}
    }

    public static void usage() {
        System.out.println("Usage: [-t tcpFlag] [ -p port ] [ -e encryptFlag ]");
        System.out.println("\t tcpFlag: 'true' for tcp (default), 'false' for udp");
        System.out.println("\t port: Application port,default is 2007");
        System.out.println("\t encryptFlag: if using secure channel,'true' for using, 'false'(default) for not using");
        System.out
                .println("\t fileName: the alternative reponse content file,default using the same content of request");
        System.out
                .println("\t rate: the rate of receive data, which will decide how many times of request data is the reponse data, it can be less than 1.");
    }
}
