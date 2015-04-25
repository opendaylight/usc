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
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscShardService;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.usc.util.UscDtoUtils;
import org.opendaylight.usc.util.UscServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.UscRoot;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.UscRootBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Alarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;

/**
 * Manager all of nodes and links of topology, which contains only local
 * controller and USC related staffs All of methods should be thread safe for
 * asynchronous event handler
 */
public class UscTopologyService {

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
    public static final String NODE_TYPE_NETWORK_DEVICE = "Device";
    private static final Logger LOG = LoggerFactory
            .getLogger(UscTopologyService.class);
    private static UscTopologyService topoService = new UscTopologyService();
    private Node localController;
    private Topology localTopology;
    private String localHostName;
    @SuppressWarnings("rawtypes")
    private UscShardService shardService;
    private UscConfigurationService configService;
    private long maxErrorNumber = 0;
    private InstanceIdentifier<Topology> topoIdentifier;
    private List<Link> localLinkList = new CopyOnWriteArrayList<Link>();
    private List<Node> localNodeList = new CopyOnWriteArrayList<Node>();
    private Hashtable<String, Integer> nodeReferList = new Hashtable<String, Integer>();
    private boolean finished = false;
    private boolean logError = true;

    private UscTopologyService() {

    }

    /**
     * get the unique topology service instance
     * 
     * @return topology service instance
     */
    public static UscTopologyService getInstance() {
        return topoService;
    }

    /**
     * create topology manager of USC using a given shard data manger
     */
    public void init() {
        shardService = UscServiceUtils.getService(UscShardService.class);
        configService = UscServiceUtils
                .getService(UscConfigurationService.class);
        maxErrorNumber = configService
                .getConfigIntValue(UscConfigurationService.USC_MAX_ERROR_NUMER);
        logError = configService
                .isConfigAsTure(UscConfigurationService.USC_LOG_ERROR_EVENT);
        initLocalHostName();
        TopologyBuilder topoBuilder = new TopologyBuilder();
        TopologyId topoId = new TopologyId(localHostName);
        localTopology = topoBuilder.setTopologyId(topoId)
                .setKey(new TopologyKey(topoId)).setNode(localNodeList)
                .setLink(localLinkList).build();
        topoIdentifier = UscDtoUtils.getTopologyIdentifier(localHostName);
        initLocalController();
        localNodeList.add(localController);
        updateUscRoot();
        //for cleaning the former shard data
        updateShard();
    }

    private void initLocalHostName() {
        try {
            localHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            localHostName = "Random" + Math.random() + "";
            LOG.warn("Failed to get local hostname!create a random key for local controller.nodeId = "
                    + localHostName);
        }
    }

    private void initLocalController() {
        NodeBuilder nodeBuilder = new NodeBuilder();
        NodeId nodeId = new NodeId(localHostName);
        localController = nodeBuilder.setNodeType(NODE_TYPE_CONTROLLER)
                .setNodeId(nodeId).setKey(new NodeKey(nodeId)).build();
    }

