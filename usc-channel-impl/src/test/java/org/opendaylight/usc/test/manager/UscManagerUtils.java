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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.ChannelAlarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.session.attributes.SessionAlarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Channel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.Topology;

/**
 * Utility functions for testing the USC manager.
 */
public class UscManagerUtils {
    public static void checkChannel(String deviceId, String type, boolean isCallHome, Channel Channel) {
        outputChannel(Channel);
        Assert.assertEquals(deviceId, Channel.getDestination().getDestNode().getValue());
        Assert.assertEquals(type, Channel.getChannelType());
        if (isCallHome) {
            Assert.assertEquals(UscTopologyFactory.CALL_HOME_DISPLAY_STRING, Channel.getCallHome());
        } else {
            Assert.assertEquals("", Channel.getCallHome());
        }

    }

    public static void checkNode(String deviceId, String type, Node node) {
        outputNode(node);
        Assert.assertEquals(deviceId, node.getNodeId().getValue());
        Assert.assertEquals(deviceId, node.getKey().getNodeId().getValue());
        Assert.assertEquals(type, node.getNodeType());
    }

    public static void checkChannelAlarm(String id, String code, String message, ChannelAlarm alarm) {
        outputChannelAlarm(alarm);
        Assert.assertEquals(id, alarm.getKey().getAlarmId().getValue());
        Assert.assertEquals(id, alarm.getAlarmId().getValue());
        Assert.assertEquals(code, alarm.getAlarmCode());
        Assert.assertEquals(message, alarm.getAlarmMessage());
    }

    public static void checkSessionAlarm(String id, String code, String message, SessionAlarm alarm) {
        outputSessionAlarm(alarm);
        Assert.assertEquals(id, alarm.getKey().getAlarmId().getValue());
        Assert.assertEquals(id, alarm.getAlarmId().getValue());
        Assert.assertEquals(code, alarm.getAlarmCode());
        Assert.assertEquals(message, alarm.getAlarmMessage());
    }

    public static void outputNode(Node node) {
        UscManagerTest.log(" ++++++++++++++++++++++++++node output start++++++++++++++++++");
        UscManagerTest.log(" key id = " + node.getKey().getNodeId().getValue());
        UscManagerTest.log(" id = " + node.getNodeId().getValue());
        UscManagerTest.log("type = " + node.getNodeType());
        UscManagerTest.log(" ++++++++++++++++++++++++++node output end++++++++++++++++++++");
    }

    public static void outputChannel(Channel Channel) {
        UscManagerTest.log(" -------------------------Channel output start------------------");
        UscManagerTest.log(" key id = " + Channel.getKey().getChannelId().getValue());
        UscManagerTest.log(" id = " + Channel.getChannelId().getValue());
        UscManagerTest.log(" type = " + Channel.getChannelType());
        UscManagerTest.log(" CallHome = " + Channel.getCallHome());
        UscManagerTest.log(" Bytes In = " + Channel.getBytesIn());
        UscManagerTest.log(" Bytes Out = " + Channel.getBytesOut());
        UscManagerTest.log(" Source Controller Id = " + Channel.getSource().getSourceNode().getValue());
        UscManagerTest.log(" Destination Device Id = " + Channel.getDestination().getDestNode().getValue());
        UscManagerTest.log(" Channel Alarm number = " + Channel.getChannelAlarms());
        int i = 1;
        if (Channel.getChannelAlarms() > 0) {
            for (ChannelAlarm alarm : Channel.getChannelAlarm()) {
                UscManagerTest.log(" Channel Alarm :" + i);
                i++;
                outputChannelAlarm(alarm);
            }
        }
        UscManagerTest.log(" Session number = " + Channel.getSessions());
        i = 1;
        if (Channel.getSessions() > 0) {
            for (Session session : Channel.getSession()) {
                UscManagerTest.log(" Session :" + i);
                i++;
                outputSession(session);
            }
        }
        UscManagerTest.log(" -------------------------Channel output end--------------------");
    }

    public static void outputSession(Session session) {
        UscManagerTest.log(" ********************Session output start********************");
        UscManagerTest.log(" Key Id = " + session.getKey().getSessionId().getValue());
        UscManagerTest.log(" Id = " + session.getSessionId().getValue());
        UscManagerTest
                .log(" Terminal Point Port = " + session.getTerminationPoint().getTerminationPointId().getValue());
        UscManagerTest.log(" Bytes In = " + session.getBytesIn());
        UscManagerTest.log(" Bytes Out = " + session.getBytesOut());
        UscManagerTest.log(" Session Alarm number = " + session.getSessionAlarms());
        int i = 1;
        if (session.getSessionAlarms() > 0) {
            for (SessionAlarm alarm : session.getSessionAlarm()) {
                UscManagerTest.log(" Session Alarm :" + i);
                i++;
                outputSessionAlarm(alarm);
            }
        }
        UscManagerTest.log(" ********************Session output end********************");
    }

    public static void outputChannelAlarm(ChannelAlarm alarm) {
        UscManagerTest.log(" ##########################Alarm output start###############");
        UscManagerTest.log(" Key Id = " + alarm.getKey().getAlarmId().getValue());
        UscManagerTest.log(" Id = " + alarm.getAlarmId().getValue());
        UscManagerTest.log(" Code = " + alarm.getAlarmCode());
        UscManagerTest.log(" Message = " + alarm.getAlarmMessage());
        UscManagerTest.log(" ##########################Alarm output end##/###############");
    }

    public static void outputSessionAlarm(SessionAlarm alarm) {
        UscManagerTest.log(" #############Alarm output start###########");
        UscManagerTest.log(" Key Id = " + alarm.getKey().getAlarmId().getValue());
        UscManagerTest.log(" Id = " + alarm.getAlarmId().getValue());
        UscManagerTest.log(" Code = " + alarm.getAlarmCode());
        UscManagerTest.log(" Message = " + alarm.getAlarmMessage());
        UscManagerTest.log(" #############Alarm output end###########");

    }

    public static void outputTopology(Topology topo) {
        UscManagerTest.log(" =======================Topology output start================");
        UscManagerTest.log(" key id = " + topo.getKey().getTopologyId().getValue());
        UscManagerTest.log(" id = " + topo.getTopologyId().getValue());
        UscManagerTest.log(" Node Number = " + topo.getNode().size());
        int i = 1;
        for (Node node : topo.getNode()) {
            UscManagerTest.log(" Node : " + i);
            i++;
            outputNode(node);
        }
        i = 1;
        UscManagerTest.log(" Channel Number = " + topo.getChannel().size());
        for (Channel Channel : topo.getChannel()) {
            UscManagerTest.log(" Channel : " + i);
            i++;
            outputChannel(Channel);
        }
        UscManagerTest.log(" ======================Topology output end==================");
    }
}
