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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.channel.attributes.ChannelAlarm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Channel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the event handler of all of channel related events
 */
public class UscChannelEventHandler implements UscEventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UscChannelEventHandler.class);
    private UscTopologyService topoService;

    /**
     * create a channel event handler using given topology manager
     */
    public UscChannelEventHandler() {

    }

    @Override
    public void handle(UscEvent event) {
        if (topoService == null) {
            topoService = UscServiceUtils.getService(UscTopologyService.class);
            if (topoService == null) {
                LOG.error("Failed to get UscTopologyService!");
                return;
            }
        }
        if (event instanceof UscChannelCreateEvent) {
            UscChannelCreateEvent evt = (UscChannelCreateEvent) event;
            Node deviceNode = UscTopologyFactory.createNode(evt.getDeviceId(),
                    UscTopologyService.NODE_TYPE_NETWORK_DEVICE);
            String key = UscTopologyService.NODE_TYPE_CONTROLLER + ":"
                    + topoService.getLocalController().getNodeId().getValue() + "-"
                    + UscTopologyService.NODE_TYPE_NETWORK_DEVICE + ":" + evt.getDeviceId() + '-'
                    + UscTopologyService.Channel_TYPE + ":" + evt.getType();
            Channel channel = UscTopologyFactory.createChannel(topoService.getLocalController(), deviceNode, key,
                    evt.getType(), evt.isCallHome());
            topoService.addChannel(channel);
        } else if (event instanceof UscChannelCloseEvent) {
            UscChannelCloseEvent evt = (UscChannelCloseEvent) event;
            topoService.removeChannel(evt.getDeviceId(), evt.getType());
        } else if (event instanceof UscChannelErrorEvent) {
            UscChannelErrorEvent evt = (UscChannelErrorEvent) event;
            ChannelAlarm alarm = UscTopologyFactory.createChannelAlram(evt.getErrorId(), evt.getErrorCode() + "",
                    evt.getMessage());
            topoService.addChannelError(evt.getDeviceId(), evt.getType(), alarm);
        }
    }

}