    @SuppressWarnings("unchecked")
    private void updateUscRoot() {
        UscRootBuilder uscRootBuilder = new UscRootBuilder();
        List<Topology> topoList = new ArrayList<Topology>();
        topoList.add(localTopology);
        UscRoot uscRoot = null;
        if (shardService != null) {
        	uscRoot = (UscRoot) shardService.read(LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getUscTopologyIdentifier());
        	if(uscRoot != null){
        		LOG.info("topology number = "+uscRoot.getTopology().size());
        	}
        } else {
            LOG.error("The shard manager is not initialized!");
        }
        uscRoot = uscRootBuilder.setTopology(topoList).build();
        if (shardService != null) {
            shardService.merge(LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getUscTopologyIdentifier(), uscRoot);
        } else {
            LOG.error("The shard manager is not initialized!");
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void updateShard() {
        if (shardService != null) {
            shardService.write(LogicalDatastoreType.OPERATIONAL,
                    topoIdentifier, localTopology, new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(final Void result) {
                            finished = true;
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            finished = true;
                            LOG.error("Failed to update topology data using shard service.");
                        }
                    });
            // wait for shard non-synchronized operation finished for
            // synchronize
            // this operation
            int m = 0;
            while (!finished && m < 60) {
                try {
                    Thread.sleep(1000);
                    m++;
                } catch (InterruptedException e) {
                    LOG.error("Thread sleep has a exception:" + e.getMessage());
                }
            }
            finished = false;
        } else {
            LOG.error("The shard manager is not initialized!");
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
     * get the whole USC topology from shard data
     * @return the whole USC topology
     */
    @SuppressWarnings({ "unchecked" })
    public Topology getWholeUscTopology() {
        if (shardService == null) {
            LOG.error("UscShardService is not initialized!");
            return null;
        }
        UscRoot uscTopology = (UscRoot) shardService.read(
                LogicalDatastoreType.OPERATIONAL,
                UscDtoUtils.getUscTopologyIdentifier());
        if(uscTopology == null){
            LOG.error("Failed to get usc topology root data.");
            return null;
        }
        String id = null;
        Topology topology = null;
        Topology wholeUscTopology = UscTopologyFactory.createTopology("usc",
                new ArrayList<Node>(), new ArrayList<Link>());
        for (Topology topo : uscTopology.getTopology()) {
            id = topo.getTopologyId().getValue();
            topology = (Topology) shardService.read(
                    LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getTopologyIdentifier(id));
            if (topology != null) {
                UscDtoUtils.mergeLinkList(
                        UscDtoUtils.mergeNodeList(wholeUscTopology, topology),
                        topology);
            }
        }
        return wholeUscTopology;
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
            if (num == null) {
                return null;
            } else if (num <= 1) {
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
                // source controller node only add once on initializing
                // removeNode(link.getSource().getSourceNode().getValue());
                removeNode(link.getDestination().getDestNode().getValue());
                updateShard();
                return link;
            } else {
                LOG.warn("Not found specified destionation.id ="
                        + destinationId);
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
            Link oldLink = removeLink(channel.getDestination().getDestNode()
                    .getValue());
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
            if (link.getDestination().getDestNode().getValue()
                    .equals(destinationId)) {
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
            List<Session> list = link.getSession();
            if (list == null) {
                list = new CopyOnWriteArrayList<Session>();
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
     * @param sessionId
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
            Session oldSession = getSession(link, session.getSessionId()
                    .getValue());
            if (oldSession != null) {
                link.getSession().remove(oldSession);
                link.getSession().add(session);
                return oldSession;
            } else {
                LOG.warn("Not found specified Session.id ="
                        + session.getSessionId().getValue());
            }
        } else {
            LOG.warn("Not found specified destionation.id =" + destinationId);
        }
        return null;
    }

    /**
     * update session of the link
     * 
     * @param link
     *            updating target link
     * @param session
     *            the updating session
     * @return old session if the session is exists, other wise return null
     */
    public Session updateSession(Link link, Session session) {
        if (link != null) {
            Session oldSession = getSession(link, session.getSessionId()
                    .getValue());
            if (oldSession != null) {
                link.getSession().remove(oldSession);
                link.getSession().add(session);
                return oldSession;
            } else {
                LOG.warn("Not found specified Session.id ="
                        + session.getSessionId().getValue());
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
            if (link.getDestination().getDestNode().getValue()
                    .equals(destinationId)) {
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
    public void updateLinkTransaction(String destinationId, long bytesIn,
            long bytesOut) {
        Link link = getLink(destinationId);
        Node deviceNode = UscTopologyFactory.createNode(destinationId,
                UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
        String key = UscTopologyService.NODE_TYPE_NETWORK_DEVICE + ":"
                + getLocalController().getNodeId() + "-"
                + UscTopologyService.NODE_TYPE_NETWORK_DEVICE + ":"
                + destinationId;
        link = UscTopologyFactory.createLink(getLocalController(), deviceNode,
                key, link.getLinkType(),
                UscTopologyFactory.isCallHome(link.getCallHome()),
                link.getBytesIn() + bytesIn, link.getBytesOut() + bytesOut,
                link.getAlarm(), link.getSession());

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
    public void updateSessionTransaction(String destinationId,
            String sessionId, long bytesIn, long bytesOut) {
        Link link = getLink(destinationId);
        Session session = getSession(destinationId, sessionId);
        session = UscTopologyFactory.createSession(sessionId, session
                .getTerminalPoint().getTerminalPointId().getValue(),
                link.getBytesIn() + bytesIn, link.getBytesOut() + bytesOut,
                session.getAlarm());
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
        if (alarm == null) {
            LOG.error("Channel Error Event: alarm is null for device id = "
                    + destinationId);
            return;
        }
        if (logError) {
            LOG.error("Channel Error Event: device Id = " + destinationId
                    + ",Id = " + alarm.getAlarmId().getValue() + ",Code = "
                    + alarm.getAlarmCode() + ",Message = "
                    + alarm.getAlarmMessage());
            return;
        }
        Link link = getLink(destinationId);
        Node deviceNode = UscTopologyFactory.createNode(destinationId,
                UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
        List<Alarm> alarmList = link.getAlarm();
        addAlarm(alarmList, alarm);
        link = UscTopologyFactory.createLink(getLocalController(), deviceNode,
                link.getLinkId().getValue(), link.getLinkType(),
                UscTopologyFactory.isCallHome(link.getCallHome()),
                link.getBytesIn(), link.getBytesOut(), alarmList,
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
        if (alarm == null) {
            LOG.error("Session Error Event: alarm is null for device id = "
                    + destinationId + ",sessionId = " + sessionId);
            return;
        }
        if (logError) {
            LOG.error("Session Error Event: deviceId = " + destinationId
                    + ",sessionId = " + sessionId + ",Id = "
                    + alarm.getAlarmId().getValue() + ",Code = "
                    + alarm.getAlarmCode() + ",Message = "
                    + alarm.getAlarmMessage());
            return;
        }
        Link link = getLink(destinationId);
        Session session = getSession(destinationId, sessionId);
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm> alarmList = session
                .getAlarm();
        addAlarm(alarmList, alarm);
        session = UscTopologyFactory.createSession(sessionId, session
                .getTerminalPoint().getTerminalPointId().getValue(),
                link.getBytesIn(), link.getBytesOut(), alarmList);
        updateSession(link, session);
        updateLink(link);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void addAlarm(List list, Object alarm) {
        if (list == null) {
            list = (LinkedList) Collections.synchronizedList(new LinkedList());
        }
        // control the max number of errors
        for (int i = 0; i < list.size() - maxErrorNumber; i++) {
            ((LinkedList) list).pop();
        }
        list.add(alarm);
    }

    /**
     * remove all of topology manager used shard data
     */
    @SuppressWarnings("unchecked")
    public void destory() {
        if (shardService != null) {
            // remove all of shard data used by USC
            UscRoot uscTopology = (UscRoot) shardService.read(
                    LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getUscTopologyIdentifier());
            for (Topology topo : uscTopology.getTopology()) {
                shardService.delete(LogicalDatastoreType.OPERATIONAL,
                        UscDtoUtils.getTopologyIdentifier(topo.getTopologyId()
                                .getValue()));
            }
            shardService.delete(LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getUscTopologyIdentifier());
        }
    }

}
