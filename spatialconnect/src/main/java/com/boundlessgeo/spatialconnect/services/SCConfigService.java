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

package com.boundlessgeo.spatialconnect.services;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.boundlessgeo.spatialconnect.SpatialConnect;
import com.boundlessgeo.spatialconnect.config.SCConfig;
import com.boundlessgeo.spatialconnect.config.SCLayerConfig;
import com.boundlessgeo.spatialconnect.config.SCRemoteConfig;
import com.boundlessgeo.spatialconnect.config.SCStoreConfig;
import com.boundlessgeo.spatialconnect.scutilities.Json.SCObjectMapper;
import com.boundlessgeo.spatialconnect.scutilities.Storage.SCFileUtilities;
import com.boundlessgeo.spatialconnect.services.authService.ExchangeAuthMethod;
import com.boundlessgeo.spatialconnect.services.authService.NoAuth;
import com.boundlessgeo.spatialconnect.services.authService.SCServerAuthMethod;
import com.boundlessgeo.spatialconnect.stores.FormStore;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.boundlessgeo.spatialconnect.scutilities.Json.SCObjectMapper.getMapper;
import static java.util.Arrays.asList;

/**
 * The SCConfigService is responsible for managing the configuration for SpatialConnect.  This includes downloading
 * remote configuration and sweeping the external storage for config files, if required. The config service is
 * also responsible for parsing the relevant parts of the config and invoking functions on other services using parts
 * of the {@link SCConfig}.
 */
public class SCConfigService extends SCService implements SCServiceLifecycle {

    private static final String LOG_TAG = SCConfigService.class.getSimpleName();
    private static final String SERVICE_NAME = "SC_CONFIG_SERVICE";
    private static final String CONFIGS_DIR = "configs";
    private Context context;
    private List<String> configPaths = new ArrayList<>();
    private SpatialConnect sc;
    private SCDataService dataService;
    private OkHttpClient client;
    private String clientId = "";
    private String clientSecret = "";


    public SCConfigService(Context context) {
        this.context = context;
        sc = SpatialConnect.getInstance();
        this.client = new OkHttpClient.Builder()
            .readTimeout(2, TimeUnit.MINUTES)
            .build();
    }

    /**
     * Add a new config to be loaded into SpatialConnect on Start
     * @param fp Full path to file
     */
    public void addConfigFilePath(String fp) {
        configPaths.add(fp);
    }

    public void addConfigFilepaths(List<String> fps) {
        configPaths.addAll(fps);
    }

    public void sweepDataDirectory() {
        File[] configFiles = SCFileUtilities.findFilesByExtension(context.getFilesDir(), ".scfg");
        if (configFiles.length > 0) {
            for (File file : configFiles) {
                configPaths.add(file.getAbsolutePath());
            }
        }
    }

