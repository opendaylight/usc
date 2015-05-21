/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A map has a list value by same key
 *
 * @param <K>
 *            hash map key type
 * @param <E>
 *            hash map element type of list value
 */
public class UscListTable<K, E> {
    protected ConcurrentHashMap<K, List<E>> table = new ConcurrentHashMap<K, List<E>>();

    /**
     * add one element entry
     * 
     * @param key
     *            key value
     * @param element
     *            one element of list value
     */
    public void addEntry(K key, E element) {
        if (table.containsKey(key)) {
            List<E> elementList = table.get(key);
            if (!elementList.contains(element)) {
                elementList.add(element);
            }
        } else {
            List<E> elementList = new CopyOnWriteArrayList<E>();
            elementList.add(element);
            table.put(key, elementList);
        }
    }

    /**
     * remove one element by the key
     * 
     * @param key
     *            key value
     * @param element
     *            removed element
     * @return true for it is found , and succeed to remove it,false for others
     */
    public boolean removeEntry(K key, E element) {
        if (table.containsKey(key)) {
            List<E> elementList = table.get(key);
            if (elementList.contains(element)) {
                elementList.remove(element);
                return true;
            }
        }
        return false;
    }

    /**
     * get first element by the key
     * 
     * @param key
     *            key value
     * @return the first element if it is found
     */
    public E getFirstElement(K key) {
        if (table.containsKey(key)) {
            return table.get(key).get(0);
        }
        return null;
    }

    /**
     * get the value of the key
     * 
     * @param key
     *            key value
     * @return the list of value
     */
    public List<E> get(K key) {
        return table.get(key);
    }
    
    public void clear(){
        table.clear();
    }
}
