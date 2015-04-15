/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.api;

import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import com.google.common.util.concurrent.FutureCallback;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;

/**
 * CLustering shard data access interface
 * 
 * @param <D>
 *            the type of operating data
 * @param <R>
 *            the call back future for write or merge operations
 */
public interface UscShardService<D extends DataObject, R> {

    /**
     * read the Shard data which specified by the InstanceIdentifier(id) with
     * LogicalDatastoreType(type) Note: the return data is the same type(Type D)
     * of InstanceIdentifier
     * 
     * @param type
     *            LogicalDatastoreType.OPERATIONAL(the data is the state
     *            result), LogicalDatastoreType.CONFIGURATION(the data is the
     *            state target)
     * @param id
     *            the data identifier
     * @return the data of the identifier specified
     */
    public D read(LogicalDatastoreType type, InstanceIdentifier<D> id);

    /**
     * 
     * write the Shard data(data) which specified by the InstanceIdentifier(id)
     * with LogicalDatastoreType(type)
     * 
     * @param type
     *            LogicalDatastoreType.OPERATIONAL(the data is the state
     *            result), LogicalDatastoreType.CONFIGURATION(the data is the
     *            state target)
     * @param id
     *            the data identifier
     * @param data
     *            the current writing data
     */
    public void write(LogicalDatastoreType type, InstanceIdentifier<D> id,
            D data);

    /**
     * write the Shard data(data) with callback future
     * 
     * @param type
     *            LogicalDatastoreType.OPERATIONAL(the data is the state
     *            result), LogicalDatastoreType.CONFIGURATION(the data is the
     *            state target)
     * @param id
     *            the data identifier
     * @param data
     *            the current writing data
     * @param callback
     *            callback class which will be called after the merge operation
     *            for dealing with the operation result
     */
    public void write(LogicalDatastoreType type, InstanceIdentifier<D> id,
            D data, FutureCallback<R> callback);

    /**
     * merge the children data with previous exists same data which has same
     * identifier
     * 
     * @param type
     *            LogicalDatastoreType.OPERATIONAL(the data is the state
     *            result), LogicalDatastoreType.CONFIGURATION(the data is the
     *            state target)
     * @param id
     *            the data identifier
     * @param data
     *            the current merging source data
     */
    public void merge(LogicalDatastoreType type, InstanceIdentifier<D> id,
            D data);

    /**
     * merge the children data with callback future
     * 
     * @param type
     *            LogicalDatastoreType.OPERATIONAL(the data is the state
     *            result), LogicalDatastoreType.CONFIGURATION(the data is the
     *            state target)
     * @param id
     *            the data identifier
     * @param data
     *            the current merging source data
     * @param callback
     *            callback class which will be called after the merge operation
     *            for dealing with the operation result
     */
    public void merge(LogicalDatastoreType type, InstanceIdentifier<D> id,
            D data, FutureCallback<R> callback);

    /**
     * delete the data object specified by the id
     * 
     * @param type
     *            LogicalDatastoreType.OPERATIONAL(the data is the state
     *            result), LogicalDatastoreType.CONFIGURATION(the data is the
     *            state target)
     * @param id
     *            specified identifier of data object
     */
    public void delete(LogicalDatastoreType type, InstanceIdentifier<D> id);

}
