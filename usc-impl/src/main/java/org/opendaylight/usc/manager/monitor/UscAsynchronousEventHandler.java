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

import org.opendaylight.usc.manager.UscConfigurationManager;
import org.opendaylight.usc.manager.api.UscConfiguration;
import org.opendaylight.usc.manager.api.UscEvent;

/**
 * Asynchronous event handler tool class, it make the event processing process
 * becomes not synchronized
 */
public class UscAsynchronousEventHandler {

    private final static ExecutorService executorService;
    static {
        executorService = Executors.newFixedThreadPool(UscConfigurationManager.getInstance().getConfigIntValue(
                UscConfiguration.USC_MAX_THREAD_NUMBER));
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
    public static void asynchronizedhandle(final UscEvent event, final UscEventHandler handler) {
        if (event != null && handler != null) {
            executorService.execute(new Runnable() {
                public void run() {
                    handler.handle(event);
                }
            });
        }
    }

    /**
     * finalize the thread pool service
     */
    public static void closeExecutorService() {
        executorService.shutdown();
    }

}
