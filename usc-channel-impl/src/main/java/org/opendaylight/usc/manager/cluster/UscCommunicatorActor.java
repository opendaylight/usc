/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster;

import org.opendaylight.controller.cluster.common.actor.AbstractUntypedActor;
import org.opendaylight.usc.manager.UscRouteBrokerService;
import org.opendaylight.usc.manager.cluster.message.UscRemoteChannelEventMessage;
import org.opendaylight.usc.manager.cluster.message.UscRemoteDataMessage;
import org.opendaylight.usc.manager.cluster.message.UscRemoteExceptionMessage;
import org.opendaylight.usc.manager.cluster.message.UscRemoteMessage;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.japi.Creator;

/**
 * the communicator among cluster based on the actor of Akka Cluster, mainly
 * handle the received message and using broker service
 * 
 */
public class UscCommunicatorActor extends AbstractUntypedActor {
    private static final Logger LOG = LoggerFactory.getLogger(UscCommunicatorActor.class);
    private UscRouteBrokerService brokerService = null;
    Cluster cluster = Cluster.get(getContext().system());

    private UscCommunicatorActor() {
    }

    /**
     * Props for create local actor
     * 
     * @return
     */
    public static Props props() {
        return Props.create(new UscCommunicatorActorCreator());
    }

    @Override
    public void preStart() {
        // subscribe to cluster changes
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), MemberEvent.class, UnreachableMember.class);
    }

    @Override
    public void postStop() {
        // unsubscribe when stop
        cluster.unsubscribe(getSelf());
    }

    @Override
    protected void handleReceive(Object message) throws Exception {
        LOG.info("handleReceive received message:" + message);
        if (brokerService == null) {
            brokerService = UscServiceUtils.getService(UscRouteBrokerService.class);
        }
        boolean handled = handleBrokerMessage(message);
        if (!handled) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unhandled message [{}]", message);
            }
            unhandled(message);
        }
    }

    private boolean handleBrokerMessage(Object message) {
        if (brokerService == null) {
            LOG.warn("Since broker service is null, the message is not handled by broker service!");
            return false;
        }
        if (message instanceof MemberUp) {
            if (!brokerService.clusterMemberUp(((MemberUp) message).member())) {
                LOG.warn("Member is Up: {},failed to add to remote actor list.", ((MemberUp) message).member());
            }
        } else if (message instanceof UnreachableMember) {
            if (!brokerService.clusterMemberDown(((UnreachableMember) message).member())) {
                LOG.warn("Member is unreachable: {},failed to remove from remote actor list.",
                        ((UnreachableMember) message).member());
            }

        } else if (message instanceof MemberRemoved) {
            if (!brokerService.clusterMemberDown(((MemberRemoved) message).member())) {
                LOG.warn("Member is removed: {},failed to remove from remote actor list.",
                        ((MemberRemoved) message).member());
            }
        } else if (message instanceof MemberEvent) {
            // ignore
        } else if (message instanceof UscRemoteMessage) {
            if (message instanceof UscRemoteChannelEventMessage) {
                UscRemoteChannelEventMessage channelEvent = (UscRemoteChannelEventMessage) message;
                LOG.trace("Receive add channel message for adding channel("
                        + channelEvent.getRouteIdentifier().getInetAddress());
                if (channelEvent.isCreate()) {
                    brokerService.addMountedDevice(channelEvent.getRouteIdentifier(), this.sender());
                } else if (channelEvent.isClose()) {
                    brokerService.removeMountedDevice(channelEvent.getRouteIdentifier(), sender());
                } else {
                    LOG.warn("Unhandled channel event [{}]", channelEvent);
                }
            } else if (message instanceof UscRemoteDataMessage) {
                UscRemoteDataMessage temp = (UscRemoteDataMessage) message;
                if (temp.isRequest()) {
                    // process request message, send message to remote
                    // controller
                    brokerService.processRequest(temp, sender());
                } else {
                    // process response message for routed local session
                    brokerService.processResponse(temp);
                }
            } else if (message instanceof UscRemoteExceptionMessage) {
                brokerService.processException((UscRemoteExceptionMessage) message);
            }
        }
        return true;
    }

    private static class UscCommunicatorActorCreator implements Creator<UscCommunicatorActor> {
        private static final long serialVersionUID = 1L;

        @Override
        public UscCommunicatorActor create() throws Exception {
            return new UscCommunicatorActor();
        }
    }
}
