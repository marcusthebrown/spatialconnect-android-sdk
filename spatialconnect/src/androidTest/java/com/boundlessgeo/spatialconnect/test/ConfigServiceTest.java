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

import com.boundlessgeo.spatialconnect.db.SCStoreConfigDAO;
import com.boundlessgeo.spatialconnect.services.SCConfigService;
import com.boundlessgeo.spatialconnect.services.SCDataService;
import com.boundlessgeo.spatialconnect.services.SCServiceManager;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import rx.observers.TestSubscriber;

import static junit.framework.Assert.assertEquals;

public class ConfigServiceTest extends BaseTestCase {

    private SCServiceManager manager;

    @Test
    public void testConfigServiceCanLoadConfigsFromExternalStorage() {
        SCConfigService configService = new SCConfigService(testContext);
        configService.loadConfigs(testConfigFile);
        assertEquals("The test config file has 3 stores.", 3, SCDataService.getInstance().getAllStores().size());
    }

    @Test
    public void testConfigServiceLoadsConfigsThroughServiceManager() {
        manager = new SCServiceManager(testContext);
        // the test config packaged with the app has 3 stores.  The remote config has 1 of the same stores plus 1
        // additional store not defined in the test config.
        assertEquals("The test config file has 3 stores plus another distinct store from the remote config",
                4, SCDataService.getInstance().getAllStores().size());
    }

    @Test
    public void testConfigServiceLoadsConfigsThroughServiceManagerWithOptionalConstructor() {
        manager = new SCServiceManager(testContext, testConfigFile);
        assertEquals("It should only have loaded the 3 stores from the config file.",
                3, SCDataService.getInstance().getAllStores().size());
    }

    @Test
    public void testConfigServicePersistsConfigs() {
        manager = new SCServiceManager(testContext);
        SCStoreConfigDAO stores = new SCStoreConfigDAO(testContext);
        assertEquals("Config service should have persisted 4 stores.", 4, stores.getNumberOfStores());
    }

    @Test
    public void testLibGpkgFunctionsLoaded() {
        manager = new SCServiceManager(testContext);
        manager.startAllServices();
//        waitForAllStoresToStart();
//                .executeSql("SELECT ST_AsText(the_geom) FROM point_features LIMIT 1;");
//                .executeSql("SELECT CreateSpatialIndex('point_features', 'the_geom', 'id');");
    }

    private void waitForAllStoresToStart() {
        TestSubscriber testSubscriber = new TestSubscriber();
        manager.getDataService().allStoresStartedObs().timeout(10, TimeUnit.MINUTES).subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
    }

    // TODO: test erroneous config files
}
