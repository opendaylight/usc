/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.topology;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.AlarmId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.LinkId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.SessionId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.TerminalPointId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Alarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.AlarmBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.AlarmKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.SessionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.SessionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.TerminalPointBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.LinkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.LinkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.NodeKey;

/**
 * the create factory of links and nodes of usc topology
 */
public class UscTopologyFactory {

    /**
     * the call home label string for UI
     */
    public static final String CALL_HOME_DISPLAY_STRING = "CallHome";

    /**
     * Builds a link for the provided source, destination, and id. Passes link
     * attributes to LinkBuilder to build a link.
     * 
     * @param source
     *            the source node of link
     * @param destination
     *            the destination node of link
     * @param id
     *            the link id
     * @param type
     *            the link type
     * @param isCallHome
     *            if the link created by call home way
     * @return new link
     */
    public static Link createLink(Node source, Node destination, String id, String type, boolean isCallHome) {
        return createLink(source, destination, id, type, isCallHome, 0, 0, new CopyOnWriteArrayList<Alarm>(),
                new CopyOnWriteArrayList<Session>());
    }

    /**
     * Builds a link for the provided source, destination, and id. Passes link
     * attributes to LinkBuilder to build a link.
     * 
     * @param source
     *            the source node of link
     * @param destination
     *            the destination node of link
     * @param id
     *            the link id
     * @param type
     *            the link type
     * @param isCallHome
     *            if the link created by call home way
     * @param bytesIn
     *            link bytes in number
     * @param bytesOut
     *            link bytes out number
     * @param alarms
     *            the error list of link
     * @param sessions
     *            the session list of link
     * @return new link
     */
    public static Link createLink(Node source, Node destination, String id, String type, boolean isCallHome,
            long bytesIn, long bytesOut, List<Alarm> alarms, List<Session> sessions) {
        LinkId linkId = new LinkId(id);
        LinkKey linkKey = new LinkKey(linkId);
        SourceBuilder sourceBuilder = new SourceBuilder();
        sourceBuilder.setSourceNode(source.getNodeId());
        DestinationBuilder destinationBuilder = new DestinationBuilder();
        destinationBuilder.setDestNode(destination.getNodeId());
        LinkBuilder linkBuilder = new LinkBuilder();
        linkBuilder.setLinkId(linkId);
        linkBuilder.setKey(linkKey);
        linkBuilder.setLinkType(type);
        linkBuilder.setSource(sourceBuilder.build());
        linkBuilder.setDestination(destinationBuilder.build());
        linkBuilder.setBytesIn(bytesIn);
        linkBuilder.setBytesOut(bytesOut);
        linkBuilder.setCallHome(getCallHomeString(isCallHome));
        linkBuilder.setAlarms((long) alarms.size());
        linkBuilder.setAlarm(alarms);
        linkBuilder.setSessions((long) sessions.size());
        linkBuilder.setSession(sessions);
        return linkBuilder.build();
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
     * @param CallHome
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
     * Builds a topology for the provided id, nodes and links. Passes topology
     * attributes to TopologyBuilder to build a topology.
     * 
     * @param id
     *            topology id
     * @param nodes
     *            node list of topology
     * @param links
     *            link list of topology
     * @return new topology
     */
    public static Topology createTopology(String id, List<Node> nodes, List<Link> links) {
        TopologyId topologyId = new TopologyId(id);
        TopologyKey topologyKey = new TopologyKey(topologyId);
        TopologyBuilder topologyBuilder = new TopologyBuilder();
        topologyBuilder.setTopologyId(topologyId);
        topologyBuilder.setKey(topologyKey);
        topologyBuilder.setNode(nodes);
        topologyBuilder.setLink(links);
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
        return createSession(
                sessionId,
                tpPort,
                0,
                0,
                new CopyOnWriteArrayList<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm>());
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
    public static Session createSession(
            String sessionId,
            String tpPort,
            long bytesIn,
            long bytesOut,
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm> alarms) {
        TerminalPointId tpId = new TerminalPointId(tpPort);
        TerminalPointBuilder tpBuilder = new TerminalPointBuilder();
        tpBuilder.setTerminalPointId(tpId);
        SessionId sId = new SessionId(sessionId);
        SessionKey sessionKey = new SessionKey(sId);
        SessionBuilder sessionBuilder = new SessionBuilder();
        sessionBuilder.setSessionId(sId);
        sessionBuilder.setKey(sessionKey);
        sessionBuilder.setBytesIn(bytesIn);
        sessionBuilder.setBytesOut(bytesOut);
        sessionBuilder.setTerminalPoint(tpBuilder.build());
        sessionBuilder.setAlarms((long) alarms.size());
        sessionBuilder.setAlarm(alarms);
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
     * @return new link error
     */
    public static org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm createSessionAlram(
            String id, String code, String message) {
        org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.AlarmBuilder alarmBuilder = new org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.AlarmBuilder();
        AlarmId alarmId = new AlarmId(id);
        org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.AlarmKey alarmKey = new org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.AlarmKey(
                alarmId);
        alarmBuilder.setAlarmId(alarmId);
        alarmBuilder.setKey(alarmKey);
        alarmBuilder.setAlarmCode(code);
        alarmBuilder.setAlarmMessage(message);
        return alarmBuilder.build();
    }

    /**
     * Builds a link alarm Passes alarm attributes to AlarmBuilder to build a
     * alarm.
     * 
     * @param id
     *            error id
     * @param code
     *            error code
     * @param message
     *            error message
     * @return new link error
     */
    public static Alarm createLinkAlram(String id, String code, String message) {
        AlarmBuilder alarmBuilder = new AlarmBuilder();
        AlarmId alarmId = new AlarmId(id);
        AlarmKey alarmKey = new AlarmKey(alarmId);
        alarmBuilder.setAlarmId(alarmId);
        alarmBuilder.setKey(alarmKey);
        alarmBuilder.setAlarmCode(code);
        alarmBuilder.setAlarmMessage(message);
        return alarmBuilder.build();
    }

}
