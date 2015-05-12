/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.topology;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.AlarmId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.ChannelId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.SessionId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.TerminationPointId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.ChannelAlarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.ChannelAlarmBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.ChannelAlarmKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.SessionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.session.attributes.SessionAlarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.session.attributes.SessionAlarmBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.session.attributes.SessionAlarmKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.session.attributes.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Channel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.ChannelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.ChannelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.TopologyKey;

/**
 * the create factory of Channels and nodes of usc topology
 */
public class UscTopologyFactory {

    /**
     * the call home label string for UI
     */
    public static final String CALL_HOME_DISPLAY_STRING = "CallHome";

    /**
     * Builds a Channel for the provided source, destination, and id. Passes
     * Channel attributes to ChannelBuilder to build a Channel.
     * 
     * @param source
     *            the source node of Channel
     * @param destination
     *            the destination node of Channel
     * @param id
     *            the Channel id
     * @param type
     *            the Channel type
     * @param isCallHome
     *            if the Channel created by call home way
     * @return new Channel
     */
    public static Channel createChannel(Node source, Node destination, String id, String type, boolean isCallHome) {
        return createChannel(source, destination, id, type, isCallHome, 0, 0,
                Collections.synchronizedList(new LinkedList<ChannelAlarm>()), new CopyOnWriteArrayList<Session>());
    }

    /**
     * Builds a Channel for the provided source, destination, and id. Passes
     * Channel attributes to ChannelBuilder to build a Channel.
     * 
     * @param source
     *            the source node of Channel
     * @param destination
     *            the destination node of Channel
     * @param id
     *            the Channel id
     * @param type
     *            the Channel type
     * @param isCallHome
     *            if the Channel created by call home way
     * @param bytesIn
     *            Channel bytes in number
     * @param bytesOut
     *            Channel bytes out number
     * @param alarms
     *            the error list of Channel
     * @param sessions
     *            the session list of Channel
     * @return new Channel
     */
    public static Channel createChannel(Node source, Node destination, String id, String type, boolean isCallHome,
            long bytesIn, long bytesOut, List<ChannelAlarm> alarms, List<Session> sessions) {
        ChannelId channelId = new ChannelId(id);
        ChannelKey channelKey = new ChannelKey(channelId);
        SourceBuilder sourceBuilder = new SourceBuilder();
        sourceBuilder.setSourceNode(source.getNodeId());
        DestinationBuilder destinationBuilder = new DestinationBuilder();
        destinationBuilder.setDestNode(destination.getNodeId());
        ChannelBuilder channelBuilder = new ChannelBuilder();
        channelBuilder.setChannelId(channelId);
        channelBuilder.setKey(channelKey);
        channelBuilder.setChannelType(type);
        channelBuilder.setSource(sourceBuilder.build());
        channelBuilder.setDestination(destinationBuilder.build());
        channelBuilder.setBytesIn(bytesIn);
        channelBuilder.setBytesOut(bytesOut);
        channelBuilder.setCallHome(getCallHomeString(isCallHome));
        channelBuilder.setChannelAlarms((long) alarms.size());
        channelBuilder.setChannelAlarm(alarms);
        channelBuilder.setSessions((long) sessions.size());
        channelBuilder.setSession(sessions);
        return channelBuilder.build();
    }

    /**
     * convert boolean to string of call home
     * 
     * @param isCallHome
     *            boolean value of call home
     * @return the string value of call home
     */
    public static String getCallHomeString(boolean isCallHome) {
        if (isCallHome)
            return CALL_HOME_DISPLAY_STRING;
        else
            return "";
    }

