/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.manager;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.util.UscDtoUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.Topology;

public class UscDtoUtilsTest {
    private UscTopologyFactoryTest topoFactoryTest = new UscTopologyFactoryTest();
    private UscTopologyService topoManager = UscTopologyService.getInstance();

    @Before
    public void setUp() {
        topoManager.init();
    }

    @Test
    public void mergeTopology() {
        Topology topoA = topoFactoryTest.createTopology(1, 2);
        UscManagerUtils.outputTopology(topoA);
        Topology topoB = topoFactoryTest.createTopology(2, 2);
        UscManagerUtils.outputTopology(topoB);
        UscDtoUtils.mergeLinkList(topoA, topoB);
        UscDtoUtils.mergeNodeList(topoA, topoB);
        UscManagerUtils.outputTopology(topoA);
    }
}
