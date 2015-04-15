/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.usc.manager.api.UscEvent;
import org.opendaylight.usc.manager.api.UscMonitorListener;
import org.opendaylight.usc.manager.api.UscMonitorTarget;

/**
 * A adapter for monitor target
 */
public class UscMonitorTargetAdapter implements UscMonitorTarget {

    private List<UscMonitorListener> listenerList = new ArrayList<UscMonitorListener>();

    @Override
    public void addMonitorEventListener(UscMonitorListener listener) {
        if (listener != null)
            listenerList.add(listener);
    }

    @Override
    public void removeMonitorEventListener(UscMonitorListener listener) {
        if (listener != null)
            listenerList.remove(listener);

    }

    @Override
    public void sendEvent(UscEvent event) {
        if (event != null) {
            for (UscMonitorListener listener : listenerList) {
                listener.onEvent(event);
            }
        }
    }

}
