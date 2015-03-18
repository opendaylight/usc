/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.usc.manager.api.UscConfiguration;
import org.opendaylight.usc.manager.api.UscShardAccess;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.usc.util.UscDtoUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.UscRoot;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.UscRootBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Alarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager all of nodes and links of topology, which contains only local
 * controller and USC related staffs All of methods should be thread safe for
 * asynchronous event handler
 */
public class UscTopologyManager {

    /**
     * controller node type string
     */
    public static final String NODE_TYPE_CONTROLLER = "Controller";
    /**
     * channel link type string
     */
    public static final String LINK_TYPE_CHANNEL = "Channel";
    /**
     * network device node type string
     */
    public static final String NODE_TYPE_NETWORK_DEVICE = "Network Device";
    private static final Logger LOG = LoggerFactory.getLogger(UscTopologyManager.class);

    private Node localController;
    private Topology localTopology;
    private String localHostName;
    private UscShardAccess shardManager;
    private long maxErrorNumber = 0;
    private InstanceIdentifier<Topology> topoIdentifier;
    private List<Link> localLinkList = new CopyOnWriteArrayList<Link>();
    private List<Node> localNodeList = new CopyOnWriteArrayList<Node>();
    private Hashtable<String, Integer> nodeReferList = new Hashtable<String, Integer>();

    /**
     * create topology manager of USC using a given shard data manger
     * 
     * @param shardManager
     *            shard data manger
     */
    public UscTopologyManager(UscShardAccess shardManager) {
        this.shardManager = shardManager;
        maxErrorNumber = UscConfigurationManager.getInstance().getConfigIntValue(UscConfiguration.USC_MAX_ERROR_NUMER);
        initLocalHostName();
        TopologyBuilder topoBuilder = new TopologyBuilder();
        TopologyId topoId = new TopologyId(localHostName);
        localTopology = topoBuilder.setTopologyId(topoId).setKey(new TopologyKey(topoId)).setNode(localNodeList)
                .setLink(localLinkList).build();
        topoIdentifier = UscDtoUtils.getTopologyIdentifier(localHostName);
        initLocalController();
        localNodeList.add(localController);
        updateUscRoot();
    }

    private void initLocalHostName() {
        try {
            localHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            localHostName = "Random" + Math.random() + "";
            LOG.warn("Failed to get local hostname!create a random key for local controller.nodeId = " + localHostName);
        }
    }

    private void initLocalController() {
        NodeBuilder nodeBuilder = new NodeBuilder();
        NodeId nodeId = new NodeId(localHostName);
        localController = nodeBuilder.setNodeType(NODE_TYPE_CONTROLLER).setNodeId(nodeId).setKey(new NodeKey(nodeId))
                .build();
    }

    private void updateUscRoot() {
        UscRootBuilder uscRootBuilder = new UscRootBuilder();
        List<Topology> topoList = new ArrayList<Topology>();
        topoList.add(localTopology);
        UscRoot uscRoot = uscRootBuilder.setTopology(topoList).build();
        if (shardManager != null) {
            shardManager.merge(LogicalDatastoreType.OPERATIONAL, UscDtoUtils.getUscTopologyIdentifier(), uscRoot);
        } else {
            LOG.error("Not initialize the shard manager!");
        }
    }

    private void updateShard() {
        if (shardManager != null) {
            shardManager.write(LogicalDatastoreType.OPERATIONAL, topoIdentifier, localTopology);
        } else {
            LOG.error("Not initialize the shard manager!");
        }
    }

    /**
     * get local controller node
     * 
     * @return controller node
     */
    public Node getLocalController() {
        return localController;
    }

    /**
     * get local topology
     * 
     * @return local topology
     */
    public Topology getLocalTopolgy() {
        return localTopology;
    }

    /**
     * get node through the id of node
     * 
     * @param nodeId
     *            node id
     * @return if node id exists than return it, other wise create a new node
     *         using node id
     */
    public Node getNode(String nodeId) {
        List<Node> list = localTopology.getNode();
        for (Node node : localTopology.getNode()) {
            if (sameNodeId(nodeId, node)) {
                return node;
            }
        }
        return null;
    }

