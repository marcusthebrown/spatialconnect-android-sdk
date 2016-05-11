/*
 * Copyright 2016 Boundless, http://boundlessgeo.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License
 */
package com.boundlessgeo.spatialconnect.test;

import com.boundlessgeo.spatialconnect.db.SCKVPStore;
import com.boundlessgeo.spatialconnect.db.SCStoreConfigRepository;
import com.boundlessgeo.spatialconnect.SpatialConnect;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import rx.observers.TestSubscriber;

import static junit.framework.Assert.assertEquals;

public class SCConfigServiceTest extends BaseTestCase {

    private SpatialConnect sc;
    private final static String HAITI_GPKG_ID = "a5d93796-5026-46f7-a2ff-e5dec85heh6b";
    private final static String WHITEHORSE_GPKG_ID = "ba293796-5026-46f7-a2ff-e5dec85heh6b";

    @Before
    public void beforeTest() throws Exception {
        testContext.deleteDatabase("Haiti");
        testContext.deleteDatabase("Whitehorse");
        testContext.deleteDatabase(SCKVPStore.DATABASE_NAME);
    }

    @After
    public void afterTest() throws Exception {
        testContext.deleteDatabase("Haiti");
        testContext.deleteDatabase("Whitehorse");
        testContext.deleteDatabase(SCKVPStore.DATABASE_NAME);
    }

    @Test
    public void testSpatialConnectCanLoadNonDefaultConfigs() {
        sc = new SpatialConnect(testContext);
        sc.addConfig(testConfigFile);
        sc.startAllServices();
        waitForStoreToStart(WHITEHORSE_GPKG_ID);
        assertEquals("The test config file has 3 stores.", 3, sc.getDataService().getAllStores().size());
    }

    @Test
    public void testConfigServicePersistsConfigs() {
        sc = new SpatialConnect(testContext);
        sc.startAllServices();
        sc.loadDefaultConfigs();
        // the remote config doesn't have the whitehorse gpkg so we wait for haiti
        waitForStoreToStart(HAITI_GPKG_ID);
        SCStoreConfigRepository stores = new SCStoreConfigRepository(testContext);
        assertEquals("Config service should have persisted 2 stores (from the remote location).",
                2, stores.getNumberOfStores());
    }


    // TODO: test erroneous config files

    private void waitForStoreToStart(final String storeId) {
        TestSubscriber testSubscriber = new TestSubscriber();
        sc.getDataService().storeStarted(storeId).timeout(1, TimeUnit.MINUTES).subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
    }
}
