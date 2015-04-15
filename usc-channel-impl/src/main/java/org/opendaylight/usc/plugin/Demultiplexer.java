package org.opendaylight.usc.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Demultiplexer extends SimpleChannelInboundHandler<Object>{

	 private static final Logger LOG = LoggerFactory.getLogger(Demultiplexer.class);

	 private final UscPlugin plugin;

	public Demultiplexer(UscPlugin plugin) {
		this.plugin = plugin;
	}
	 
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		LOG.trace("Demultiplexer.channelRead0: " + msg);

		Channel channel = ctx.channel();
		LocalChannel serverChannel = channel.attr(UscPlugin.LOCAL_SERVER_CHANNEL).get();
		
		ChannelPipeline pl = serverChannel.pipeline();
		LOG.trace("Demultiplexer.channelRead0: serverChannel.pipeline is " + pl);
       
		LOG.trace("write data to " + serverChannel + ": " + msg);
		serverChannel.writeAndFlush(msg);
		
		/*String message = "test1\n";
        ByteBuf payload = Unpooled.copiedBuffer(message.getBytes());
        LOG.trace("write data to " + serverChannel + ": " + payload);
        serverChannel.writeAndFlush(payload); */
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		LOG.trace("channelInactive()");
	}

	
}
