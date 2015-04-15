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



import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.manager.api.UscShardService;
import org.opendaylight.usc.util.UscServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscTopologyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.topologies.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.output.UscTopology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.usc.topology.output.UscTopologyBuilder;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the YANG RPCs defined in module usc. Service provides rpc
 * for viewing the usc topology.
 */
public class UscServiceImpl implements UscService {
    private static final Logger LOG = LoggerFactory
            .getLogger(UscServiceImpl.class);
    @SuppressWarnings("rawtypes")
    private UscShardService shardService;
    private UscTopologyService topoService;

    /**
     * Create a UscService and initialize the Shard Service
     */
    public UscServiceImpl() {
        shardService = UscServiceUtils.getService(UscShardService.class);
        if (shardService == null) {
            LOG.error("Failed to get UscShardService!");
        }
        topoService = UscServiceUtils.getService(UscTopologyService.class);
    }

    /**
     * Implements rpc call for viewing the usc topology.
     */

    @SuppressWarnings("unchecked")
    @Override
    public Future<RpcResult<UscTopologyOutput>> uscTopology(
            UscTopologyInput input) {

        if (topoService == null || shardService == null) {
            LOG.error("USC Topology Service is not initialized, currently can't process this rpc request.");
            return (Future<RpcResult<UscTopologyOutput>>) RpcResultBuilder
                    .failed()
                    .withError(ErrorType.RPC,
                            "Internal error,For details please see the log.")
                    .build();
        }
        List<Topology> topologies = new ArrayList<Topology>();

        // Build Output
        // there only one whole topology
        topologies.add(topoService.getWholeUscTopology());
        UscTopologyBuilder uscTopologyBuilder = new UscTopologyBuilder();
        uscTopologyBuilder.setTopology(topologies);
        UscTopologyOutputBuilder uscTopologyOutputBuilder = new UscTopologyOutputBuilder();
        UscTopology uscTopology = uscTopologyBuilder.build();
        uscTopologyOutputBuilder.setUscTopology(uscTopology);
        UscTopologyOutput output = uscTopologyOutputBuilder.build();
        // Return Results
        return RpcResultBuilder.success(output).buildFuture();
    }
}
