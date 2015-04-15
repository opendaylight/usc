/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.manager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Alarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Node;

public class UscTopologyFactoryTest {
    private UscTopologyService topoManager = UscTopologyService.getInstance();

    @Before
    public void setUp() {
        topoManager.init();
    }

    @Test
    public void testTopology() {
        UscManagerUtils.outputTopology(createTopology(2, 2));
    }

    public Topology createTopology(int controllerNumber, int deviceNumber) {
        List<Node> nodeList = new CopyOnWriteArrayList<Node>();
        List<Link> linkList = new CopyOnWriteArrayList<Link>();
        TopologyId topoId = new TopologyId("usc");
        Topology topo = (new TopologyBuilder()).setTopologyId(topoId)
                .setKey(new TopologyKey(topoId)).setNode(nodeList)
                .setLink(linkList).build();

        String deviceName = "device";
        String contollerName = topoManager.getLocalController().getNodeId()
                .getValue();
        Node deviceNode = null;
        Node controllerNode = null;
        Link link = null;
        for (int i = 0; i < controllerNumber; i++) {
            contollerName = "Controller" + getRandomInt();
            for (int j = 0; j < deviceNumber; j++) {
                deviceName = "Device" + getRandomInt();
                link = createRandomLink(contollerName, deviceName);
                controllerNode = createNode(contollerName,
                        UscTopologyService.NODE_TYPE_CONTROLLER);
                deviceNode = createNode(deviceName,
                        UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
                linkList.add(link);
                addNode(nodeList, controllerNode);
                addNode(nodeList, deviceNode);
            }
        }
        return topo;
    }

    public void addNode(List<Node> nodeList, Node node) {
        if (node != null) {
            String id = node.getNodeId().getValue();
            for (Node n : nodeList) {
                if (id.equals(n.getNodeId().getValue())) {
                    UscManagerTest.log("When add node, it already exists.id = "
                            + id);
                    return;
                }
            }
            nodeList.add(node);
        }
    }

    public String getRandomChannelType() {
        String ret = "TCP Channel";
        int random = (int) (Math.random() * 10000);
        switch (random % 4) {
        case 0:
            ret = "TCP Channel";
            break;
        case 1:
            ret = "TLS Channel";
            break;
        case 2:
            ret = "UDP Channel";
            break;
        case 3:
            ret = "DTLS Channel";
            break;
        }
        return ret;
    }

    public boolean getRandomCallHome() {
        int random = (int) (Math.random() * 10000);
        if (random % 2 == 0)
            return true;
        else
            return false;
    }

    public int getRandomInt() {
        int random = (int) (Math.random() * 10000);
        return random;
    }

    @Test
    public void createLink() {
        Link link = createRandomLink(topoManager.getLocalController()
                .getNodeId().getValue(), "device1", "Usc Channel", true);
        UscManagerUtils.checkLink("device1", "Usc Channel", true, link);
    }

    public Link createRandomLink() {
        String deviceId = "Device" + getRandomInt();
        Node controllerNode = UscTopologyFactory.createNode("Controller"
                + getRandomInt(), UscTopologyService.NODE_TYPE_CONTROLLER);
        Node deviceNode = UscTopologyFactory.createNode("Controller"
                + getRandomInt(), UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
        String key = UscTopologyService.NODE_TYPE_CONTROLLER + ":"
                + controllerNode.getNodeId().getValue() + "-"
                + UscTopologyService.NODE_TYPE_NETWORK_DEVICE + ":" + deviceId;
        Link channel = UscTopologyFactory.createLink(controllerNode,
                deviceNode, key, getRandomChannelType(), getRandomCallHome());
        return channel;
    }

    public Link createRandomLink(String contollerId, String deviceId) {
        Node controllerNode = UscTopologyFactory.createNode(contollerId,
                UscTopologyService.NODE_TYPE_CONTROLLER);
        Node deviceNode = UscTopologyFactory.createNode(deviceId,
                UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
        String key = UscTopologyService.NODE_TYPE_CONTROLLER + ":"
                + controllerNode.getNodeId().getValue() + "-"
                + UscTopologyService.NODE_TYPE_NETWORK_DEVICE + ":" + deviceId;
        Link channel = UscTopologyFactory.createLink(controllerNode,
                deviceNode, key, getRandomChannelType(), getRandomCallHome());
        return channel;
    }

    public Link createRandomLink(String contollerId, String deviceId,
            String type, boolean isCallHome) {
        Node controllerNode = UscTopologyFactory.createNode(contollerId,
                UscTopologyService.NODE_TYPE_CONTROLLER);
        Node deviceNode = UscTopologyFactory.createNode(deviceId,
                UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
        String key = UscTopologyService.NODE_TYPE_CONTROLLER + ":"
                + controllerNode.getNodeId().getValue() + "-"
                + UscTopologyService.NODE_TYPE_NETWORK_DEVICE + ":" + deviceId;
        Link channel = UscTopologyFactory.createLink(controllerNode,
                deviceNode, key, type, isCallHome, getRandomInt(),
                getRandomInt(),
                createRandomChannelAlarmList(getRandomInt() % 3),
                createRandomSessionList(getRandomInt() % 3));
        return channel;
    }

    @Test
    public void checkChannelAlarm() {
        String id = UUID.randomUUID().toString();
        String code = 32 + "";
        String message = "this is a channel test alarm";
        Alarm alarm = UscTopologyFactory.createLinkAlram(id, code, message);
        UscManagerUtils.checkChannelAlarm(id, code, message, alarm);
    }

    @Test
    public void checkSessionAlarm() {
        String id = UUID.randomUUID().toString();
        String code = 30 + "";
        String message = "this is a session test alarm";
        org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm alarm = UscTopologyFactory
                .createSessionAlram(id, code, message);
        UscManagerUtils.checkSessionAlarm(id, code, message, alarm);
    }

    public Session createRandomSession() {
        return UscTopologyFactory.createSession(getRandomInt() + "",
                getRandomInt() + "", getRandomInt(), getRandomInt(),
                createRandomSessionAlarmList(getRandomInt() % 3));
    }

    public List<Session> createRandomSessionList(int number) {
        List<Session> list = new CopyOnWriteArrayList<Session>();
        for (int i = 0; i < number; i++) {
            list.add(createRandomSession());
        }
        return list;
    }

    public List<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm> createRandomSessionAlarmList(
            int number) {
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm> alarmList = new CopyOnWriteArrayList<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm>();
        for (int i = 0; i < number; i++) {
            alarmList.add(UscTopologyFactory.createSessionAlram(UUID
                    .randomUUID().toString(), getRandomInt() + "",
                    "this a session alarm " + getRandomInt()));
        }
        return alarmList;
    }

    public List<Alarm> createRandomChannelAlarmList(int number) {
        List<Alarm> alarmList = new CopyOnWriteArrayList<Alarm>();
        for (int i = 0; i < number; i++) {
            alarmList.add(UscTopologyFactory.createLinkAlram(UUID.randomUUID()
                    .toString(), getRandomInt() + "", "this a channel alarm "
                    + getRandomInt()));
        }
        return alarmList;
    }

    @Test
    public void createNode() {
        Node node = createNode("device1", "Network Device");
        UscManagerUtils.checkNode("device1", "Network Device", node);
    }

    public Node createNode(String deviceId, String type) {
        Node node = UscTopologyFactory.createNode(deviceId, type);
        return node;
    }

    public Node createRandomNode() {
        Node node = UscTopologyFactory.createNode("Device" + getRandomInt(),
                "Device Type" + getRandomInt());
        return node;
    }
}