    /**
     * add a node to local topology, when the node exists in node list of
     * topology, then only update the refer number of the node
     * 
     * @param node
     *            the adding node
     */
    public void addNode(Node node) {
        if (node != null) {
            String nodeId = node.getKey().getNodeId().getValue();
            Integer num = nodeReferList.get(nodeId);
            // filter same node using nodeId
            // Note: here only has the nodes related with current controller,
            // but all of nodes including nodes related with other controllers
            // should have unique nodeId
            if (num == null) {
                // not exsits, add node and one refer number
                localNodeList.add(node);
                nodeReferList.put(nodeId, 1);
            } else {
                // exsits,only add refer number
                nodeReferList.put(nodeId, num + 1);
            }
        }
    }

    /**
     * remove the node specified by the node id, when the refer number of the
     * node is more than one,then only minus one refer number. when the refer
     * number is only one,the will remove the node too
     * 
     * @param nodeId
     *            node id of the removing node
     * @return the node of the removing node,if node is not exists then return
     *         null
     */
    public Node removeNode(String nodeId) {
        if (nodeId != null && !nodeId.equals("")) {
            Integer num = nodeReferList.get(nodeId);
            Node node = getNode(nodeId);
            if (num == null || num == 1) {
                // only one, so can remove node and
                // node refer key
                localNodeList.remove(node);
                nodeReferList.remove(nodeId);
            } else {
                // more than one ,only minus refer number
                nodeReferList.put(nodeId, num - 1);
            }
            return node;
        }
        return null;
    }

    /**
     * check the node has same id with the specified id
     * 
     * @param id
     *            a node id
     * @param node
     *            a node
     * @return if the node has same id with the specified id then return
     *         true,other wise return false
     */
    public boolean sameNodeId(String id, Node node) {
        return id.equals(node.getNodeId().getValue());
    }

    /**
     * add a link to topology link list
     * 
     * @param channel
     *            the adding link
     */
    public void addLink(Link channel) {
        if (channel != null) {
            List<Link> list = localTopology.getLink();
            if (list == null) {
                list = new CopyOnWriteArrayList<Link>();
            }
            list.add(channel);
            updateShard();
        }
    }

    /**
     * remove the link specified by the destination id,and same time will remove
     * the corresponding source and destination nodes
     * 
     * @param destinationId
     *            node id of the destination node
     * @return the link of the removing link,if the link related with specified
     *         destination id is not exists then return null
     */
    public Link removeLink(String destinationId) {
        if (destinationId != null) {
            Link link = getLink(destinationId);
            if (link != null) {
                localTopology.getLink().remove(link);
                removeNode(link.getSource().getSourceNode().getValue());
                removeNode(link.getDestination().getDestNode().getValue());
                updateShard();
                return link;
            } else {
                LOG.warn("Not found specified destionation.id =" + destinationId);
            }
        }
        return null;
    }

    /**
     * update link information, and update the shard data of local topology
     * 
     * @param channel
     *            the new link
     * @return old link
     */
    public Link updateLink(Link channel) {
        if (channel != null) {
            Link oldLink = removeLink(channel.getDestination().getDestNode().getValue());
            addLink(channel);
            updateShard();
            return oldLink;
        }
        return null;
    }

    /**
     * get first link of specified destination id
     * 
     * @param destinationId
     *            destination node id
     * @return the link, if the link related with specified destination id is
     *         not exists then return null
     */
    public Link getLink(String destinationId) {
        for (Link link : localTopology.getLink()) {
            if (link.getDestination().getDestNode().getValue().equals(destinationId)) {
                return link;
            }
        }
        return null;
    }

    /**
     * add session to the link which has the specified destination id
     * 
     * @param destinationId
     *            destination id
     * @param session
     *            the adding session
     */
    public void addSession(String destinationId, Session session) {
        Link link = getLink(destinationId);
        if (link != null) {
            List list = link.getSession();
            if (list == null) {
                list = new CopyOnWriteArrayList();
            }
            list.add(session);
        } else {
            LOG.warn("Not found specified destionation.id =" + destinationId);
        }
    }

