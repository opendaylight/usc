/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.manager;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.osgi.BundleDelegatingClassLoader;
import com.google.common.base.Preconditions;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.buffer.Unpooled;
import io.netty.channel.local.LocalChannel;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscMonitor;
import org.opendaylight.usc.manager.cluster.UscCommunicatorActor;
import org.opendaylight.usc.manager.cluster.UscDeviceMountTable;
import org.opendaylight.usc.manager.cluster.UscRemoteChannelIdentifier;
import org.opendaylight.usc.manager.cluster.UscRouteIdentifier;
import org.opendaylight.usc.manager.cluster.UscRouteIdentifierData;
import org.opendaylight.usc.manager.cluster.UscRoutedLocalSessionManager;
import org.opendaylight.usc.manager.cluster.UscRoutedRemoteSessionManager;
import org.opendaylight.usc.manager.cluster.message.UscRemoteDataMessage;
import org.opendaylight.usc.manager.cluster.message.UscRemoteExceptionMessage;
import org.opendaylight.usc.manager.cluster.message.UscRemoteMessage;
import org.opendaylight.usc.manager.monitor.UscMonitorImpl;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionTransactionEvent;
import org.opendaylight.usc.manager.monitor.evt.base.UscErrorLevel;
import org.opendaylight.usc.plugin.UscConnectionManager;
import org.opendaylight.usc.plugin.exception.UscChannelException;
import org.opendaylight.usc.plugin.exception.UscException;
import org.opendaylight.usc.plugin.exception.UscSessionException;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.plugin.model.UscChannelImpl;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.opendaylight.usc.protocol.UscData;
import org.opendaylight.usc.util.UscServiceUtils;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConversions;

/**
 * the route broker service, create the actor system and manage all of route
 * information for USC, and process all request for remote channel
 *
 */
public class UscRouteBrokerService {
    private static final String ACTOR_SYSTEM_NAME = "odl-cluster-usc";
    private static final String COMMUNICATOR_ACTOR_NAME = "UscCommunicator";
    private static final Logger LOG = LoggerFactory.getLogger(UscRouteBrokerService.class);
    private UscDeviceMountTable deviceTable;
    private final UscRoutedRemoteSessionManager remoteSessionManager = new UscRoutedRemoteSessionManager();
    private final UscRoutedLocalSessionManager localSessionManager = new UscRoutedLocalSessionManager();
    private final ConcurrentHashMap<String, UscConnectionManager> connectionManagerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UscRemoteChannelIdentifier, Integer> sessionIdMap = new ConcurrentHashMap<>();
    private ActorRef communicator;
    private static UscRouteBrokerService service = new UscRouteBrokerService();
    private final int MAX_FIXED_SESSION_ID = Character.MAX_VALUE - 1000;
    private ActorSystem actorSystem = null;
    private final Set<ActorSelection> remoteActors = new CopyOnWriteArraySet<>();
    private Config actorSystemConfig = null;
    private Cluster cluster = null;
    private final UscMonitor monitor = new UscMonitorImpl();

    private UscRouteBrokerService() {

    }

    /**
     * create actor system and communicator
     */
    public void init() {
        this.deviceTable = UscDeviceMountTable.getInstance();
        UscConfigurationService configService = UscServiceUtils.getService(UscConfigurationService.class);
        if (configService == null) {
            LOG.error("Failed to get configuration service,can't create ActorSystem and local remote communicator!");
            return;
        }
        BundleDelegatingClassLoader classLoader = new BundleDelegatingClassLoader(
                FrameworkUtil.getBundle(UscRouteBrokerService.class), Thread.currentThread().getContextClassLoader());
        File defaultConfigFile = new File(configService.getConfigStringValue(UscConfigurationService.AKKA_CLUSTER_FILE));
        Preconditions.checkState(defaultConfigFile.exists(), "akka.conf is missing");
        if (defaultConfigFile.exists()) {
            actorSystemConfig = ConfigFactory.load(ConfigFactory.parseFile(defaultConfigFile)).getConfig(
                    ACTOR_SYSTEM_NAME);
            if (actorSystemConfig == null) {
                LOG.error("Failed to create ActorSystem and local remote communicator!");
                return;
            }
            actorSystem = ActorSystem.create(ACTOR_SYSTEM_NAME, actorSystemConfig, classLoader);
            cluster = Cluster.get(actorSystem);
            communicator = actorSystem.actorOf(UscCommunicatorActor.props(), COMMUNICATOR_ACTOR_NAME);
        } else {
            LOG.error("Failed to create ActorSystem and local remote communicator!");
        }
    }

