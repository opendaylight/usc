/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.client.netconf;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.usc.plugin.UscPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

@ThreadSafe
final class ProtocolSessionPromise<S> extends DefaultPromise<S> {
    private final Logger LOG = LoggerFactory.getLogger(ProtocolSessionPromise.class);
    private final UscPlugin plugin;
    private final ReconnectStrategy strategy;
    private final InetSocketAddress address;
    private final Bootstrap b;

    @GuardedBy("this")
    private Future<?> pending;

    ProtocolSessionPromise(UscPlugin plugin, final EventExecutor executor, final InetSocketAddress address,
            final ReconnectStrategy strategy, final Bootstrap b) {
        super(executor);
        this.plugin = plugin;
        this.strategy = Preconditions.checkNotNull(strategy);
        this.address = Preconditions.checkNotNull(address);
        this.b = Preconditions.checkNotNull(b);
    }

    synchronized void connect() {
        final Object lock = this;

        try {
            final int timeout = this.strategy.getConnectTimeout();

            LOG.debug("Promise {} attempting connect for {}ms", lock, timeout);

            this.b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout);

            LOG.warn("About to connect");

            // final ChannelFuture connectFuture = this.b.connect(this.address);

            final ChannelFuture connectFuture = plugin.connect(b, this.address);
            LOG.warn("Connect finished");
            // Add listener that attempts reconnect by invoking this method again.
            connectFuture.addListener(new BootstrapConnectListener(lock));
            this.pending = connectFuture;
        } catch (final Exception e) {
            LOG.info("Failed to connect to {}", address, e);
            setFailure(e);
        }
    }

    @Override
    public synchronized boolean cancel(final boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            this.pending.cancel(mayInterruptIfRunning);
            return true;
        }

        return false;
    }

    @Override
    public synchronized Promise<S> setSuccess(final S result) {
        LOG.debug("Promise {} completed", this);
        this.strategy.reconnectSuccessful();
        return super.setSuccess(result);
    }

    private class BootstrapConnectListener implements ChannelFutureListener {
        private final Object lock;

        public BootstrapConnectListener(final Object lock) {
            this.lock = lock;
        }

        @Override
        public void operationComplete(final ChannelFuture cf) throws Exception {
            synchronized (lock) {

                LOG.debug("Promise {} connection resolved", lock);

                // Triggered when a connection attempt is resolved.
                Preconditions.checkState(ProtocolSessionPromise.this.pending.equals(cf));

                /*
                 * The promise we gave out could have been cancelled, which cascades to the connect getting cancelled,
                 * but there is a slight race window, where the connect is already resolved, but the listener has not
                 * yet been notified -- cancellation at that point won't stop the notification arriving, so we have to
                 * close the race here.
                 */
                if (isCancelled()) {
                    if (cf.isSuccess()) {
                        LOG.debug("Closing channel for cancelled promise {}", lock);
                        cf.channel().close();
                    }
                    return;
                }

                if (cf.isSuccess()) {
                    LOG.debug("Promise {} connection successful", lock);
                    return;
                }

                LOG.debug("Attempt to connect to {} failed", ProtocolSessionPromise.this.address, cf.cause());

                final Future<Void> rf = ProtocolSessionPromise.this.strategy.scheduleReconnect(cf.cause());
                rf.addListener(new ReconnectingStrategyListener());
                ProtocolSessionPromise.this.pending = rf;
            }
        }

        private class ReconnectingStrategyListener implements FutureListener<Void> {
            @Override
            public void operationComplete(final Future<Void> sf) {
                synchronized (lock) {
                    // Triggered when a connection attempt is to be made.
                    Preconditions.checkState(ProtocolSessionPromise.this.pending.equals(sf));

                    /*
                     * The promise we gave out could have been cancelled, which cascades to the reconnect attempt
                     * getting cancelled, but there is a slight race window, where the reconnect attempt is already
                     * enqueued, but the listener has not yet been notified -- if cancellation happens at that point, we
                     * need to catch it here.
                     */
                    if (!isCancelled()) {
                        if (sf.isSuccess()) {
                            connect();
                        } else {
                            setFailure(sf.cause());
                        }
                    }
                }
            }
        }

    }

}
