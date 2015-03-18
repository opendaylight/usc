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

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.usc.manager.api.UscShardAccess;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.usc.util.UscDtoUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.UscRoot;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.output.UscTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.topology.Node;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * Implementation of the YANG RPCs defined in module usc. Service provides rpc
 * for viewing the usc topology.
 */
public class UscServiceImpl implements UscService {
    private UscShardAccess shardManager;

    /**
     * UscServiceImpl Constructor
     * 
     * @param shardManager
     *            Shard Data Access implementation class for read USC topology
     *            infomation
     */
    public UscServiceImpl(UscShardAccess shardManager) {
        this.shardManager = shardManager;
    }

    /**
     * Implements rpc call for viewing the usc topology.
     */
    @Override
    public Future<RpcResult<UscTopologyOutput>> uscTopology(UscTopologyInput input) {
        UscRoot uscTopology = (UscRoot) shardManager.read(LogicalDatastoreType.OPERATIONAL,
                UscDtoUtils.getUscTopologyIdentifier());
        List<Topology> topologies = new ArrayList<Topology>();
        String id = null;
        Topology topology = null;
        Topology wholeUscTopology = UscTopologyFactory.createTopology("usc", new ArrayList<Node>(),
                new ArrayList<Link>());
        for (Topology topo : uscTopology.getTopology()) {
            id = topo.getTopologyId().getValue();
            topology = (Topology) shardManager.read(LogicalDatastoreType.OPERATIONAL,
                    UscDtoUtils.getTopologyIdentifier(id));
            if (topology != null) {
                UscDtoUtils.mergeLinkList(UscDtoUtils.mergeNodeList(wholeUscTopology, topology), topology);
            }
        }

        // Build Output
        // there only one whole topology
        topologies.add(wholeUscTopology);
        UscTopologyBuilder uscTopologyBuilder = new UscTopologyBuilder();
        uscTopologyBuilder.setTopology(topologies);
        UscTopologyOutputBuilder uscTopologyOutputBuilder = new UscTopologyOutputBuilder();
        uscTopologyOutputBuilder.setUscTopology(uscTopologyBuilder.build());

        // Return Results
        return RpcResultBuilder.success(uscTopologyOutputBuilder.build()).buildFuture();

    }
}