    /**
     * remove session from the link which has the specified destination id
     * 
     * @param destinationId
     *            destination id
     * @param session
     *            the removing session
     */
    public Session removeSession(String destinationId, String sessionId) {
        Link link = getLink(destinationId);
        if (link != null) {
            Session session = getSession(link, sessionId);
            if (session != null) {
                link.getSession().remove(session);
                return session;
            } else {
                LOG.warn("Not found specified Session.id =" + sessionId);
            }
        } else {
            LOG.warn("Not found specified destionation.id =" + destinationId);
        }
        return null;
    }

    /**
     * update session of the link which has the specified destination id
     * 
     * @param destinationId
     *            destination id
     * @param session
     *            the updating session
     */
    public Session updateSession(String destinationId, Session session) {
        Link link = getLink(destinationId);
        if (link != null) {
            Session oldSession = getSession(link, session.getSessionId().getValue());
            if (oldSession != null) {
                link.getSession().remove(oldSession);
                link.getSession().add(session);
                return oldSession;
            } else {
                LOG.warn("Not found specified Session.id =" + session.getSessionId().getValue());
            }
        } else {
            LOG.warn("Not found specified destionation.id =" + destinationId);
        }
        return null;
    }

    /**
     * update session of the link
     * 
     * @param Link
     *            updating target link
     * @param session
     *            the updating session
     * @return old session if the session is exists, other wise return null
     */
    public Session updateSession(Link link, Session session) {
        if (link != null) {
            Session oldSession = getSession(link, session.getSessionId().getValue());
            if (oldSession != null) {
                link.getSession().remove(oldSession);
                link.getSession().add(session);
                return oldSession;
            } else {
                LOG.warn("Not found specified Session.id =" + session.getSessionId().getValue());
            }
        } else {
            LOG.warn("link is null!");
        }
        return null;
    }

    /**
     * get session from the link with specified session id
     * 
     * @param link
     *            the target link for getting
     * @param sessionId
     *            specified session id
     * @return if find the session which has the session id, return the session
     *         other wise return null
     */
    public Session getSession(Link link, String sessionId) {
        for (Session session : link.getSession()) {
            if (session.getSessionId().getValue().equals(sessionId)) {
                return session;
            }
        }
        return null;
    }

    /**
     * get session which has specified session id from the link which has the
     * destination id
     * 
     * @param destinationId
     *            the target link for getting
     * @param sessionId
     *            specified session id
     * @return if find the session which has the session id, return the session
     *         other wise return null
     */
    public Session getSession(String destinationId, String sessionId) {
        for (Link link : localTopology.getLink()) {
            if (link.getDestination().getDestNode().getValue().equals(destinationId)) {
                for (Session session : link.getSession()) {
                    if (session.getSessionId().getValue().equals(sessionId)) {
                        return session;
                    }
                }
            }
        }
        return null;
    }

    /**
     * update the transaction data values of the link which specified by the
     * destination id
     * 
     * @param destinationId
     *            specified destination id
     * @param bytesIn
     *            bytes in number
     * @param bytesOut
     *            bytes out number
     */
    public void updateLinkTransaction(String destinationId, long bytesIn, long bytesOut) {
        Link link = getLink(destinationId);
        Node deviceNode = UscTopologyFactory.createNode(destinationId, UscTopologyManager.NODE_TYPE_NETWORK_DEVICE);
        String key = UscTopologyManager.NODE_TYPE_NETWORK_DEVICE + ":" + getLocalController().getNodeId() + "-"
                + UscTopologyManager.NODE_TYPE_NETWORK_DEVICE + ":" + destinationId;
        link = UscTopologyFactory.createLink(getLocalController(), deviceNode, key, link.getLinkType(),
                UscTopologyFactory.isCallHome(link.getCallHome()), link.getBytesIn() + bytesIn, link.getBytesOut()
                        + bytesOut, link.getAlarm(), link.getSession());

    }

