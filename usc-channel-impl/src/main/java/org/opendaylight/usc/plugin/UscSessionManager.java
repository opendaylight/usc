/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.local.LocalChannel;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.usc.manager.monitor.evt.UscSessionCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCreateEvent;
import org.opendaylight.usc.plugin.model.UscSession;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * This class manages the collection of client sessions that are currently
 * established to this controller node.
 */
public abstract class UscSessionManager {

    private static final Logger log = LoggerFactory.getLogger(UscSessionManager.class);

    private final UscPlugin plugin;

    /**
     * One session manager is created per connection, so the session IDs here
     * are unique
     */
    private final ConcurrentHashMap<Integer, UscSessionImpl> sessions = new ConcurrentHashMap<>();

    protected UscSessionManager(UscPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Add a session (channel) to this session manager.
     * 
     * @param port
     * @param channel
     * @return
     */
    public UscSessionImpl addSession(int sessionId, int port, LocalChannel channel) {
        final UscSessionImpl session = createSession(sessionId, port, channel);
        if (sessions.putIfAbsent(sessionId, session) == null) {
            final int sId = sessionId;
            channel.closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    removeSession(sId);
                    log.trace("serverChannel for session " + sId + " closed");
                }
            });

            plugin.sendEvent(new UscSessionCreateEvent(session));

            return session;
        }
        throw new RuntimeException("out of available session IDs");
    }

    /**
     * Remove all sessions from this manager.
     */
    public void removeAllSessions() {
        for (int sessionId : sessions.keySet()) {
            removeSession(sessionId);
        }
    }

    public UscSession removeSession(int sessionId) {
        UscSessionImpl session = sessions.remove(sessionId);
        if (session != null) {
            plugin.sendEvent(new UscSessionCloseEvent(session));
        }
        return session;
    }

    /**
     * Get the session information corresponding to a session ID.
     * 
     * @param sessionId
     * @return
     */
    public UscSessionImpl getSession(int sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 
     * @return return all sessions
     */
    public Collection<UscSessionImpl> getAllSessions() {
        return sessions.values();
    }

    @VisibleForTesting
    public int getSessionCount() {
        return sessions.size();
    }

    protected abstract UscSessionImpl createSession(int sessionId, int port, LocalChannel channel);

}
