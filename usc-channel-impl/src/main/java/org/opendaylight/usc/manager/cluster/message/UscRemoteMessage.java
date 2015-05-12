/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster.message;

import java.io.Serializable;

import org.opendaylight.usc.manager.cluster.UscRouteIdentifier;

@SuppressWarnings("serial")
public class UscRemoteMessage implements Serializable {
    protected UscRouteIdentifier routeIdentifier;

    public UscRemoteMessage(UscRouteIdentifier routeIdentifier) {
        super();
        this.routeIdentifier = routeIdentifier;
    }

    public UscRouteIdentifier getRouteIdentifier() {
        return routeIdentifier;
    }
    
    @Override
    public String toString(){
        return routeIdentifier.toString();
    }
}