    /**
     * update the transaction data values of the session specified by session
     * id, the session belongs to the link which specified by the destination id
     * 
     * @param destinationId
     *            specified destination id
     * @param sessionId
     *            specified session id
     * @param bytesIn
     *            bytes in number
     * @param bytesOut
     *            bytes out number
     */
    public void updateSessionTransaction(String destinationId, String sessionId, long bytesIn, long bytesOut) {
        Link link = getLink(destinationId);
        Node deviceNode = UscTopologyFactory.createNode(destinationId, UscTopologyManager.NODE_TYPE_NETWORK_DEVICE);
        Session session = getSession(destinationId, sessionId);
        session = UscTopologyFactory.createSession(sessionId, session.getTerminalPoint().getTerminalPointId()
                .getValue(), link.getBytesIn() + bytesIn, link.getBytesOut() + bytesOut, session.getAlarm());
        updateSession(link, session);
        // update link date at same time
        updateLinkTransaction(destinationId, bytesIn, bytesOut);
        updateLink(link);
    }

    /**
     * add error information to the link which has the specified destination id
     * 
     * @param destinationId
     *            specified destination id
     * @param alarm
     *            error information object
     */
    public void addLinkError(String destinationId, Alarm alarm) {
        Link link = getLink(destinationId);
        Node deviceNode = UscTopologyFactory.createNode(destinationId, UscTopologyManager.NODE_TYPE_NETWORK_DEVICE);
        String key = UscTopologyManager.NODE_TYPE_NETWORK_DEVICE + ":" + getLocalController().getNodeId() + "-"
                + UscTopologyManager.NODE_TYPE_NETWORK_DEVICE + ":" + destinationId;
        List alarmList = link.getAlarm();
        addAlarm((LinkedList) alarmList, alarm);
        link = UscTopologyFactory.createLink(getLocalController(), deviceNode, key, link.getLinkType(),
                UscTopologyFactory.isCallHome(link.getCallHome()), link.getBytesIn(), link.getBytesOut(), alarmList,
                link.getSession());
        updateLink(link);
    }

    /**
     * add error information to the session specified by session id, the session
     * belongs to the link which specified by the destination id
     * 
     * @param destinationId
     *            specified destination id
     * @param sessionId
     *            specified session id
     * @param alarm
     *            error information object
     */
    public void addSessionError(
            String destinationId,
            String sessionId,
            org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm alarm) {
        Link link = getLink(destinationId);
        Node deviceNode = UscTopologyFactory.createNode(destinationId, UscTopologyManager.NODE_TYPE_NETWORK_DEVICE);
        Session session = getSession(destinationId, sessionId);
        List alarmList = session.getAlarm();
        addAlarm((LinkedList) alarmList, alarm);
        session = UscTopologyFactory.createSession(sessionId, session.getTerminalPoint().getTerminalPointId()
                .getValue(), link.getBytesIn(), link.getBytesOut(), alarmList);
        updateSession(link, session);
        updateLink(link);
    }

    private void addAlarm(LinkedList list, Object alarm) {
        if (list == null) {
            list = (LinkedList) Collections.synchronizedList(new LinkedList());
        }
        // control the max number of errors
        for (int i = 0; i < list.size() - maxErrorNumber; i++) {
            list.pop();
        }
        list.add(alarm);
    }

    /**
     * remove all of topolofy manager used shard data
     */
    public void destory() {
        if (shardManager != null) {
            // remove all of shard data used by USC
            UscRoot uscTopology = (UscRoot) shardManager.read(LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getUscTopologyIdentifier());
            for (Topology topo : uscTopology.getTopology()) {
                shardManager.delete(LogicalDatastoreType.OPERATIONAL,
                        UscDtoUtils.getTopologyIdentifier(topo.getTopologyId().getValue()));
            }
            shardManager.delete(LogicalDatastoreType.OPERATIONAL, UscDtoUtils.getUscTopologyIdentifier());
        }
    }

}
