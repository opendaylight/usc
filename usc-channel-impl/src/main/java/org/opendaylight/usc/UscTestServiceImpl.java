package org.opendaylight.usc;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import java.util.Hashtable;
import java.util.concurrent.Future;

import org.opendaylight.usc.manager.UscManagerService;
import org.opendaylight.usc.plugin.UscPlugin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.AddChannelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.AddChannelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.AddChannelOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.NetconfRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.NetconfRequestOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.NetconfRequestOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.RemoveChannelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.RemoveChannelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.RemoveChannelOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.UscTestService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UscTestServiceImpl implements UscTestService {
    private static final Logger LOG = LoggerFactory
            .getLogger(UscTestServiceImpl.class);
    public static final AttributeKey<String> CLIENT_KEY = AttributeKey
            .valueOf("client_key");
    private Hashtable<String, Channel> connectList = new Hashtable<String, Channel>();
    private Hashtable<String, EventLoopGroup> groupList = new Hashtable<String, EventLoopGroup>();
    private UscPlugin plugin = UscManagerService.getInstance().getPluginTcp();
    private static ByteBuf payload = Unpooled.buffer(10000);

    @Override
    public Future<RpcResult<AddChannelOutput>> addChannel(AddChannelInput input) {
        String hostname = input.getChannel().getHostname();
        int port = input.getChannel().getPort();
        AddChannelOutputBuilder builder = new AddChannelOutputBuilder();
        String result = connectNetconfDevice(hostname, port, input.getChannel()
                .isRemote());
        builder.setResult(result);
        outputConnectList();
        return RpcResultBuilder.success(builder.build()).buildFuture();
    }

    @Override
    public Future<RpcResult<RemoveChannelOutput>> removeChannel(
            RemoveChannelInput input) {
        String hostname = input.getChannel().getHostname();
        int port = input.getChannel().getPort();
        Channel clientChannel = connectList.get(hostname + ":" + port);
        EventLoopGroup group = groupList.get(hostname + ":" + port);
        String result = "";
        LOG.info("connectList number is " + connectList.size());
        outputConnectList();
        if (clientChannel == null) {
            result = "Failed to remove channel(" + hostname + ":" + port + ")!";
        } else {
            // plugin.closeAgentInternalConnection(clientChannel);
            closeConnect(group);
            result = "Succeed to remove device(" + hostname + ":" + port + ")!";
        }
        RemoveChannelOutputBuilder builder = new RemoveChannelOutputBuilder();
        builder.setResult(result);
        return RpcResultBuilder.success(builder.build()).buildFuture();
    }

    private void outputConnectList() {
        LOG.info("ConnectList is " + connectList);
    }

    private String connectNetconfDevice(String hostname, int port, boolean remote) {
        Bootstrap clientBootStrap = getNewBootstrap();
        InetSocketAddress address = new InetSocketAddress(hostname, port);
        try {
            Channel clientChannel = plugin
                    .connect(clientBootStrap, address, remote).sync().channel();
            clientChannel.attr(CLIENT_KEY).set(hostname + ":" + port);
            connectList.put(hostname + ":" + port, clientChannel);
            groupList.put(hostname + ":" + port, clientBootStrap.group());
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "Failed to Connect device(" + hostname + ":" + port
                    + ")!error is " + e.getMessage();
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

    private void closeConnect(EventLoopGroup localGroup) {
        localGroup.shutdownGracefully();

        // allow some time for all ports to close;
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public Future<RpcResult<NetconfRequestOutput>> netconfRequest(
            NetconfRequestInput input) {
        String hostname = input.getChannel().getHostname();
        int port = input.getChannel().getPort();
        Channel clientChannel = connectList.get(hostname + ":" + port);
        String result = "";
        outputConnectList();
        payload.writeBytes(input.getChannel().getContent().getBytes());
        if (clientChannel == null) {
            result = "Failed to send request to device(" + hostname + ":"
                    + port + "), since it is not found!";
        } else {
            clientChannel.writeAndFlush(payload);
            result = "Succeed to send request to device(" + hostname + ":"
                    + port + "),content is " + input.getChannel().getContent();
        }
        NetconfRequestOutputBuilder builder = new NetconfRequestOutputBuilder();
        builder.setResult(result);
        return RpcResultBuilder.success(builder.build()).buildFuture();
    }

}