    private void updateActorListFromCluster() {
        List<String> actorList = new ArrayList<>();
        ActorSelection remoteActorSelection = null;
        scala.collection.immutable.List<Address> seedNodeList = cluster.settings().SeedNodes().toList();
        for (Address address : JavaConversions.seqAsJavaList(seedNodeList)) {
            if (isLocalAddress(address)) {
                continue;
            }
            actorList.add(address.toString());
        }
        for (String path : actorList) {
            if (!isLocalActor(path)) {
                path = path + communicator.path().toStringWithoutAddress();
                remoteActorSelection = actorSystem.actorSelection(path);
                if (remoteActorSelection != null) {
                    remoteActors.add(remoteActorSelection);
                } else {
                    LOG.error("Failed to get actor selection for " + path);
                }

            }
        }
    }

    /**
     * process member up event for cluster
     *
     * @param member
     *            the member of cluster
     * @return always true,currently not false
     */
    public boolean clusterMemberUp(Member member) {
        if (isLocalAddress(member.address())) {
            return true;
        }
        String path = member.address() + communicator.path().toStringWithoutAddress();
        ActorSelection remoteActorSelection = actorSystem.actorSelection(path);
        if (remoteActorSelection != null) {
            remoteActors.add(remoteActorSelection);
            LOG.info("Added remote actor selection for " + path + ", remote actor number becomes "
                    + remoteActors.size());
        } else {
            LOG.error("Failed to get actor selection for " + path);
        }
        return true;
    }

