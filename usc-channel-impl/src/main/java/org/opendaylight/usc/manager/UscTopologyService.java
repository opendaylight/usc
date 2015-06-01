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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscShardService;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.usc.util.UscDtoUtils;
import org.opendaylight.usc.util.UscServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.TerminationPointId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.UscTopology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.UscTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.ChannelAlarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.session.attributes.SessionAlarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.session.attributes.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Channel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.TopologyKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Manager all of nodes and Channels of topology, which contains only local
 * controller and USC related staffs All of methods should be thread safe for
 * asynchronous event handler
 */
public class UscTopologyService {

    /**
     * controller node type string
     */
    public static final String NODE_TYPE_CONTROLLER = "Controller";
    /**
     * channel channel type string
     */
    public static final String Channel_TYPE_CHANNEL = "channel";
    /**
     * network device node type string
     */
    public static final String NODE_TYPE_NETWORK_DEVICE = "Device";
    private static final Logger LOG = LoggerFactory.getLogger(UscTopologyService.class);
    private static UscTopologyService topoService = new UscTopologyService();
    private Node localController;
    private Topology localTopology;
    private String localHostName;
    @SuppressWarnings("rawtypes")
    private UscShardService shardService;
    private UscConfigurationService configService;
    private long maxErrorNumber = 0;
    private InstanceIdentifier<Topology> topoIdentifier;
    private List<Channel> localChannelList = new CopyOnWriteArrayList<Channel>();
    private List<Node> localNodeList = new CopyOnWriteArrayList<Node>();
    private Hashtable<String, Integer> nodeReferList = new Hashtable<String, Integer>();
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
    public synchronized void init() {
        shardService = UscServiceUtils.getService(UscShardService.class);
        configService = UscServiceUtils.getService(UscConfigurationService.class);
        maxErrorNumber = configService.getConfigIntValue(UscConfigurationService.USC_MAX_ERROR_NUMER);
        logError = configService.isConfigAsTure(UscConfigurationService.USC_LOG_ERROR_EVENT);
        initLocalHostName();
        TopologyBuilder topoBuilder = new TopologyBuilder();
        TopologyId topoId = new TopologyId(localHostName);
        localTopology = topoBuilder.setTopologyId(topoId).setKey(new TopologyKey(topoId)).setNode(localNodeList)
                .setChannel(localChannelList).build();
        topoIdentifier = UscDtoUtils.getTopologyIdentifier(localHostName);
        initLocalController();
        localNodeList.add(localController);
        updateUscTopology();
    }

    private void initLocalHostName() {
        try {
            localHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            if (LOG.isDebugEnabled()) {
                e.printStackTrace();
            }
            localHostName = "Random" + Math.random() + "";
            LOG.warn("Failed to get local hostname!create a random key for local controller.nodeId = " + localHostName
                    + ", error message is " + e.getMessage());
        }
    }

