package org.opendaylight.usc.manager.cluster;

import io.netty.channel.Channel;
import akka.actor.ActorRef;

public class UscRouteSessionData {
    private ActorRef actorRef;
    private Channel channel;

    public UscRouteSessionData(Channel channel, ActorRef actorRef) {
        this.actorRef = actorRef;
        this.channel = channel;
    }

    public ActorRef getActorRef() {
        return actorRef;
    }

    public void setActorRef(ActorRef actorRef) {
        this.actorRef = actorRef;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @Override
    public String toString() {
        return "UscRouteSessionData:channel is " + channel + ",actor is " + actorRef.path();
    }
}
