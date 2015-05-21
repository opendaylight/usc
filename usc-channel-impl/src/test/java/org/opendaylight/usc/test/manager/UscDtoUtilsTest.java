/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.manager;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.manager.cluster.UscDeviceMountTable;
import org.opendaylight.usc.manager.cluster.UscChannelIdentifier;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.test.AbstractUscTest;
import org.opendaylight.usc.util.UscDtoUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.Topology;

/**
 * Test suite for USC data transfer object utilities.
 */
public class UscDtoUtilsTest extends AbstractUscTest {
    private UscTopologyFactoryTest topoFactoryTest = new UscTopologyFactoryTest();
    private UscTopologyService topoManager = UscTopologyService.getInstance();

    @Before
    public void setUp() {
        topoManager.init();
    }

    @Test
    public void mergeTopology() {
        int con1 = 1,con2 = 2;
        int node1 = 2, node2 = 2;
        Topology topoA = topoFactoryTest.createTopology(con1, node1);
        UscManagerUtils.outputTopology(topoA);
        Topology topoB = topoFactoryTest.createTopology(con2, node2);
        UscManagerUtils.outputTopology(topoB);
        UscDtoUtils.mergeChannelList(topoA, topoB);
        UscDtoUtils.mergeNodeList(topoA, topoB);
//        Assert.assertEquals(topoA.getNode().size(), node1 + node2);
//        Assert.assertEquals(topoA.getChannel().size(), con1 * node1 + con2 *  node2);
        UscManagerUtils.outputTopology(topoA);
    }

    @Test
    public void uscRemoteChannelEquals() {
        InetAddress address1 = null;
        InetAddress address2 = null;
        try {
            address1 = InetAddress.getByName("192.168.56.102");
            address2 = InetAddress.getByName("192.168.56.102");
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        UscDeviceMountTable table = UscDeviceMountTable.getInstance();
        UscChannelIdentifier remoteChannel1 = new UscChannelIdentifier(address1, ChannelType.TLS);
        UscChannelIdentifier remoteChannel2 = new UscChannelIdentifier(address2, ChannelType.TLS);
        table.addEntry(remoteChannel1, null);
        Assert.assertEquals(remoteChannel1, remoteChannel2);
        // Assert.assertTrue(table.existRemoteChannel(remoteChannel2, null));
    }
}
