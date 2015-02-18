/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.LinkId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.TopologyId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.output.UscTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.LinkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.LinkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * Implementation of the YANG RPCs defined in module usc.
 * Service provides rpc for viewing the usc topology.
 */
public class UscServiceImpl implements UscService {


	/**
	 * Implements rpc call for viewing the usc topology.
	 */
	@Override
	public Future<RpcResult<UscTopologyOutput>> uscTopology(UscTopologyInput input) {
		
		// Build Nodes
		Node applicationNode = buildNode("application:1", "application");
		Node controllerNode = buildNode("controller:1", "controller");
		Node firstDeviceNode = buildNode("device:1.1", "device");
		Node secondDeviceNode = buildNode("device:1.2", "device");
		
		// Build Links
		Link applicationControllerLink = buildLink(applicationNode, controllerNode, "link:1.1", "rpc", 0L, 0L, 0L, "");
		Link controllerFirstDeviceLink = buildLink(controllerNode, firstDeviceNode, "link:1.2", "tcp", 0L, 0L, 0L, "");
		Link controllerSecondDeviceLink = buildLink(controllerNode, secondDeviceNode, "link:1.3", "tcp", 0L, 0L, 0L, "");
		
		// Build Topology
		Topology topology = buildTopology("usc:1", new Node[] { applicationNode, controllerNode, firstDeviceNode, secondDeviceNode }, new Link[] { applicationControllerLink, controllerFirstDeviceLink, controllerSecondDeviceLink });
		
		// Build Output
		List<Topology> topologies = new ArrayList<Topology>();
		topologies.add(topology);
		UscTopologyBuilder uscTopologyBuilder = new UscTopologyBuilder();
		uscTopologyBuilder.setTopology(topologies);
		UscTopologyOutputBuilder uscTopologyOutputBuilder = new UscTopologyOutputBuilder();
		uscTopologyOutputBuilder.setUscTopology(uscTopologyBuilder.build());
		
		// Return Results
		return RpcResultBuilder.success(uscTopologyOutputBuilder.build()).buildFuture();
		
	}
	
        /**
         * Builds a link for the provided source, destination, and id.
	 * Passes link attributes to LinkBuilder to build a link.
         */
	private Link buildLink(Node source, Node destination, String id, String type, long sessions, long bytesIn, long bytesOut, String callHome) {
		LinkId linkId = new LinkId(id);
		LinkKey linkKey = new LinkKey(linkId);
		SourceBuilder sourceBuilder = new SourceBuilder();
		sourceBuilder.setSourceNode(source.getNodeId());
		DestinationBuilder destinationBuilder = new DestinationBuilder();
		destinationBuilder.setDestNode(destination.getNodeId());
		LinkBuilder linkBuilder = new LinkBuilder();
		linkBuilder.setLinkId(linkId);
		linkBuilder.setKey(linkKey);
		linkBuilder.setLinkType(type);
		linkBuilder.setSource(sourceBuilder.build());
		linkBuilder.setDestination(destinationBuilder.build());
		linkBuilder.setSessions(sessions);
		linkBuilder.setBytesIn(bytesIn);
		linkBuilder.setBytesOut(bytesOut);
		linkBuilder.setCallHome(callHome);
		return linkBuilder.build();
	}
	
        /**
         * Builds a node for the provided id.
         * Passes node attributes to NodeBuilder to build a node.
         */
	private Node buildNode(String id, String type) {
		NodeId nodeId = new NodeId(id);
		NodeKey nodeKey = new NodeKey(nodeId);
		NodeBuilder nodeBuilder = new NodeBuilder();
		nodeBuilder.setNodeId(nodeId);
		nodeBuilder.setKey(nodeKey);
		nodeBuilder.setNodeType(type);
		return nodeBuilder.build();
	}
	
        /**
         * Builds a topology for the provided id, nodes and links.
         * Passes topology attributes to TopologyBuilder to build a topology.
         */
	private Topology buildTopology(String id, Node[] nodes, Link[] links) {
		TopologyId topologyId = new TopologyId(id); 
		TopologyKey topologyKey = new TopologyKey(topologyId); 
		List<Node> nodeList = new ArrayList<Node>();
		for (int i = 0; i < nodes.length; i++) nodeList.add(nodes[i]);
		List<Link> linkList = new ArrayList<Link>();
		for (int i = 0; i < links.length; i++) linkList.add(links[i]);
		TopologyBuilder topologyBuilder = new TopologyBuilder();
		topologyBuilder.setTopologyId(topologyId);
		topologyBuilder.setKey(topologyKey);
		topologyBuilder.setNode(nodeList);
		topologyBuilder.setLink(linkList);
		return topologyBuilder.build();
	}

}
