/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor;

import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.manager.api.UscEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelErrorEvent;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.usc.util.UscServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Alarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the event handler of all of channel related events
 */
public class UscChannelEventHandler implements UscEventHandler {
    private static final Logger LOG = LoggerFactory
            .getLogger(UscChannelEventHandler.class);
    private UscTopologyService topoService;

    /**
     * create a channel event handler using given topology manager
     */
    public UscChannelEventHandler() {
        topoService = UscServiceUtils.getService(UscTopologyService.class);
        if (topoService == null) {
            LOG.error("Failed to get UscTopologyService!");
        }
    }

    @Override
    public void handle(UscEvent event) {
        if (topoService == null) {
            LOG.error("UscTopologyService is not initialized!");
            return;
        }
        if (event instanceof UscChannelCreateEvent) {
            UscChannelCreateEvent evt = (UscChannelCreateEvent) event;
            Node deviceNode = UscTopologyFactory.createNode(evt.getDeviceId(),
                    UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
            topoService.addNode(deviceNode);
            String key = UscTopologyService.NODE_TYPE_CONTROLLER + ":"
                    + topoService.getLocalController().getNodeId().getValue()
                    + "-" + UscTopologyService.NODE_TYPE_NETWORK_DEVICE + ":"
                    + evt.getDeviceId();
            Link channel = UscTopologyFactory.createLink(
                    topoService.getLocalController(), deviceNode, key,
                    evt.getType(), evt.isCallHome());
            topoService.addLink(channel);
        } else if (event instanceof UscChannelCloseEvent) {
            UscChannelCloseEvent evt = (UscChannelCloseEvent) event;
            topoService.removeLink(evt.getDeviceId());
        } else if (event instanceof UscChannelErrorEvent) {
            UscChannelErrorEvent evt = (UscChannelErrorEvent) event;
            Alarm alarm = UscTopologyFactory.createLinkAlram(evt.getErrorId(),
                    evt.getErrorCode() + "", evt.getMessage());
            topoService.addLinkError(evt.getDeviceId(), alarm);
        }
    }

}
