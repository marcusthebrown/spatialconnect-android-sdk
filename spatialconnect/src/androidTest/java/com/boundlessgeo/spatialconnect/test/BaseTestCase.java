/**
 * Copyright 2015-2016 Boundless, http://boundlessgeo.com
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

import android.app.Activity;
import android.content.Context;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.boundlessgeo.spatialconnect.SpatialConnectActivity;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public abstract class BaseTestCase {

    /**
     * Activity
     */
    protected static Activity activity = null;

    /**
     * Test context
     */
    protected static Context testContext = null;

    /**
     * Test context
     */
    protected static File testConfigFile;

    public BaseTestCase() {
    }

    @ClassRule
    public static ActivityTestRule<SpatialConnectActivity> rule = new ActivityTestRule<>(SpatialConnectActivity.class);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // Set the activity and test context
        activity = rule.getActivity();
        testContext = activity.createPackageContext(
                "com.boundlessgeo.spatialconnect.test",
                Context.CONTEXT_IGNORE_SECURITY
        );
        try {
            testConfigFile = File.createTempFile("config.scfg", null, activity.getCacheDir());
            // read test scconfig.json file from test resources directory
            InputStream is = testContext.getResources().openRawResource(R.raw.scconfig);
            FileOutputStream fos = new FileOutputStream(testConfigFile);
            byte[] data = new byte[is.available()];
            is.read(data);
            fos.write(data);
            is.close();
            fos.close();
        } catch (IOException ex) {
            System.exit(0);
        }
    }

}