    private void initLocalController() {
        NodeBuilder nodeBuilder = new NodeBuilder();
        NodeId nodeId = new NodeId(localHostName);
        localController = nodeBuilder.setNodeType(NODE_TYPE_CONTROLLER).setNodeId(nodeId).setKey(new NodeKey(nodeId))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void updateUscTopology() {
        UscTopology UscTopology = null;
        if (shardService != null) {
            if (existLocalTopology()) {
                // reset since USC service were restarted.
                updateShard();
                LOG.info("The local topology already exists in Shard, and has been reseted ");
                return;
            }
            UscTopology = (UscTopology) shardService.read(LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getUscTopologyIdentifier());
            boolean UscTopologyExist = false;
            if (UscTopology != null) {
                LOG.info("Before initialize USC root, Topologies already has " + UscTopology.getTopology().size()
                        + ",which is set by other controller.");
                UscTopologyExist = true;
            }
            UscTopologyBuilder UscTopologyBuilder = new UscTopologyBuilder();
            List<Topology> topoList = new ArrayList<Topology>();
            topoList.add(localTopology);
            UscTopology = UscTopologyBuilder.setTopology(topoList).build();
            if (UscTopologyExist) {
                shardService.merge(LogicalDatastoreType.OPERATIONAL, UscDtoUtils.getUscTopologyIdentifier(),
                        UscTopology);
            } else {
                shardService.write(LogicalDatastoreType.OPERATIONAL, UscDtoUtils.getUscTopologyIdentifier(),
                        UscTopology);
            }
        } else {
            LOG.error("The shard manager is not initialized!UscTopology can't be initialized for Shard.");
        }
    }

    private boolean existLocalTopology() {
        if (shardService != null) {
            @SuppressWarnings("unchecked")
            DataObject tmp = shardService.read(LogicalDatastoreType.OPERATIONAL, topoIdentifier);
            if (tmp != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private synchronized void updateShard() {
        if (shardService != null) {
            
            final CountDownLatch finished = new CountDownLatch(1);
            
            shardService.write(LogicalDatastoreType.OPERATIONAL, topoIdentifier, localTopology,
                    new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(final Void result) {
                            finished.countDown();
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            finished.countDown();
                            LOG.error("Failed to update topology data using shard service.");
                        }
                    });
            // wait for shard non-synchronized operation finished for
            // synchronize
            // this operation
            try {
                finished.await(60, TimeUnit.SECONDS);
                
            } catch (InterruptedException e) {
                LOG.error("Thread sleep has a exception:" + e.getMessage());
            }
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
     * 
     * @return the whole USC topology
     */
    @SuppressWarnings({ "unchecked" })
    public synchronized Topology getWholeUscTopology() {
        if (shardService == null) {
            LOG.error("UscShardService is not initialized!");
            return null;
        }
        UscTopology uscTopology = (UscTopology) shardService.read(LogicalDatastoreType.OPERATIONAL,
                UscDtoUtils.getUscTopologyIdentifier());
        if (uscTopology == null) {
            LOG.error("Failed to get usc topology root data.");
            return null;
        }
        String id = null;
        Topology topology = null;
        Topology wholeUscTopology = UscTopologyFactory.createTopology("usc", new ArrayList<Node>(),
                new ArrayList<Channel>());
        for (Topology topo : uscTopology.getTopology()) {
            id = topo.getTopologyId().getValue();
            topology = (Topology) shardService.read(LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getTopologyIdentifier(id));
            if (topology != null) {
                UscDtoUtils.mergeChannelList(UscDtoUtils.mergeNodeList(wholeUscTopology, topology), topology);
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
    public synchronized Node getNode(String nodeId) {
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
    public synchronized void addNode(Node node) {
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
    public synchronized Node removeNode(String nodeId) {
        if (nodeId != null && !nodeId.equals("")) {
            Integer num = nodeReferList.get(nodeId);
            Node node = getNode(nodeId);
            if (node == null) {
                LOG.warn("removeNode:Node is not found for device id = " + nodeId);
                return null;
            }
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
    public synchronized boolean sameNodeId(String id, Node node) {
        return id.equals(node.getNodeId().getValue());
    }

    /**
     * add a channel to topology channel list
     * 
     * @param channel
     *            the adding channel
     */
    public synchronized void addChannel(Channel channel) {
        if (channel != null) {
            localTopology.getChannel().add(channel);
            addNode(UscTopologyFactory.createNode(channel.getDestination().getDestNode().getValue(),
                    UscTopologyService.NODE_TYPE_NETWORK_DEVICE));
            updateShard();
        }
    }

    /**
     * remove the channel specified by the destination id,and same time will
     * remove the corresponding source and destination nodes
     * 
     * @param destinationId
     *            node id of the destination node
     * @param type
     *            the type of channel
     * @return the channel of the removing channel,if the channel related with
     *         specified destination id is not exists then return null
     */
    public synchronized Channel removeChannel(String destinationId, String type) {
        if (destinationId != null) {
            Channel channel = getChannel(destinationId, type);
            if (channel != null) {
                localTopology.getChannel().remove(channel);
                // source controller node only add once on initializing
                // removeNode(channel.getSource().getSourceNode().getValue());
                removeNode(channel.getDestination().getDestNode().getValue());
                updateShard();
                return channel;
            } else {
                LOG.warn("Not found specified destionation.id =" + destinationId);
            }
        }
        return null;
    }

    /**
     * update channel information, and update the shard data of local topology
     * 
     * @param channel
     *            the new channel
     * @return old channel
     */
    public synchronized Channel updateChannel(Channel channel) {
        if (channel != null) {
            Channel oldChannel = removeChannel(channel.getDestination().getDestNode().getValue(),
                    channel.getChannelType());
            addChannel(channel);
            updateShard();
            return oldChannel;
        }
        return null;
    }

    public synchronized void updateChannel(Channel channel, Session session, boolean removeFlag) {
        Session oldSession = getSession(channel, session.getSessionId().getValue());
        if (oldSession != null) {
            channel.getSession().remove(oldSession);
        }
        if (!removeFlag) {
            channel.getSession().add(session);
        }
        String destinationId = channel.getDestination().getDestNode().getValue();
        Node deviceNode = UscTopologyFactory.createNode(destinationId, UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
        channel = UscTopologyFactory.createChannel(getLocalController(), deviceNode, channel.getKey().getChannelId()
                .getValue(), channel.getChannelType(), UscTopologyFactory.isCallHome(channel.getCallHome()),
                channel.getBytesIn(), channel.getBytesOut(), channel.getChannelAlarm(), channel.getSession());
        updateChannel(channel);
    }

    public synchronized void updateTransaction(Channel channel, Session session, long bytesIn, long bytesOut) {
        Session oldSession = getSession(channel, session.getSessionId().getValue());
        if (oldSession != null) {
            channel.getSession().remove(oldSession);
        }
        channel.getSession().add(session);
        String destinationId = channel.getDestination().getDestNode().getValue();
        Node deviceNode = UscTopologyFactory.createNode(destinationId, UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
        channel = UscTopologyFactory.createChannel(getLocalController(), deviceNode, channel.getKey().getChannelId()
                .getValue(), channel.getChannelType(), UscTopologyFactory.isCallHome(channel.getCallHome()),
                channel.getBytesIn() + bytesIn, channel.getBytesOut() + bytesOut, channel.getChannelAlarm(),
                channel.getSession());
        updateChannel(channel);
    }

    /**
     * get first channel of specified destination id
     * 
     * @param destinationId
     *            destination node id
     * @return the channel, if the channel related with specified destination id
     *         is not exists then return null
     */
    public synchronized Channel getChannel(String destinationId, String type) {
        for (Channel channel : localTopology.getChannel()) {
            if (channel.getDestination().getDestNode().getValue().equals(destinationId)
                    && channel.getChannelType().equals(type)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * add session to the channel which has the specified destination id
     * 
     * @param destinationId
     *            destination id
     * @param type
     *            channel type
     * @param session
     *            the adding session
     */
    public synchronized void addSession(String destinationId, String type, Session session) {
        Channel channel = getChannel(destinationId, type);
        if (channel != null) {
            updateChannel(channel, session, false);
        } else {
            LOG.warn("Not found specified destionation.id =" + destinationId);
        }
    }

    /**
     * remove session from the channel which has the specified destination id
     * 
     * @param destinationId
     *            destination id
     * @param type
     *            channel type
     * @param sessionId
     *            the removing session
     */
    public synchronized Session removeSession(String destinationId, String type, String sessionId) {
        Channel channel = getChannel(destinationId, type);
        if (channel != null) {
            Session session = getSession(channel, sessionId);
            if (session != null) {
                updateChannel(channel, session, true);
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
     * get session from the channel with specified session id
     * 
     * @param channel
     *            the target channel for getting
     * @param sessionId
     *            specified session id
     * @return if find the session which has the session id, return the session
     *         other wise return null
     */
    public synchronized Session getSession(Channel channel, String sessionId) {
        for (Session session : channel.getSession()) {
            if (session.getSessionId().getValue().equals(sessionId)) {
                return session;
            }
        }
        return null;
    }

    /**
     * get session which has specified session id from the channel which has the
     * destination id
     * 
     * @param destinationId
     *            the target channel for getting
     * @param sessionId
     *            specified session id
     * @return if find the session which has the session id, return the session
     *         other wise return null
     */
    public synchronized Session getSession(String destinationId, String sessionId) {
        for (Channel channel : localTopology.getChannel()) {
            if (channel.getDestination().getDestNode().getValue().equals(destinationId)) {
                for (Session session : channel.getSession()) {
                    if (session.getSessionId().getValue().equals(sessionId)) {
                        return session;
                    }
                }
            }
        }
        return null;
    }

    /**
     * update the transaction data values of the session specified by session
     * id, the session belongs to the channel which specified by the destination
     * id
     * 
     * @param destinationId
     *            specified destination id
     * @param sessionId
     *            specified session id
     * @param type
     *            channel type
     * @param bytesIn
     *            bytes in number
     * @param bytesOut
     *            bytes out number
     */
    public synchronized void updateSessionTransaction(String destinationId, String type, String sessionId,
            long bytesIn, long bytesOut) {
        Channel channel = getChannel(destinationId, type);
        if (channel == null) {
            LOG.warn("channel is not found for device({}),type({})", destinationId, type);
            return;
        }
        Session session = getSession(destinationId, sessionId);
        if (session == null) {
            LOG.warn("Session is not found for device[" + destinationId + "] and session[" + sessionId + "].");
            return;
        }
        TerminationPoint tp = session.getTerminationPoint();
        if (tp == null) {
            LOG.warn("TerminationPoint is not found for device[" + destinationId + "] and session[" + sessionId + "].");
            return;
        }
        TerminationPointId tpid = tp.getTerminationPointId();
        if (tpid == null) {
            LOG.warn("TerminationPointId is not found for device[" + destinationId + "] and session[" + sessionId
                    + "].");
            return;
        }
        String tpPort = tpid.getValue();
        session = UscTopologyFactory.createSession(sessionId, tpPort, session.getBytesIn() + bytesIn,
                session.getBytesOut() + bytesOut, session.getSessionAlarm());
        updateTransaction(channel, session, bytesIn, bytesOut);
    }

    /**
     * add error information to the channel which has the specified destination
     * id
     * 
     * @param destinationId
     *            specified destination id
     * @param type
     *            channel type
     * @param alarm
     *            error information object
     */
    public synchronized void addChannelError(String destinationId, String type, ChannelAlarm alarm) {
        if (alarm == null) {
            LOG.error("Channel Error Event: alarm is null for device id = " + destinationId);
            return;
        }
        if (logError) {
            LOG.error("Channel Error Event: device Id = " + destinationId + ",Id = " + alarm.getAlarmId().getValue()
                    + ",Code = " + alarm.getAlarmCode() + ",Message = " + alarm.getAlarmMessage());
            return;
        }
        Channel channel = getChannel(destinationId, type);
        if (channel == null) {
            LOG.warn("Channel is not found for device id = " + destinationId);
            return;
        }
        Node deviceNode = UscTopologyFactory.createNode(destinationId, UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
        List<ChannelAlarm> alarmList = channel.getChannelAlarm();
        addAlarm(alarmList, alarm);
        channel = UscTopologyFactory.createChannel(getLocalController(), deviceNode, channel.getChannelId().getValue(),
                channel.getChannelType(), UscTopologyFactory.isCallHome(channel.getCallHome()), channel.getBytesIn(),
                channel.getBytesOut(), alarmList, channel.getSession());
        updateChannel(channel);
    }

    /**
     * add error information to the session specified by session id, the session
     * belongs to the channel which specified by the destination id
     * 
     * @param destinationId
     *            specified destination id
     * @param type
     *            channel type
     * @param sessionId
     *            specified session id
     * @param alarm
     *            error information object
     */
    public synchronized void addSessionError(String destinationId, String type, String sessionId, SessionAlarm alarm) {
        if (alarm == null) {
            LOG.error("Session Error Event: alarm is null for device id = " + destinationId + ",sessionId = "
                    + sessionId);
            return;
        }
        if (logError) {
            LOG.error("Session Error Event: deviceId = " + destinationId + ",sessionId = " + sessionId + ",Id = "
                    + alarm.getAlarmId().getValue() + ",Code = " + alarm.getAlarmCode() + ",Message = "
                    + alarm.getAlarmMessage());
            return;
        }
        Channel channel = getChannel(destinationId, type);
        if (channel == null) {
            LOG.warn("Channel is not found for device id = " + destinationId);
            return;
        }
        Session session = getSession(destinationId, sessionId);
        if (session == null) {
            LOG.warn("Session is not found for device[" + destinationId + "] and session[" + sessionId + "].");
            return;
        }
        List<SessionAlarm> alarmList = session.getSessionAlarm();
        addAlarm(alarmList, alarm);
        session = UscTopologyFactory.createSession(sessionId, session.getTerminationPoint().getTerminationPointId()
                .getValue(), session.getBytesIn(), session.getBytesOut(), alarmList);
        updateChannel(channel, session, false);
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
    public synchronized void destory() {
        if (shardService != null) {
            // remove all of shard data used by USC
            UscTopology uscTopology = (UscTopology) shardService.read(LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getUscTopologyIdentifier());
            for (Topology topo : uscTopology.getTopology()) {
                shardService.delete(LogicalDatastoreType.OPERATIONAL,
                        UscDtoUtils.getTopologyIdentifier(topo.getTopologyId().getValue()));
            }
            shardService.delete(LogicalDatastoreType.OPERATIONAL, UscDtoUtils.getUscTopologyIdentifier());
        }
    }

}