    private boolean isLocalAddress(Address address) {
        if (cluster.selfAddress().equals(address)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * process member down event for cluster
     *
     * @param member
     *            the member of cluster
     * @return always true,currently not false
     */
    public boolean clusterMemberDown(Member member) {
        if (isLocalAddress(member.address())) {
            return true;
        }
        for (ActorSelection actorSelection : remoteActors) {
            LOG.info("clusterMemberDown: actorSelection is " + actorSelection.pathString() + ",member is "
                    + member.address());
            if (actorSelection.pathString().contains(member.address().toString())) {
                remoteActors.remove(actorSelection);
                String actorPath = member.address() + communicator.path().toStringWithoutAddress();
                // remove all remote channels related with the specified cluster
                // member
                deviceTable.removeAll(actorPath);
                LOG.info("Succed to remove the remote actor when Member(" + member.address()
                        + ") is down or unreachable.");
                return true;
            }
        }
        LOG.info("Failed to remove the remote actor when Member(" + member.address() + ") is down or unreachable.");
        return false;
    }

    private boolean isLocalActor(String server) {
        try {
            if (InetAddress.getLocalHost().getHostName().equalsIgnoreCase(server)) {
                return true;
            }
        } catch (UnknownHostException e) {
            if (LOG.isDebugEnabled()) {
                e.printStackTrace();
            }
            LOG.warn("Failed to get local hostname!error message is " + e.getMessage());
        }
        return false;
    }

    /**
     * get singleton instance of broker service
     *
     * @return singleton instance of broker service
     */
    public static UscRouteBrokerService getInstance() {
        return service;
    }

    /**
     * check if the route identifier is local session using remote channel for
     * intercepting the request from local channel
     *
     * @param routeId
     *            route identifier
     * @return true for local route identifier, false for others
     */
    public boolean isLocalRemoteSession(UscRouteIdentifier routeId) {
        return localSessionManager.isRemoteMessage(routeId);
    }

    /**
     * check if the route identifier is the remote session using local channel
     * for intercepting the response from agent channel
     *
     * @param routeId
     *            route identifier
     * @return true for remote route identifier, false for others
     */
    public boolean isRemoteSession(UscRouteIdentifier routeId) {
        if (routeId == null) {
            return false;
        }
        return remoteSessionManager.isRemoteSession(routeId);
    }

    /**
     * get actor which has the remote channel of local route identifier
     *
     * @param localRouteId
     *            local route identifier
     * @return actorRef
     */
    public ActorRef getRemoteActorForRequest(UscRouteIdentifier localRouteId) {
        // since route identifier has different hash code , even it is the child
        // of remote channel
        UscRemoteChannelIdentifier remoteChannel = new UscRemoteChannelIdentifier(localRouteId.getInetAddress(),
                localRouteId.getChannelType());
        return deviceTable.getActorRef(remoteChannel);
    }

    /**
     * get actor which sending request to local channel, and will call back to
     * it
     *
     * @param localRouteId
     *            local route identifier which geting from agent channel
     * @return call back actorRef
     */
    public ActorRef getRemoteActorForResponse(UscRouteIdentifier localRouteId) {
        return remoteSessionManager.getActorRef(localRouteId);
    }

    /**
     * check if exist the remote channel in remote device table
     *
     * @param remoteChannel
     *            remote channel
     * @return true for exist,false for others
     */
    public boolean existRemoteChannel(UscRemoteChannelIdentifier remoteChannel) {
        return deviceTable.existRemoteChannel(remoteChannel);
    }

    private UscRouteIdentifier getRemoteRouteIdentifier(UscRouteIdentifier localRouteId) {
        return remoteSessionManager.getRemoteRouteIdentifier(localRouteId);
    }

    /**
     * add remote channel and communicator which belongs to the controller which
     * connected with the remote channel
     *
     * @param remoteChannel
     *            remote channel
     * @param communicator
     *            communicator
     */
    public void addMountedDevice(UscRemoteChannelIdentifier remoteChannel, ActorRef communicator) {
        // since the hash code is different,it can not use UscRouteIdentifier as
        // a UscRemoteChannelIdentifier
        UscRemoteChannelIdentifier filteredRemoteChannel = new UscRemoteChannelIdentifier(
                remoteChannel.getInetAddress(), remoteChannel.getChannelType());
        deviceTable.addEntry(filteredRemoteChannel, communicator);
        if (communicator.compareTo(this.communicator) != 0) {
            ActorSelection remoteActorSelection = actorSystem.actorSelection(communicator.path());
            if (remoteActorSelection != null) {
                remoteActors.add(remoteActorSelection);
            } else {
                LOG.error("Failed to get actor selection for " + communicator.path());
            }
        }
    }

    /**
     * remove remote channel and communicator which belongs to the controller
     * which connected with the remote channel
     *
     * @param remoteChannel
     *            remote channel
     * @param communicator
     *            communicator
     */
    public void removeMountedDevice(UscRemoteChannelIdentifier remoteChannel, ActorRef communicator) {
        deviceTable.removeEntry(remoteChannel, communicator.toString());
        // send channel connection exception to all related local session
        localSessionManager.removeAll(remoteChannel);
        monitor.onEvent(new UscChannelCloseEvent(remoteChannel.getIp(), remoteChannel.getRemoteChannelType()));
        LOG.info("Remove remote channel {}", remoteChannel);
    }

    /**
     * add local session for processing the response which getting from remote
     * controller
     *
     * @param localRouteId
     *            local route identifier
     * @param serverChannel
     *            server local channel
     */
    public void addLocalSession(UscRouteIdentifier localRouteId, LocalChannel serverChannel) {
        localSessionManager.addEntry(localRouteId, serverChannel);
        monitor.onEvent(new UscSessionCreateEvent(localRouteId.getIp(), localRouteId.getRemoteChannelType(),
                localRouteId.getSessionId() + "", localRouteId.getApplicationPort()));
    }

    /**
     * remove local session for processing the response which getting from
     * remote controller
     *
     * @param localRouteId
     *            local route identifier
     */
    public void removeLocalSession(UscRouteIdentifier localRouteId) {
        localSessionManager.removeEntry(localRouteId);
        monitor.onEvent(new UscSessionCloseEvent(localRouteId.getIp(), localRouteId.getRemoteChannelType(),
                localRouteId.getSessionId() + ""));
        LOG.info("Remove local session {}", localRouteId);
    }

    /**
     * create a new lcoal session id for remote caller
     *
     * @param remoteRouteId
     *            remote route identifier
     * @return the new session id, the id is descending from max session id
     */
    public int createNewLocalSessionId(UscRouteIdentifier remoteRouteId) {
        Integer maxSessionId = sessionIdMap.get(remoteRouteId);
        if (maxSessionId == null) {
            sessionIdMap.put(remoteRouteId, 1);
            return MAX_FIXED_SESSION_ID;
        } else {
            int sessionId = MAX_FIXED_SESSION_ID - maxSessionId;
            sessionIdMap.put(remoteRouteId, maxSessionId + 1);
            return sessionId;
        }
    }

    /**
     * get the server channel for sending request to remote using the particular
     * route identifier
     *
     * @param localRouteId
     *            local route identifier
     * @return server local channel
     */
    public LocalChannel getRequestSource(UscRouteIdentifier localRouteId) {
        return localSessionManager.getServerChannel(localRouteId);
    }

    /**
     * send local remote request or notice to remote actor
     *
     * @param message
     *            remote message
     */
    public void sendRequest(UscRemoteMessage message) {
        UscRouteIdentifier routeId = message.getRouteIdentifier();
        ActorRef remoteActorRef = getRemoteActorForRequest(routeId);
        if (remoteActorRef != null) {
            remoteActorRef.tell(message, communicator);
            if (message instanceof UscRemoteDataMessage) {
                monitor.onEvent(new UscSessionTransactionEvent(routeId.getIp(), routeId.getRemoteChannelType(), routeId
                        .getSessionId() + "", 0, ((UscRemoteDataMessage) message).getPayload().length));
            }
        } else {
            LOG.error("Failed to send request,since not found any remote actoRef for remote channel:" + routeId);
        }
    }

    /**
     * broad cast message to all managed remote actors,like add channel event
     * message
     *
     * @param message
     *            remote message
     */
    public void broadcastMessage(UscRemoteMessage message) {
        if (remoteActors.size() == 0) {
            // normally will not enter here, since cluster member up event will
            // happen at before
            updateActorListFromCluster();
        }
        if (remoteActors.size() == 0) {
            LOG.warn("Failed to send broadcast message to all remote actor, since currently there is no remote actor!Remote actor list is empty!");
            // TODO broadcast message
            for (ActorRef actorRef : deviceTable.getActorRefList()) {
                if (actorRef.compareTo(communicator) != 0) {
                    actorRef.tell(message, communicator);
                }
            }
        } else {
            LOG.trace("Start to send broadcast message to " + remoteActors.size() + " remote acotrs.");
            for (ActorSelection actorSelection : remoteActors) {
                actorSelection.tell(message, communicator);
            }
        }
    }

    /**
     * send local USC channel response to remote session
     *
     * @param localRouteId
     *            local route identifier
     * @param payload
     *            response pay load, no usc header
     */
    public void sendResponse(UscRouteIdentifier localRouteId, byte[] payload) {
        ActorRef remoteActor = getRemoteActorForResponse(localRouteId);
        if (remoteActor != null) {
            UscRouteIdentifier remoteRouteId = getRemoteRouteIdentifier(localRouteId);
            // frame sessionId is local sessionId,can not be used in remote
            UscRemoteDataMessage message = new UscRemoteDataMessage(remoteRouteId, payload, false);
            remoteActor.tell(message, communicator);
        } else {
            LOG.error("Not found the remote actor for routeIdentifier (" + localRouteId + ")");
        }
    }

    /**
     * send local USC channel exception response to remote session
     *
     * @param localRouteId
     *            local route identifier
     * @param exception
     *            exception of agent channel
     */
    public void sendException(UscRouteIdentifier localRouteId, UscException exception) {
        ActorRef remoteActor = getRemoteActorForResponse(localRouteId);
        if (remoteActor != null) {
            UscRouteIdentifier remoteRouteId = getRemoteRouteIdentifier(localRouteId);
            // frame sessionId is local sessionId,can not be used in remote
            UscRemoteExceptionMessage message = new UscRemoteExceptionMessage(remoteRouteId, exception);
            remoteActor.tell(message, communicator);
        } else {
            LOG.error("Not found the remote actor for routeIdentifier (" + localRouteId + ")");
        }
    }

    /**
     * process remote request for remote caller(sender)
     *
     * @param message
     *            request content
     * @param sender
     *            request caller
     */
    public void processRequest(UscRemoteDataMessage message, ActorRef sender) {
        UscRouteIdentifier remoteRouteId = message.getRouteIdentifier();
        // find response remote session
        UscRouteIdentifier localRouteId = remoteSessionManager.getLocalRouteIdentifier(remoteRouteId);
        io.netty.channel.Channel agentChannel = null;
        if (localRouteId == null) {

            // first time for this route id
            UscChannelImpl localUscChannel = getLocalUscChannel(remoteRouteId);
            if (localUscChannel == null) {
                // since local USC Channel is not exist,send error response
                // directly
                UscRemoteExceptionMessage responseMessage = new UscRemoteExceptionMessage(remoteRouteId,
                        new UscChannelException("Remote channel is not existed in here!"));
                sender.tell(responseMessage, communicator);
                return;
            }

            // add new remote session for first time
            UscRouteIdentifierData routeData = new UscRouteIdentifierData(sender, remoteRouteId,
                    createNewLocalSessionId(remoteRouteId), localUscChannel.getChannel());
            remoteSessionManager.addEntry(routeData);
            LOG.info("Added remote session for " + routeData);
            localRouteId = routeData.getLocalRouteIdentifier();
            agentChannel = localUscChannel.getChannel();
        } else {
            LOG.trace("Find used channel, send request to agent directly.");
            // for next time request from same remote route id
            agentChannel = remoteSessionManager.getAgentChannel(localRouteId);
        }

        // change remote session id to local session id

        UscData data = new UscData(message.getRouteIdentifier().getApplicationPort(), localRouteId.getSessionId(),
                Unpooled.copiedBuffer(message.getPayload()));
        agentChannel.writeAndFlush(data);
        return;

    }

    /**
     * process response which getting from remote controller
     *
     * @param message
     *            response content
     */
    public void processResponse(UscRemoteMessage message) {
        if (message instanceof UscRemoteDataMessage) {
            LOG.info("get response from remote channel, for " + message.getRouteIdentifier());
            UscRemoteDataMessage temp = (UscRemoteDataMessage) message;
            UscRouteIdentifier localRouteId = message.getRouteIdentifier();
            LocalChannel serverChannel = getRequestSource(localRouteId);
            if (serverChannel != null) {
                LOG.trace("Write response to serverChannel(" + serverChannel.hashCode() + "), content "
                        + new String(temp.getPayload()));
                serverChannel.writeAndFlush(Unpooled.copiedBuffer(temp.getPayload()));
                monitor.onEvent(new UscSessionTransactionEvent(localRouteId.getIp(), localRouteId
                        .getRemoteChannelType(), localRouteId.getSessionId() + "", temp.getPayload().length, 0));
            } else {
                LOG.error("Failed to find the server channel for routeIdentifier({}), can't process response({})!",
                        temp.getRouteIdentifier(), message);
            }
        } else {
            LOG.warn("The message type is different, it can't be processed.message type is {}", message.getClass());
        }
    }

    /**
     * process remote exception message
     *
     * @param message
     */
    public void processException(UscRemoteExceptionMessage message) {
        UscException ex = message.getException();
        LocalChannel serverChannel = getRequestSource(message.getRouteIdentifier());
        serverChannel.writeAndFlush(ex);
        UscRouteIdentifier routeId = message.getRouteIdentifier();
        if (ex instanceof UscSessionException) {
            UscSessionException tmp = (UscSessionException) ex;
            monitor.onEvent(new UscSessionErrorEvent(routeId.getIp(), routeId.getRemoteChannelType(), routeId
                    .getSessionId() + "", tmp.getErrorCode().getCode(), UscErrorLevel.ERROR, tmp.getMessage()));
        } else {
            LOG.warn("Unmonitored error event: error is {}, remote identifier is {}.", ex, message.getRouteIdentifier());
        }
    }

    /**
     * get local usc channel through the remote channel identifier
     *
     * @param remoteChannel
     *            remote channel identifier
     * @return local corresponding usc channel
     */
    private UscChannelImpl getLocalUscChannel(UscRemoteChannelIdentifier remoteChannel) {
        UscConnectionManager connectionManager = connectionManagerMap.get(remoteChannel.getChannelType().name());
        if (connectionManager == null) {
            LOG.info("Current connection manager list is " + connectionManagerMap + ",size is "
                    + connectionManagerMap.size());
            LOG.error("Failed to get the connection manager for channel type(" + remoteChannel.getChannelType()
                    + "),so UscRemoteChannel(" + remoteChannel + ") is not found in local!");
            return null;
        }
        try {
            return connectionManager.getConnection(new UscDevice(remoteChannel.getInetAddress()),
                    remoteChannel.getChannelType());
        } catch (Exception e) {
            LOG.error("UscRemoteChannel(" + remoteChannel + ") is not found in local!error = " + e.getMessage());
            return null;
        }
    }

    /**
     * set connection manager for each channel type
     *
     * @param type
     *            channel type
     * @param connectionManager
     *            connection manager
     */
    public void setConnetionManager(ChannelType type, UscConnectionManager connectionManager) {
        connectionManagerMap.put(type.name(), connectionManager);
    }

    /**
     * destroy broker service
     */
    public void destroy() {
        if (actorSystem != null) {
            actorSystem.terminate();
        }
    }

}