    /**
     * Load config in a running Config Service
     */
    public void loadConfigs() {
        for (String path: configPaths) {
            File config = new File(path);
            final SCConfig scConfig;
            try {
                scConfig = SCObjectMapper.getMapper().readValue(config, SCConfig.class);
                loadConfig(scConfig);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Load config in a running Config Service
     * @param config {@link SCConfig} Config object to be loaded
     */
    public void loadConfig(SCConfig config) {
        loadLayers(config.getLayers());
        loadDataStores(config.getStores());

        SCRemoteConfig remoteConfig = config.getRemote();
        if (remoteConfig != null) {
            String auth = remoteConfig.getAuth();
            if (!TextUtils.isEmpty(auth) && auth.equals("no-auth")) {
                sc.connectAuth(new NoAuth());
                sc.connectBackend(remoteConfig);
            } else if (!TextUtils.isEmpty(auth) && auth.equals("exchange-auth")) {
                sc.connectAuth(new ExchangeAuthMethod(context, remoteConfig.getHttpUri()));
                // if you are using exchange auth, you don't need mqtt,
                // so you never need to connect a backend
                loadLayersFromExchange(config);
            } else {
                sc.connectAuth(new SCServerAuthMethod(context, remoteConfig.getHttpUri()));
                sc.connectBackend(remoteConfig);
            }
        }
    }

    private void loadLayersFromExchange(SCConfig config) {
        // gets layers from exchange that the user has write permissions for
        // final String theUrl = String.format(Locale.US, "%s/some/endpoint/", exchangeRootUrl);
        final String theUrl = "http://undemo.boundlessgeo.io:8084/layers";
        Request request = new Request.Builder()
            .url(theUrl)
            .addHeader("Authorization", Credentials.basic(clientId, clientSecret))
            .get()
            .build();
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONArray layers = new JSONArray(response.body().string());
                for (int i = 0; i < layers.length(); i++) {
                    SCLayerConfig layerConfig = getMapper().readValue(
                        layers.getJSONObject(i).toString(),
                        SCLayerConfig.class
                    );
                    config.addLayer(layerConfig);
                    SpatialConnect.getInstance().getDataService().getFormStore()
                        .registerFormByConfig(layerConfig);
                }
                // parse list of layers
                // SCBackendService.configReceived.onNext(true);
                SpatialConnect.getInstance().getConfigService().setCachedConfig(config);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add form to SpatialConnect using a Form Config object
     * @param c {@link SCConfig} Config object to be loaded
     */
    public void addForm(SCLayerConfig c) {
        dataService.getFormStore().registerFormByConfig(c);
    }

    /**
     * Remove form from SpatialConnect using a config
     * @param c {@link SCConfig} Config object to be loaded
     */
    public void removeForm(SCLayerConfig c) {
        dataService.getFormStore().unregisterFormByConfig(c);
    }

    /**
     * Add store to SpatialConnect using a Store Config object
     * @param c {@link SCConfig} Config object to be loaded
     */
    public void addStore(SCStoreConfig c) {
        sc.getDataService().registerAndStartStoreByConfig(c);
    }

    /**
     * Remove store from SpatialConnect using a config
     * @param c {@link SCConfig} Config object to be loaded
     */
    public void removeStore(SCStoreConfig c) {
        dataService.unregisterStore(dataService.getStoreByIdentifier(c.getUniqueID()));
    }

    /**
     * This will overwrite the current cached config and will be used to configure the
     * system if the Backend Service is unable to fetch a config from the server.
     * @param config {@link SCConfig} Config object to be loaded
     */
    public void setCachedConfig(SCConfig config) {

        try {
            String configJson = SCObjectMapper.getMapper().writeValueAsString(config);
            sc.getCache().setValue(configJson, "spatialconnect.config.remote.cached");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

    }

    /**
     * Retrieves the last cached config. This is used when the Backend Service is not
     able to fetch a config from the SpatialConnect server
     * @return {@link SCConfig}
     */
    public SCConfig getCachedConfig() {
        SCConfig returnConfig = null;
        try {
            String configJson = sc.getCache().getStringValue("spatialconnect.config.remote.cached");
            if (configJson != null) {
                returnConfig = SCObjectMapper.getMapper().readValue(
                        configJson,
                        SCConfig.class);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return returnConfig;
    }

    @Override
    public boolean start(Map<String, SCService> deps) {
        sweepDataDirectory();
        dataService = (SCDataService) deps.get(SCDataService.serviceId());
        loadConfigs();
        return super.start(deps);
    }

    @Override
    public String getId() {
        return SERVICE_NAME;
    }

    @Override
    public List<String> getRequires() {
        return asList(SCDataService.serviceId());
    }

    /* Registers all the forms specified in each config file */
    private void loadLayers(List<SCLayerConfig> layerConfigs) {
        if (layerConfigs != null) {
            Log.d(LOG_TAG, "Loading "+ layerConfigs.size() +" form configs");
            for (SCLayerConfig layerConfig : layerConfigs) {
                if (layerConfig.getSchema() != null) {
                    Log.d(LOG_TAG, "Creating table for form " + layerConfig.getLayerKey());
                    FormStore store = sc.getDataService().getFormStore();
                    if (store != null) {
                        store.registerFormByConfig(layerConfig);
                    }
                } else {
                    // TODO handle non schema layers
                    //right now do nothing
                }
            }
        }
    }

    /* Registers all the stores specified in each config file */
    private void loadDataStores(List<SCStoreConfig> storeConfigs) {
        if (storeConfigs != null) {
            Log.d(LOG_TAG, "Loading "+ storeConfigs.size() +" store configs");
            for (SCStoreConfig storeConfig : storeConfigs) {
                Log.d(LOG_TAG, "Adding store " + storeConfig.getName() + " to data service.");
                try {
                    dataService.registerAndStartStoreByConfig(storeConfig);
                } catch (Exception ex) {
                    Log.w(LOG_TAG, "Exception adding store to data service ", ex);
                }
            }
        }
    }

    public static String serviceId() {
        return SERVICE_NAME;
    }
}
