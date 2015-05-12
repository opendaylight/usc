/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.manager;

import javax.net.ssl.SSLException;

import org.junit.Test;
import org.opendaylight.usc.test.AbstractUscTest;
import org.opendaylight.usc.util.UscDtoUtils;

/**
 * Test suite for USC manager.
 */
public class UscManagerTest extends AbstractUscTest {
    @Test
    public void testID() throws SSLException {
        log("Usc=" + UscDtoUtils.getUscTopologyIdentifier());
        log("UscChannel=" + UscDtoUtils.getTopologyIdentifier("Server1"));
    }

    public static void log(String str) {
        System.out.println(str);
    }
}
