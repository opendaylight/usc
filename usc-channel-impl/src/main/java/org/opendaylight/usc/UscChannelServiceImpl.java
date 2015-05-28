/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.usc.manager.UscManagerService;
import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.manager.api.UscShardService;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.usc.plugin.UscPlugin;
import org.opendaylight.usc.util.UscServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.AddChannelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.DeleteChannelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.UscChannelService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.ViewChannelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.ViewChannelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.ViewChannelOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.view.channel.output.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.view.channel.output.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.view.channel.output.TopologyKey;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.Futures;

/**
 * Implementation of the YANG RPCs defined in module usc. Service provides rpc for viewing the usc topology.
 */
public class UscChannelServiceImpl implements UscChannelService {
    private static final Logger LOG = LoggerFactory.getLogger(UscChannelServiceImpl.class);
    @SuppressWarnings("rawtypes")
    private UscShardService shardService;
    private UscTopologyService topoService;
    public static final AttributeKey<String> CLIENT_KEY = AttributeKey.valueOf("client_key");
    private Hashtable<String, Channel> connectList = new Hashtable<String, Channel>();
    private Hashtable<String, EventLoopGroup> groupList = new Hashtable<String, EventLoopGroup>();
    private UscPlugin plugin = UscManagerService.getInstance().getPluginTcp();

    /**
     * Create a UscService and initialize the Shard Service
     */
    public UscChannelServiceImpl() {
        shardService = UscServiceUtils.getService(UscShardService.class);
        if (shardService == null) {
            LOG.error("Failed to get UscShardService!");
        }
        topoService = UscServiceUtils.getService(UscTopologyService.class);
    }

    /**
     * Implements rpc call for viewing the usc topology.
     */

    @Override
    public Future<RpcResult<Void>> addChannel(AddChannelInput input) {
        String hostname = input.getChannel().getDestination().getDestNode().getValue();
        int port = Integer.parseInt(input.getChannel().getSession().get(0).getTerminationPoint()
                .getTerminationPointId().getValue());
        boolean isCallhome = UscTopologyFactory.isCallHome(input.getChannel().getCallHome());
        String result = connectNetconfDevice(hostname, port, isCallhome);
        LOG.info("Add Channel result is " + result);
        return null;
    }

    private String connectNetconfDevice(String hostname, int port, boolean remote) {
        Bootstrap clientBootStrap = getNewBootstrap();
        InetSocketAddress address = new InetSocketAddress(hostname, port);
        try {
            Channel clientChannel = plugin.connect(clientBootStrap, address, remote).sync().channel();
            clientChannel.attr(CLIENT_KEY).set(hostname + ":" + port);
            connectList.put(hostname + ":" + port, clientChannel);
            groupList.put(hostname + ":" + port, clientBootStrap.group());
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "Failed to Connect device(" + hostname + ":" + port + ")!error is " + e.getMessage();
        }
        return "Succeed to connect device(" + hostname + ":" + port + ")!";
    }

    private Bootstrap getNewBootstrap() {
        Bootstrap ret = new Bootstrap();
        EventLoopGroup localGroup = new LocalEventLoopGroup();

        // set up client bootstrap
        ret.group(localGroup);
        ret.channel(LocalChannel.class);
        ret.handler(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new LoggingHandler("Manager Test 1", LogLevel.TRACE));
            }
        });
        return ret;
    }

    @Override
    public Future<RpcResult<Void>> deleteChannel(DeleteChannelInput input) {
        String channelId = input.getChannelId().getValue();
        LOG.info("Failed to delete channel, since don't has enough info!channelId is " + channelId);
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<RpcResult<ViewChannelOutput>> viewChannel(ViewChannelInput input) {
        if (topoService == null || shardService == null) {
            LOG.error("USC Topology Service is not initialized, currently can't process this rpc request.");
            return Futures.immediateFuture(RpcResultBuilder.<ViewChannelOutput> failed()
                    .withError(ErrorType.RPC, "Internal error,please see the log for details.").build());
        }

        // Build Output
        // there only one whole topology
        org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.usc.topology.Topology topo = topoService
                .getWholeUscTopology();
        if (topo == null) {
            return Futures.immediateFuture(RpcResultBuilder.<ViewChannelOutput> failed()
                    .withError(ErrorType.RPC, "Internal error,please see the log for details.").build());
        }
        ViewChannelOutputBuilder outputBuilder = new ViewChannelOutputBuilder();
        TopologyBuilder topoBuilder = new TopologyBuilder();
        TopologyKey topoKey = new TopologyKey(topo.getTopologyId());
        Topology outputTopo = topoBuilder.setChannel(topo.getChannel()).setKey(topoKey).setNode(topo.getNode())
                .setTopologyId(topo.getTopologyId()).build();
        List<Topology> outputTopologyList = new ArrayList<Topology>();
        outputTopologyList.add(outputTopo);
        outputBuilder.setTopology(outputTopologyList);
        ViewChannelOutput output = outputBuilder.build();
        // Return Results
        return RpcResultBuilder.success(output).buildFuture();
    }
}
