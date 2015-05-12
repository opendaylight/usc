package org.opendaylight.usc.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Demultiplexer extends SimpleChannelInboundHandler<Object> {
    private static final Logger LOG = LoggerFactory.getLogger(Demultiplexer.class);

    public Demultiplexer(UscPlugin plugin) {
        
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        LOG.trace("Demultiplexer.channelRead0: " + msg);

        Channel channel = ctx.channel();
        Channel serverChannel = channel.attr(UscPlugin.LOCAL_SERVER_CHANNEL).get();
        ReferenceCountUtil.retain(msg);

        if (msg instanceof DatagramPacket) {
            ByteBuf payload = ((DatagramPacket) msg).content();
            serverChannel.writeAndFlush(payload);
        } else {
            serverChannel.writeAndFlush(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }
}
