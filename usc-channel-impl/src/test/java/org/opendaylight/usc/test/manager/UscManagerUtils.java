/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.manager;

import org.junit.Assert;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Alarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Node;

public class UscManagerUtils {
    public static void checkLink(String deviceId, String type,
            boolean isCallHome, Link link) {
        outputLink(link);
        Assert.assertEquals(deviceId, link.getDestination().getDestNode()
                .getValue());
        Assert.assertEquals(type, link.getLinkType());
        if (isCallHome) {
            Assert.assertEquals(UscTopologyFactory.CALL_HOME_DISPLAY_STRING,
                    link.getCallHome());
        } else {
            Assert.assertEquals("", link.getCallHome());
        }

    }

    public static void checkNode(String deviceId, String type, Node node) {
        outputNode(node);
        Assert.assertEquals(deviceId, node.getNodeId().getValue());
        Assert.assertEquals(deviceId, node.getKey().getNodeId().getValue());
        Assert.assertEquals(type, node.getNodeType());
    }

    public static void checkChannelAlarm(String id, String code,
            String message, Alarm alarm) {
        outputChannelAlarm(alarm);
        Assert.assertEquals(id, alarm.getKey().getAlarmId().getValue());
        Assert.assertEquals(id, alarm.getAlarmId().getValue());
        Assert.assertEquals(code, alarm.getAlarmCode());
        Assert.assertEquals(message, alarm.getAlarmMessage());
    }

    public static void checkSessionAlarm(
            String id,
            String code,
            String message,
            org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm alarm) {
        outputSessionAlarm(alarm);
        Assert.assertEquals(id, alarm.getKey().getAlarmId().getValue());
        Assert.assertEquals(id, alarm.getAlarmId().getValue());
        Assert.assertEquals(code, alarm.getAlarmCode());
        Assert.assertEquals(message, alarm.getAlarmMessage());
    }

    public static void outputNode(Node node) {
        UscManagerTest
                .log(" ++++++++++++++++++++++++++node output start++++++++++++++++++");
        UscManagerTest.log(" key id = " + node.getKey().getNodeId().getValue());
        UscManagerTest.log(" id = " + node.getNodeId().getValue());
        UscManagerTest.log("type = " + node.getNodeType());
        UscManagerTest
                .log(" ++++++++++++++++++++++++++node output end++++++++++++++++++++");
    }

    public static void outputLink(Link link) {
        UscManagerTest
                .log(" -------------------------link output start------------------");
        UscManagerTest.log(" key id = " + link.getKey().getLinkId().getValue());
        UscManagerTest.log(" id = " + link.getLinkId().getValue());
        UscManagerTest.log(" type = " + link.getLinkType());
        UscManagerTest.log(" CallHome = " + link.getCallHome());
        UscManagerTest.log(" Bytes In = " + link.getBytesIn());
        UscManagerTest.log(" Bytes Out = " + link.getBytesOut());
        UscManagerTest.log(" Source Controller Id = "
                + link.getSource().getSourceNode().getValue());
        UscManagerTest.log(" Destination Device Id = "
                + link.getDestination().getDestNode().getValue());
        UscManagerTest.log(" Channel Alarm number = " + link.getAlarms());
        int i = 1;
        if (link.getAlarms() > 0) {
            for (Alarm alarm : link.getAlarm()) {
                UscManagerTest.log(" Channel Alarm :" + i);
                i++;
                outputChannelAlarm(alarm);
            }
        }
        UscManagerTest.log(" Session number = " + link.getSessions());
        i = 1;
        if (link.getSessions() > 0) {
            for (Session session : link.getSession()) {
                UscManagerTest.log(" Session :" + i);
                i++;
                outputSession(session);
            }
        }
        UscManagerTest
                .log(" -------------------------link output end--------------------");
    }

    public static void outputSession(Session session) {
        UscManagerTest
                .log(" ********************Session output start********************");
        UscManagerTest.log(" Key Id = "
                + session.getKey().getSessionId().getValue());
        UscManagerTest.log(" Id = " + session.getSessionId().getValue());
        UscManagerTest.log(" Terminal Point Port = "
                + session.getTerminalPoint().getTerminalPointId().getValue());
        UscManagerTest.log(" Bytes In = " + session.getBytesIn());
        UscManagerTest.log(" Bytes Out = " + session.getBytesOut());
        UscManagerTest.log(" Session Alarm number = " + session.getAlarms());
        int i = 1;
        if (session.getAlarms() > 0) {
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm alarm : session
                    .getAlarm()) {
                UscManagerTest.log(" Session Alarm :" + i);
                i++;
                outputSessionAlarm(alarm);
            }
        }
        UscManagerTest
                .log(" ********************Session output end********************");
    }

    public static void outputChannelAlarm(Alarm alarm) {
        UscManagerTest
                .log(" ##########################Alarm output start###############");
        UscManagerTest.log(" Key Id = "
                + alarm.getKey().getAlarmId().getValue());
        UscManagerTest.log(" Id = " + alarm.getAlarmId().getValue());
        UscManagerTest.log(" Code = " + alarm.getAlarmCode());
        UscManagerTest.log(" Message = " + alarm.getAlarmMessage());
        UscManagerTest
                .log(" ##########################Alarm output end##/###############");
    }

    public static void outputSessionAlarm(
            org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm alarm) {
        UscManagerTest.log(" #############Alarm output start###########");
        UscManagerTest.log(" Key Id = "
                + alarm.getKey().getAlarmId().getValue());
        UscManagerTest.log(" Id = " + alarm.getAlarmId().getValue());
        UscManagerTest.log(" Code = " + alarm.getAlarmCode());
        UscManagerTest.log(" Message = " + alarm.getAlarmMessage());
        UscManagerTest.log(" #############Alarm output end###########");

    }

    public static void outputTopology(Topology topo) {
        UscManagerTest
                .log(" =======================Topology output start================");
        UscManagerTest.log(" key id = "
                + topo.getKey().getTopologyId().getValue());
        UscManagerTest.log(" id = " + topo.getTopologyId().getValue());
        UscManagerTest.log(" Node Number = " + topo.getNode().size());
        int i = 1;
        for (Node node : topo.getNode()) {
            UscManagerTest.log(" Node : " + i);
            i++;
            outputNode(node);
        }
        i = 1;
        UscManagerTest.log(" Link Number = " + topo.getLink().size());
        for (Link link : topo.getLink()) {
            UscManagerTest.log(" Link : " + i);
            i++;
            outputLink(link);
        }
        UscManagerTest
                .log(" ======================Topology output end==================");
    }
}
