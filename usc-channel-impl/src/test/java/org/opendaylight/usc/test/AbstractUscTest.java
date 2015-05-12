/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test;

import org.junit.BeforeClass;
import org.opendaylight.usc.manager.UscConfigurationServiceImpl;

/**
 * Test engine requiring USC framework components such as
 * UscPlugin, UscManager, UscSecureService, etc.
 * Preconditions are executed.
 */
public abstract class AbstractUscTest {
    @BeforeClass
    public static void initializePreconditions() {
        UscConfigurationServiceImpl.setDefaultPropertyFilePath("src/test/resources/etc/usc/usc.properties");
        UscConfigurationServiceImpl.getInstance();
    }
}
