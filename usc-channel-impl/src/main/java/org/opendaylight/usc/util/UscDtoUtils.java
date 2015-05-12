/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.util;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.ChannelId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.UscTopology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Channel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.ChannelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.topology.attributes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.TopologyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the utils class for data transfer object
 */
public class UscDtoUtils {

    private static final Logger LOG = LoggerFactory.getLogger(UscDtoUtils.class);

    /**
     * get USC root topology identifier
     * 
     * @return USC root identifier
     */
    public static InstanceIdentifier<UscTopology> getUscTopologyIdentifier() {
        return InstanceIdentifier.builder(UscTopology.class).build();
    }

    /**
     * get the topology identifier
     * 
     * @param topologyId
     *            topology id
     * @return topology identifier
     */
    public static InstanceIdentifier<Topology> getTopologyIdentifier(String topologyId) {
        return InstanceIdentifier.builder(UscTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId))).build();

    }

    /**
     * get the Channel identifier belongs to a particular topology
     * 
     * @param topologyId
     *            topology id
     * @param ChannelId
     *            Channel id
     * @return Channel identifier
     */
    public static InstanceIdentifier<Channel> getChannelIdentifier(String topologyId, String ChannelId) {
        return InstanceIdentifier.builder(UscTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                .child(Channel.class, new ChannelKey(new ChannelId(ChannelId))).build();
    }

    /**
     * get the node identifier belongs to a particular topology
     * 
     * @param topologyId
     *            topology id
     * @param nodeId
     *            node id
     * @return node identifier
     */
    public static InstanceIdentifier<Node> getNodeIdentifier(String topologyId, String nodeId) {
        return InstanceIdentifier.builder(UscTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                .child(Node.class, new NodeKey(new NodeId(nodeId))).build();
    }

    /**
     * merge the node list of two topology, the first topology will contains the
     * merging result
     * 
     * @param topoA
     *            the A topology
     * @param topoB
     *            the B topology
     * @return topology merged
     */
    public static Topology mergeNodeList(Topology topoA, Topology topoB) {
        // because each topology only has one controller related USC Channel and
        // deviceNode,so need filter same node and merge all others
        List<Node> nodeList = topoA.getNode();
        boolean find = false;
        for (Node nodeB : topoB.getNode()) {
            for (Node nodeA : topoA.getNode()) {
                if (nodeA.getKey().getNodeId().getValue().equals(nodeB.getKey().getNodeId().getValue())) {
                    find = true;
                    LOG.debug("Find same node id: " + nodeA.getKey().getNodeId().getValue());
                    break;
                }
            }
            if (!find) {
                // TODO improve performance
                nodeList.add(nodeB);
            } else {
                find = false;
            }
        }
        return topoA;
    }

    /**
     * clone node list form a topology
     * 
     * @param topo
     *            topology
     * @return new node list
     */
    public static List<Node> cloneNodeList(Topology topo) {
        List<Node> nodeList = new ArrayList<Node>();
        for (Node node : topo.getNode()) {
            nodeList.add(node);
        }
        return nodeList;
    }

    /**
     * merge the Channel list of two topology, the first topology will contains the
     * merging result
     * 
     * @param topoA
     *            the A topology
     * @param topoB
     *            the B topology
     * @return topology merged
     */
    public static Topology mergeChannelList(Topology topoA, Topology topoB) {
        List<Channel> list = topoA.getChannel();
        LOG.debug("Before Merging, topoA Channel number:" + list.size());
        LOG.debug("Before Merging, topoB Channel number:" + topoB.getChannel().size());
        for (Channel Channel : topoB.getChannel()) {
            list.add(Channel);
        }
        LOG.debug("After Merging, topoA Channel number:" + list.size());
        return topoA;
    }

}
