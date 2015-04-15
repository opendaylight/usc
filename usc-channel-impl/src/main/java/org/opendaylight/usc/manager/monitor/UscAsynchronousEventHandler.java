/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opendaylight.usc.manager.UscConfigurationServiceImpl;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous event handler tool class, it make the event processing process
 * becomes not synchronized
 */
public class UscAsynchronousEventHandler {
    private static final Logger LOG = LoggerFactory
            .getLogger(UscAsynchronousEventHandler.class);
    private final static ExecutorService executorService;
    static {
        executorService = Executors
                .newFixedThreadPool(UscConfigurationServiceImpl.getInstance()
                        .getConfigIntValue(
                                UscConfigurationService.USC_MAX_THREAD_NUMBER));
    }

    /**
     * static method,can make the event processing process becomes not
     * synchronized
     * 
     * @param event
     *            the event will be handled
     * @param handler
     *            the handler which will process the event
     */
    public static void asynchronizedhandle(final UscEvent event,
            final UscEventHandler handler) {
        if (event != null && handler != null) {
            executorService.execute(new Runnable() {
                public void run() {
                    LOG.trace("handle event[" + event.getClass().getName()
                            + "] using handler[" + handler.getClass().getName()
                            + "].");
                    handler.handle(event);
                }
            });
        } else {

        }
    }

    /**
     * finalize the thread pool service
     */
    public static void closeExecutorService() {
        executorService.shutdown();
    }

}
