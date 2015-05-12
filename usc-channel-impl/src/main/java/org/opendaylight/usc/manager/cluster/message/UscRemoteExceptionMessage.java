/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster.message;

import org.opendaylight.usc.manager.cluster.UscRouteIdentifier;
import org.opendaylight.usc.plugin.exception.UscException;

@SuppressWarnings("serial")
public class UscRemoteExceptionMessage extends UscRemoteMessage {
    private UscException exception;

    public UscRemoteExceptionMessage(UscRouteIdentifier routeIdentifier,
            UscException exception) {
        super(routeIdentifier);
        this.exception = exception;
    }

    public UscException getException() {
        return exception;
    }

    @Override
    public String toString() {
        return super.toString() + ",request is " + exception;
    }
}
