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

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.UscRoot;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.LinkId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.LinkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.topology.NodeKey;
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
    public static InstanceIdentifier<UscRoot> getUscTopologyIdentifier() {
        return InstanceIdentifier.builder(UscRoot.class).build();
    }

    /**
     * get the topology identifier
     * 
     * @param topologyId
     *            topology id
     * @return topology identifier
     */
    public static InstanceIdentifier<Topology> getTopologyIdentifier(String topologyId) {
        return InstanceIdentifier.builder(UscRoot.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId))).build();

    }

    /**
     * get the link identifier belongs to a particular topology
     * 
     * @param topologyId
     *            topology id
     * @param linkId
     *            link id
     * @return link identifier
     */
    public static InstanceIdentifier<Link> getLinkIdentifier(String topologyId, String linkId) {
        return InstanceIdentifier.builder(UscRoot.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                .child(Link.class, new LinkKey(new LinkId(linkId))).build();
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
        return InstanceIdentifier.builder(UscRoot.class)
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
        // because each topology only has one controller related USC Link and
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
     * merge the link list of two topology, the first topology will contains the
     * merging result
     * 
     * @param topoA
     *            the A topology
     * @param topoB
     *            the B topology
     * @return topology merged
     */
    public static Topology mergeLinkList(Topology topoA, Topology topoB) {
        List<Link> list = topoA.getLink();
        LOG.debug("Before Merging, topoA link number:" + list.size());
        LOG.debug("Before Merging, topoB link number:" + topoB.getLink().size());
        for (Link link : topoB.getLink()) {
            list.add(link);
        }
        LOG.debug("After Merging, topoA link number:" + list.size());
        return topoA;
    }

}
