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
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.usc.test.AbstractUscTest;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Channel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.Topology;

/**
 * Test suite for USC topology.
 */
public class UscTopologyManagerTest extends AbstractUscTest {
    private String serverName = "Server1";
    private static UscTopologyService topoManager = UscTopologyService.getInstance();
    private UscTopologyFactoryTest topoTestFactory = new UscTopologyFactoryTest();

    @BeforeClass
    public static void init() {
        // topoManager.clear();
        topoManager.init();
    }

    @Before
    public void setUp() {
        for (Channel Channel : topoManager.getLocalTopolgy().getChannel()) {
            while (Channel != null) {
                Channel = topoManager.removeChannel(Channel.getDestination().getDestNode().getValue(),
                        Channel.getChannelType());
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
        Topology topology = UscTopologyFactory.createTopology(serverName, new CopyOnWriteArrayList<Node>(),
                new CopyOnWriteArrayList<Channel>());
        UscManagerUtils.outputTopology(topology);
    }

    @Test
    public void getNode() {
        initSequenceNodeList(5);
        topoManager.addNode(topoManager.getLocalController());
        Node node = topoManager.getNode(topoManager.getLocalController().getNodeId().getValue());
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

    // @Test
    // public void getChannel() {
    // initSequenceChannelList(5);
    // String deviceId = "Device2";
    // Channel Channel = topoManager.getChannel(deviceId,);
    // Assert.assertNotNull(Channel);
    // UscManagerUtils.checkChannel(deviceId, Channel.getChannelType(),
    // UscTopologyFactory.isCallHome(Channel.getCallHome()), Channel);
    // }

    @Test
    public void addChannel() {
        initSequenceChannelList(5);
        String controllerId = "Controller Test";
        String deviceId = "Device Test";
        Channel Channel = topoTestFactory.createRandomChannel(controllerId, deviceId);
        topoManager.addChannel(Channel);
        Channel = topoManager.getChannel(deviceId, Channel.getChannelType());
        Assert.assertNotNull(Channel);
        UscManagerUtils.checkChannel(deviceId, Channel.getChannelType(),
                UscTopologyFactory.isCallHome(Channel.getCallHome()), Channel);
    }

    @Test
    public void removeChannel() {
        initSequenceChannelList(5);
        String controllerId = "Controller Test";
        String deviceId = "Device Test";
        Channel Channel = topoTestFactory.createRandomChannel(controllerId, deviceId);
        topoManager.addChannel(Channel);
        Channel = topoManager.removeChannel(deviceId, Channel.getChannelType());
        Assert.assertNotNull(Channel);
        UscManagerUtils.checkChannel(deviceId, Channel.getChannelType(),
                UscTopologyFactory.isCallHome(Channel.getCallHome()), Channel);
        Channel = topoManager.removeChannel(deviceId, Channel.getChannelType());
        Assert.assertNull(Channel);
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

    public void initRandomChannelList(int number) {
        Channel Channel = null;
        for (int i = 0; i < number; i++) {
            Channel = topoTestFactory.createRandomChannel();
            topoManager.addChannel(Channel);
        }
    }

    public void initSequenceChannelList(int number) {
        Channel Channel = null;
        for (int i = 0; i < number; i++) {
            Channel = topoTestFactory.createRandomChannel("Contorller" + i, "Device" + i);
            topoManager.addChannel(Channel);
        }
    }
}
