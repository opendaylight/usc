/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.opendaylight.usc.manager.UscRouteBrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;

/**
 * remote channel management table
 */
public class UscDeviceMountTable extends UscListTable<UscChannelIdentifier, ActorRef> {
    private static final Logger LOG = LoggerFactory.getLogger(UscDeviceMountTable.class);
    private static UscDeviceMountTable instance = new UscDeviceMountTable();

    private UscDeviceMountTable() {
        super();
    }

    /**
     * get singleton instance
     * 
     * @return singleton instance
     */
    public static UscDeviceMountTable getInstance() {
        return instance;
    }

    /**
     * get a actor which belongs to a controller which has the connected remote
     * channel
     * 
     * @param remoteChannel
     *            the identifier of remote channel
     * @return the actor(currently return first actor
     */
    public ActorRef getActorRef(UscChannelIdentifier remoteChannel) {
        // TODO improve for not first one is the latest response one
        ActorRef tmp = getFirstElement(remoteChannel);
        if (tmp == null) {
            LOG.error("Failed to get first actorRef for remote channel:" + remoteChannel + ", in device table:" + table);
        }
        return tmp;
    }

    /**
     * check if it exists the remote channel
     * 
     * @param remoteChannel
     * @return
     */
    public boolean existRemoteChannel(UscChannelIdentifier remoteChannel) {
        LOG.trace("Device table:remote device number is " + table.size() + ", content is " + table
                + ",search channel is " + remoteChannel);
        List<ActorRef> actorRefList = table.get(remoteChannel);
        if (actorRefList != null) {
            return true;
        }
        return false;
    }

    /**
     * get actorRef set of all actor which sent adding channel message to local
     * 
     * @return actorRef set
     */
    public List<ActorRef> getActorRefList() {
        Set<ActorRef> refSet = new HashSet<ActorRef>();
        for (Entry<UscChannelIdentifier, List<ActorRef>> entry : table.entrySet()) {
            List<ActorRef> refList = entry.getValue();
            for (ActorRef ref : refList) {
                refSet.add(ref);
            }
        }
        List<ActorRef> retList = new ArrayList<ActorRef>();
        retList.addAll(refSet);
        return retList;
    }

    /**
     * remove entry related with a specified remote channel and actor path
     * 
     * @param remoteChannel
     *            remote channel identifier
     * @param actorPath
     *            a communicator actor path
     */
    public void removeEntry(UscChannelIdentifier remoteChannel, String actorPath) {
        // since the hash code is different,it can not use UscRouteIdentifier as
        // a UscRemoteChannelIdentifier
        UscChannelIdentifier filteredRemoteChannel = new UscChannelIdentifier(remoteChannel.getInetAddress(),
                remoteChannel.getChannelType());
        for (Entry<UscChannelIdentifier, List<ActorRef>> entry : table.entrySet()) {
            if (entry.getKey().equals(filteredRemoteChannel)) {
                List<ActorRef> refList = entry.getValue();
                for (ActorRef ref : refList) {
                    if (UscRouteBrokerService.isSameActorRef(actorPath, ref)) {
                        if (refList.size() == 1) {
                            table.remove(entry.getKey());
                        } else {
                            refList.remove(ref);
                        }
                    }
                }
            }
        }
    }

    /**
     * remove all actor path related device entry
     * 
     * @param actorPath
     *            a communicator actor path
     */
    public void removeAll(String actorPath) {
        for (Entry<UscChannelIdentifier, List<ActorRef>> entry : table.entrySet()) {
            List<ActorRef> refList = entry.getValue();
            for (ActorRef ref : refList) {
                if (ref.path().toString().equals(actorPath)) {
                    if (refList.size() == 1) {
                        table.remove(entry.getKey());
                    } else {
                        refList.remove(ref);
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return table.toString();
    }
}
