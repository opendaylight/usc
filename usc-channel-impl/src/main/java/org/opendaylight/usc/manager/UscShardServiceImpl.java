/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.usc.manager.api.UscShardService;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/**
 * Clustering shard data manager for USC
 */
public class UscShardServiceImpl implements UscShardService<DataObject, Object> {

    private static final Logger LOG = LoggerFactory
            .getLogger(UscShardServiceImpl.class);

    private DataBroker dataProvider;
    private WriteTransaction writeTransaction;
    private ReadTransaction readTransaction;

    private static UscShardServiceImpl shardService = new UscShardServiceImpl();

    private UscShardServiceImpl() {

    }

    /**
     * get the shard service implementation instance
     * 
     * @return shard service instance
     */
    public static UscShardServiceImpl getInstance() {
        return shardService;
    }

    /**
     * Initialize shard data manager using given DataBroker
     * 
     * @param dp
     *            shard data service of opendaylight
     */
    public void init(DataBroker dp) {
        if (dp != null) {
            dataProvider = dp;
        } else {
            LOG.error("Data service is null!");
        }
    }

    @Override
    public DataObject read(LogicalDatastoreType type,
            InstanceIdentifier<DataObject> id) {
        DataObject ret = null;
        readTransaction = dataProvider.newReadOnlyTransaction();
        try {
            ret = readTransaction.read(type, id).checkedGet().get();
        } catch (ReadFailedException e) {
            if (LOG.isDebugEnabled()) {
                e.printStackTrace();
            }
            LOG.error("Failed to read the data from shard data.type is " + type
                    + ", id is " + id + ", exception is " + e.getMessage());
        }
        return ret;
    }

    @Override
    public void write(LogicalDatastoreType type,
            final InstanceIdentifier<DataObject> id, final DataObject data) {
        writeTransaction = dataProvider.newWriteOnlyTransaction();
        writeTransaction.put(type, id, data);

        Futures.addCallback(writeTransaction.submit(),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(final Void result) {
                        LOG.trace("Successfully write [{}]", data.toString());
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        LOG.error(
                                String.format("Failed to write [%s]",
                                        data.toString()), t);
                    }
                });
    }

    @Override
    public void write(LogicalDatastoreType type,
            InstanceIdentifier<DataObject> id, DataObject data,
            FutureCallback<Object> callback) {
        writeTransaction = dataProvider.newWriteOnlyTransaction();
        writeTransaction.put(type, id, data);
        Futures.addCallback(writeTransaction.submit(), callback);
    }

    @Override
    public void merge(LogicalDatastoreType type,
            final InstanceIdentifier<DataObject> id, final DataObject data) {
        writeTransaction = dataProvider.newWriteOnlyTransaction();
        writeTransaction.merge(type, id, data);
        Futures.addCallback(writeTransaction.submit(),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(final Void result) {
                        LOG.trace("Successfully merge [{}]", data.toString());
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        LOG.error(
                                String.format("Failed to merge [%s]",
                                        data.toString()), t);
                    }
                });
    }

    @Override
    public void merge(LogicalDatastoreType type,
            InstanceIdentifier<DataObject> id, DataObject data,
            FutureCallback<Object> callback) {
        writeTransaction = dataProvider.newWriteOnlyTransaction();
        writeTransaction.merge(type, id, data);
        Futures.addCallback(writeTransaction.submit(), callback);
    }

    @Override
    public void delete(LogicalDatastoreType type,
            final InstanceIdentifier<DataObject> id) {
        writeTransaction = dataProvider.newWriteOnlyTransaction();
        writeTransaction.delete(type, id);
        Futures.addCallback(writeTransaction.submit(),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(final Void result) {
                        LOG.trace("Successfully delete [{}]", id.toString());
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        LOG.error(
                                String.format("Failed to delete [%s]",
                                        id.toString()), t);
                    }
                });
    }

}