    /**
     * convert String to boolean of call home
     * 
     * @param callHome
     *            String value of call home
     * @return the boolean value of call home
     */
    public static boolean isCallHome(String callHome) {
        if (callHome.equals(CALL_HOME_DISPLAY_STRING)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Builds a node for the provided id. Passes node attributes to NodeBuilder
     * to build a node.
     * 
     * @param id
     *            node id
     * @param type
     *            node type
     * @return new node
     */
    public static Node createNode(String id, String type) {
        NodeId nodeId = new NodeId(id);
        NodeKey nodeKey = new NodeKey(nodeId);
        NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.setNodeId(nodeId);
        nodeBuilder.setKey(nodeKey);
        nodeBuilder.setNodeType(type);
        return nodeBuilder.build();
    }

    /**
     * Builds a topology for the provided id, nodes and Channels. Passes
     * topology attributes to TopologyBuilder to build a topology.
     * 
     * @param id
     *            topology id
     * @param nodes
     *            node list of topology
     * @param Channels
     *            Channel list of topology
     * @return new topology
     */
    public static Topology createTopology(String id, List<Node> nodes, List<Channel> Channels) {
        TopologyId topologyId = new TopologyId(id);
        TopologyKey topologyKey = new TopologyKey(topologyId);
        TopologyBuilder topologyBuilder = new TopologyBuilder();
        topologyBuilder.setTopologyId(topologyId);
        topologyBuilder.setKey(topologyKey);
        topologyBuilder.setNode(nodes);
        topologyBuilder.setChannel(Channels);
        return topologyBuilder.build();
    }

    /**
     * Builds a node for the provided id. Passes node attributes to NodeBuilder
     * to build a node.
     * 
     * @param sessionId
     *            session id
     * @param tpPort
     *            the port of terminal point which related with the session
     * @return new session
     */
    public static Session createSession(String sessionId, String tpPort) {
        return createSession(sessionId, tpPort, 0, 0, Collections.synchronizedList(new LinkedList<SessionAlarm>()));
    }

    /**
     * Builds a node for the provided id. Passes node attributes to NodeBuilder
     * to build a node.
     * 
     * @param sessionId
     *            session id
     * @param tpPort
     *            the port of terminal point which related with the session
     * @param bytesIn
     *            bytes in number
     * @param bytesOut
     *            bytes out number
     * @param alarms
     *            error list
     * @return new session
     */
    public static Session createSession(String sessionId, String tpPort, long bytesIn, long bytesOut,
            List<SessionAlarm> alarms) {
        TerminationPointId tpId = new TerminationPointId(tpPort);
        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setTerminationPointId(tpId);
        SessionId sId = new SessionId(sessionId);
        SessionKey sessionKey = new SessionKey(sId);
        SessionBuilder sessionBuilder = new SessionBuilder();
        sessionBuilder.setSessionId(sId);
        sessionBuilder.setKey(sessionKey);
        sessionBuilder.setBytesIn(bytesIn);
        sessionBuilder.setBytesOut(bytesOut);
        sessionBuilder.setTerminationPoint(tpBuilder.build());
        sessionBuilder.setSessionAlarms((long) alarms.size());
        sessionBuilder.setSessionAlarm(alarms);
        return sessionBuilder.build();
    }

    /**
     * Builds a session alarm Passes alarm attributes to AlarmBuilder to build a
     * alarm.
     * 
     * @param id
     *            error id
     * @param code
     *            error code
     * @param message
     *            error message
     * @return new Channel error
     */
    public static SessionAlarm createSessionAlram(String id, String code, String message) {
        SessionAlarmBuilder alarmBuilder = new SessionAlarmBuilder();
        AlarmId alarmId = new AlarmId(id);
        SessionAlarmKey alarmKey = new SessionAlarmKey(alarmId);
        alarmBuilder.setAlarmId(alarmId);
        alarmBuilder.setKey(alarmKey);
        alarmBuilder.setAlarmCode(code);
        alarmBuilder.setAlarmMessage(message);
        return alarmBuilder.build();
    }

    /**
     * Builds a Channel alarm Passes alarm attributes to AlarmBuilder to build a
     * alarm.
     * 
     * @param id
     *            error id
     * @param code
     *            error code
     * @param message
     *            error message
     * @return new Channel error
     */
    public static ChannelAlarm createChannelAlram(String id, String code, String message) {
        ChannelAlarmBuilder alarmBuilder = new ChannelAlarmBuilder();
        AlarmId alarmId = new AlarmId(id);
        ChannelAlarmKey alarmKey = new ChannelAlarmKey(alarmId);
        alarmBuilder.setAlarmId(alarmId);
        alarmBuilder.setKey(alarmKey);
        alarmBuilder.setAlarmCode(code);
        alarmBuilder.setAlarmMessage(message);
        return alarmBuilder.build();
    }

}
