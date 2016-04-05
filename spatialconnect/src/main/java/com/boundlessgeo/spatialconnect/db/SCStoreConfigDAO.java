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

package com.boundlessgeo.spatialconnect.db;

import android.content.Context;

import com.boundlessgeo.spatialconnect.config.SCStoreConfig;

public class SCStoreConfigDAO {

    private KeyValueDAO keyValueDAO;

    public SCStoreConfigDAO(Context context) {
        keyValueDAO = new KeyValueDAO(context);
    }

    public void addStore(SCStoreConfig store) {
        String storePrefix = "stores." + store.getUniqueID() + ".";
        keyValueDAO.put(storePrefix + "id", store.getUniqueID());
        keyValueDAO.put(storePrefix + "type", store.getType());
        keyValueDAO.put(storePrefix + "version", store.getVersion());
        keyValueDAO.put(storePrefix + "name", store.getName());
        keyValueDAO.put(storePrefix + "uri", store.getUri());
    }

    public int getNumberOfStores() {
        return keyValueDAO.getValuesForKeyPrefix("stores.%.id").size();
    }
}
