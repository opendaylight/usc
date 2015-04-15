/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.manager;

import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Node;

public class UscTopologyManagerTest {
    private String serverName = "Server1";
    private UscTopologyService topoManager = UscTopologyService.getInstance();
    private UscTopologyFactoryTest topoTestFactory = new UscTopologyFactoryTest();

    @Before
    public void setUp() {
        topoManager.init();
        for (Link link : topoManager.getLocalTopolgy().getLink()) {
            while (link != null) {
                link = topoManager.removeLink(link.getDestination()
                        .getDestNode().getValue());
            }
        }
        for (Node node : topoManager.getLocalTopolgy().getNode()) {
            while (node != null) {
                node = topoManager.removeNode(node.getNodeId().getValue());
            }
        }
    }

    @Test
    public void createTopology() {
        Topology topology = UscTopologyFactory.createTopology(serverName,
                new CopyOnWriteArrayList<Node>(),
                new CopyOnWriteArrayList<Link>());
        UscManagerUtils.outputTopology(topology);
    }

    @Test
    public void getNode() {
        initSequenceNodeList(5);
        topoManager.addNode(topoManager.getLocalController());
        Node node = topoManager.getNode(topoManager.getLocalController()
                .getNodeId().getValue());
        Assert.assertNotNull(node);
        node = topoManager.getNode("Device4");
        UscManagerUtils.checkNode("Device4", "Device Type4", node);
        Assert.assertNotNull(node);
        node = topoManager.getNode("Device20");
        Assert.assertNull(node);
    }

    @Test
    public void addNode() {
        initSequenceNodeList(5);
        String deviceId = "Device test";
        String deviceType = "Device Type test";
        Node node = topoTestFactory.createNode(deviceId, deviceType);
        topoManager.addNode(node);
        node = topoManager.getNode(deviceId);
        Assert.assertNotNull(node);
        UscManagerUtils.checkNode(deviceId, deviceType, node);
    }

    @Test
    public void removeNode() {
        initSequenceNodeList(5);
        String deviceId = "Device test";
        String deviceType = "Device Type test";
        Node node = topoTestFactory.createNode(deviceId, deviceType);
        topoManager.addNode(node);// first
        node = topoTestFactory.createNode(deviceId, deviceType);
        topoManager.addNode(node);// second
        topoManager.addNode(node);// third
        node = topoManager.getNode(deviceId);
        Assert.assertNotNull(node);
        node = topoManager.removeNode(deviceId);// first
        Assert.assertNotNull(node);
        node = topoManager.getNode(deviceId);
        Assert.assertNotNull(node);
        topoManager.removeNode(deviceId);// second
        Assert.assertNotNull(node);
        node = topoManager.getNode(deviceId);
        Assert.assertNotNull(node);
        topoManager.removeNode(deviceId);// third
        Assert.assertNotNull(node);
        UscManagerUtils.checkNode(deviceId, deviceType, node);
        node = topoManager.getNode(deviceId);
        Assert.assertNull(node);
        topoManager.removeNode(deviceId);// fouth
        Assert.assertNull(node);
    }

    @Test
    public void getLink() {
        initSequenceLinkList(5);
        String deviceId = "Device2";
        Link link = topoManager.getLink(deviceId);
        Assert.assertNotNull(link);
        UscManagerUtils.checkLink(deviceId, link.getLinkType(),
                UscTopologyFactory.isCallHome(link.getCallHome()), link);
    }

    @Test
    public void addLink() {
        initSequenceLinkList(5);
        String controllerId = "Controller Test";
        String deviceId = "Device Test";
        Link link = topoTestFactory.createRandomLink(controllerId, deviceId);
        topoManager.addLink(link);
        link = topoManager.getLink(deviceId);
        Assert.assertNotNull(link);
        UscManagerUtils.checkLink(deviceId, link.getLinkType(),
                UscTopologyFactory.isCallHome(link.getCallHome()), link);
    }

    @Test
    public void removeLink() {
        initSequenceLinkList(5);
        String controllerId = "Controller Test";
        String deviceId = "Device Test";
        Link link = topoTestFactory.createRandomLink(controllerId, deviceId);
        topoManager.addLink(link);
        link = topoManager.removeLink(deviceId);
        Assert.assertNotNull(link);
        UscManagerUtils.checkLink(deviceId, link.getLinkType(),
                UscTopologyFactory.isCallHome(link.getCallHome()), link);
        link = topoManager.removeLink(deviceId);
        Assert.assertNull(link);
    }

    public void initRandomNodeList(int number) {
        Node node = topoManager.getLocalController();
        topoManager.addNode(node);
        for (int i = 0; i < number; i++) {
            node = topoTestFactory.createRandomNode();
            topoManager.addNode(node);
        }
    }

    public void initSequenceNodeList(int number) {
        Node node = topoManager.getLocalController();
        topoManager.addNode(node);
        for (int i = 0; i < number; i++) {
            node = topoTestFactory.createNode("Device" + i, "Device Type" + i);
            topoManager.addNode(node);
        }
    }

    public void initRandomLinkList(int number) {
        Link link = null;
        for (int i = 0; i < number; i++) {
            link = topoTestFactory.createRandomLink();
            topoManager.addLink(link);
        }
    }

    public void initSequenceLinkList(int number) {
        Link link = null;
        for (int i = 0; i < number; i++) {
            link = topoTestFactory.createRandomLink("Contorller" + i, "Device"
                    + i);
            topoManager.addLink(link);
        }
    }
}
