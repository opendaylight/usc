/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor;

import org.opendaylight.usc.manager.UscTopologyManager;
import org.opendaylight.usc.manager.api.UscEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelErrorEvent;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Alarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Node;

/**
 * the event handler of all of channel related events
 */
public class UscChannelEventHandler implements UscEventHandler {

    private UscTopologyManager topoManager;

    /**
     * create a channel event handler using given topology manager
     * 
     * @param topoManager
     *            topology manager
     */
    public UscChannelEventHandler(UscTopologyManager topoManager) {
        this.topoManager = topoManager;
    }

    @Override
    public void handle(UscEvent event) {
        if (event instanceof UscChannelCreateEvent) {
            UscChannelCreateEvent evt = (UscChannelCreateEvent) event;
            Node deviceNode = UscTopologyFactory.createNode(evt.getDeviceId(),
                    UscTopologyManager.NODE_TYPE_NETWORK_DEVICE);
            topoManager.addNode(deviceNode);
            String key = UscTopologyManager.NODE_TYPE_CONTROLLER + ":"
                    + topoManager.getLocalController().getNodeId().getValue() + "-"
                    + UscTopologyManager.NODE_TYPE_NETWORK_DEVICE + ":" + evt.getDeviceId();
            Link channel = UscTopologyFactory.createLink(topoManager.getLocalController(), deviceNode, key,
                    evt.getType(), evt.isCallHome());
            topoManager.addLink(channel);
        } else if (event instanceof UscChannelCloseEvent) {
            UscChannelCloseEvent evt = (UscChannelCloseEvent) event;
            topoManager.removeLink(evt.getDeviceId());
        } else if (event instanceof UscChannelErrorEvent) {
            UscChannelErrorEvent evt = (UscChannelErrorEvent) event;
            Alarm alarm = UscTopologyFactory.createLinkAlram(evt.getErrorId(), evt.getErrorCode() + "",
                    evt.getMessage());
            topoManager.addLinkError(evt.getDeviceId(), alarm);
        }
    }

}
