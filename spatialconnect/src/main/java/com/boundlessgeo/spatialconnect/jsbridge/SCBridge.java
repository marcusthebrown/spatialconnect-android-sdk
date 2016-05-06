package com.boundlessgeo.spatialconnect.jsbridge;

import android.location.Location;
import android.support.annotation.Nullable;
import android.util.Log;

import com.boundlessgeo.spatialconnect.geometries.SCBoundingBox;
import com.boundlessgeo.spatialconnect.geometries.SCGeometry;
import com.boundlessgeo.spatialconnect.geometries.SCGeometryFactory;
import com.boundlessgeo.spatialconnect.geometries.SCSpatialFeature;
import com.boundlessgeo.spatialconnect.query.SCGeometryPredicateComparison;
import com.boundlessgeo.spatialconnect.query.SCPredicate;
import com.boundlessgeo.spatialconnect.query.SCQueryFilter;
import com.boundlessgeo.spatialconnect.services.SCSensorService;
import com.boundlessgeo.spatialconnect.services.SCServiceManager;
import com.boundlessgeo.spatialconnect.stores.SCDataStore;
import com.boundlessgeo.spatialconnect.stores.SCKeyTuple;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.UnsupportedEncodingException;
import java.util.List;

import rx.Subscriber;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * This module handles messages sent from Javascript.
 */
public class SCBridge extends ReactContextBaseJavaModule {

    private final String LOG_TAG = SCBridge.class.getSimpleName();
    private SCServiceManager manager;
    private ReactContext reactContext;

    public SCBridge(ReactApplicationContext reactContext) {
        super(reactContext);
        // TODO: this should be a singleton with a getInstance() method or injected with dagger
        this.manager = new SCServiceManager(reactContext.getApplicationContext());
        this.manager.startAllServices();
        this.manager.loadDefaultConfigs();
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "SCBridge";
    }

    /**
     * Sends an event to Javascript.
     *
     * @param eventName the name of the event
     * @param params    a map of the key/value pairs associated with the event
     * @see <a href="https://facebook.github.io/react-native/docs/native-modules-android
     * .html#sending-events-to-javascript"> https://facebook.github.io/react-native/docs/native-modules-android
     * .html#sending-events-to-javascript</a>
     */
    public void sendEvent(String eventName, @Nullable WritableMap params) {
        Log.v(LOG_TAG, String.format("Sending event %s to Javascript: %s", eventName, params.toString()));
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    /**
     * Sends an event to Javascript.
     *
     * @param eventName     the name of the event
     * @param payloadString a payload string associated with the event
     * @see <a href="https://facebook.github.io/react-native/docs/native-modules-android
     * .html#sending-events-to-javascript"> https://facebook.github.io/react-native/docs/native-modules-android
     * .html#sending-events-to-javascript</a>
     */
    public void sendEvent(String eventName, @Nullable String payloadString) {
        Log.v(LOG_TAG, String.format("Sending event %s to Javascript: %s", eventName, payloadString));
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, payloadString);
    }

    /**
     * Handles a message sent from Javascript.  Expects the message envelope to look like:
     * <code>{"action":<integer>,"payload":<JSON Object>}</code>
     *
     * @param message
     */
    @ReactMethod
    public void handler(ReadableMap message) {
        Log.d(LOG_TAG, "Received message from JS: " + message.toString());
        message = message.getMap("data");

        if (message == null && message.equals("undefined")) {
            Log.w(LOG_TAG, "data message was null or undefined");
            return;
        }
        else {
            // parse bridge message to determine command and action number
            Integer actionNumber = message.getInt("action");
            BridgeCommand command = BridgeCommand.fromActionNumber(actionNumber);
            if (command.equals(BridgeCommand.SENSORSERVICE_GPS)) {
                handleSensorServiceGps(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_ACTIVESTORESLIST)) {
                handleActiveStoresList();
            }
            if (command.equals(BridgeCommand.DATASERVICE_ACTIVESTOREBYID)) {
                handleActiveStoreById(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_GEOSPATIALQUERYALL)
                    || command.equals(BridgeCommand.DATASERVICE_SPATIALQUERYALL)) {
                handleQueryAll(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_UPDATEFEATURE)) {
                handleUpdateFeature(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_DELETEFEATURE)) {
                handleDeleteFeature(message);
            }
            if (command.equals(BridgeCommand.DATASERVICE_CREATEFEATURE)) {
                handleCreateFeature(message);
            }
        }
    }

    /**
     * Handles all the {@link BridgeCommand#SENSORSERVICE_GPS} commands.
     *
     * @param message
     */
    private void handleSensorServiceGps(ReadableMap message) {
        Log.d(LOG_TAG, "Handling SENSORSERVICE_GPS message :" + message.toString());
        SCSensorService sensorService = manager.getSensorService();
        Integer payloadNumber = message.getInt("payload");
        if (payloadNumber == 1) {
            sensorService.startGPSListener();
            sensorService.getLastKnownLocation()
                    .subscribeOn(Schedulers.newThread())
                    .subscribe(new Action1<Location>() {
                        @Override
                        public void call(Location location) {
                            WritableMap params = Arguments.createMap();
                            params.putString("latitude", String.valueOf(location.getLatitude()));
                            params.putString("longitude", String.valueOf(location.getLongitude()));
                            sendEvent("lastKnownLocation", params);
                        }
                    });
        }
        if (payloadNumber == 0) {
            sensorService.disableGPSListener();
        }
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_ACTIVESTORESLIST} command.
     */
    private void handleActiveStoresList() {
        Log.d(LOG_TAG, "Handling DATASERVICE_ACTIVESTORESLIST message");
        List<SCDataStore> stores = manager.getDataService().getActiveStores();
        WritableMap eventPayload = Arguments.createMap();
        WritableArray storesArray = Arguments.createArray();
        for (SCDataStore store : stores) {
            storesArray.pushMap(getStoreMap(store));
        }
        eventPayload.putArray("stores", storesArray);
        sendEvent("storesList", eventPayload);
    }

    /**
     * Handles all the {@link BridgeCommand#DATASERVICE_ACTIVESTOREBYID} commands.
     *
     * @param message
     */
    private void handleActiveStoreById(ReadableMap message) {
        Log.d(LOG_TAG, "Handling ACTIVESTOREBYID message :" + message.toString());
        String storeId = message.getMap("payload").getString("storeId");
        SCDataStore store = manager.getDataService().getStoreById(storeId);
        sendEvent("store", getStoreMap(store));
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_GEOSPATIALQUERYALL} and
     * {@link BridgeCommand#DATASERVICE_SPATIALQUERYALL} commands.
     *
     * @param message
     */
    private void handleQueryAll(ReadableMap message) {
        Log.d(LOG_TAG, "Handling *QUERYALL message :" + message.toString());
        SCQueryFilter filter = getFilter(message);
        if (filter != null) {
            manager.getDataService().queryAllStores(filter)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            new Subscriber<SCSpatialFeature>() {
                                @Override
                                public void onCompleted() {
                                    Log.d(LOG_TAG, "query observable completed");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(LOG_TAG, "onError()\n" + e.getMessage());
                                }

                                @Override
                                public void onNext(SCSpatialFeature feature) {
                                    try {
                                        // base64 encode id and set it before sending across wire
                                        String encodedId = ((SCGeometry) feature).getKey().encodedCompositeKey();
                                        feature.setId(encodedId);
                                        sendEvent("spatialQuery", ((SCGeometry) feature).toJson());
                                    }
                                    catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                    );
        }
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_UPDATEFEATURE} command.
     *
     * @param message
     */
    private void handleUpdateFeature(ReadableMap message) {
        Log.d(LOG_TAG, "Handling UPDATEFEATURE message :" + message.toString());
        try {
            SCSpatialFeature featureToUpdate = getFeatureToUpdate(message.getMap("payload").getString("feature"));
            manager.getDataService().getStoreById(featureToUpdate.getKey().getStoreId())
                    .update(featureToUpdate)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            new Subscriber<SCSpatialFeature>() {
                                @Override
                                public void onCompleted() {
                                    Log.d(LOG_TAG, "update completed");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(LOG_TAG, "onError()\n" + e.getLocalizedMessage());
                                }

                                @Override
                                public void onNext(SCSpatialFeature updated) {
                                    Log.d(LOG_TAG, "feature updated!");
                                    //TODO: send this over some "update" stream
                                }
                            }
                    );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the {@link BridgeCommand#DATASERVICE_DELETEFEATURE} command.
     *
     * @param message
     */
    private void handleDeleteFeature(ReadableMap message) {
        Log.d(LOG_TAG, "Handling DELETEFEATURE message :" + message.toString());
        try {
            SCKeyTuple featureKey = new SCKeyTuple(message.getString("payload"));
            manager.getDataService().getStoreById(featureKey.getStoreId())
                    .delete(featureKey)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            new Subscriber<Boolean>() {
                                @Override
                                public void onCompleted() {
                                    Log.d(LOG_TAG, "delete completed");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(LOG_TAG, "onError()\n" + e.getLocalizedMessage());
                                }

                                @Override
                                public void onNext(Boolean deleted) {
                                    //TODO: send this over some "deleted" stream
                                    Log.d(LOG_TAG, "feature deleted!");
                                }
                            }
                    );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    /**
     * Handles the {@link BridgeCommand#DATASERVICE_CREATEFEATURE} command.
     *
     * @param message
     */
    private void handleCreateFeature(ReadableMap message) {
        Log.d(LOG_TAG, "Handling CREATEFEATURE message :" + message.toString());
        try {
            SCSpatialFeature newFeature = getNewFeature(message.getMap("payload"));
            manager.getDataService().getStoreById(newFeature.getKey().getStoreId())
                    .create(newFeature)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            new Subscriber<SCSpatialFeature>() {
                                @Override
                                public void onCompleted() {
                                    Log.d(LOG_TAG, "create completed");
                                }

                                @Override
                                public void onError(Throwable e) {
                                    e.printStackTrace();
                                    Log.e(LOG_TAG, "onError()\n" + e.getLocalizedMessage());
                                }

                                @Override
                                public void onNext(SCSpatialFeature feature) {
                                    try {
                                        // base64 encode id and set it before sending across wire
                                        String encodedId = ((SCGeometry) feature).getKey().encodedCompositeKey();
                                        feature.setId(encodedId);
                                        sendEvent("createFeature", ((SCGeometry) feature).toJson());
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                    );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns an SCSpatialFeature instance based on the message from the bridge to create a new feature.
     *
     * @param message the message received from the Javascript
     * @return
     * @throws UnsupportedEncodingException
     */
    private SCSpatialFeature getNewFeature(ReadableMap message) throws UnsupportedEncodingException {
        String featureString = message.getString("feature");
        SCSpatialFeature feature = new SCGeometryFactory().getSpatialFeatureFromFeatureJson(featureString);
        feature.setStoreId(message.getString("storeId"));
        feature.setLayerId(message.getString("layerId"));
        return feature;
    }

    /**
     * Returns an SCSpatialFeature instance based on the GeoJSON Feature string sent from the bridge for update.
     *
     * @param featureString the GeoJSON string representing the feature
     * @return
     * @throws UnsupportedEncodingException
     */
    private SCSpatialFeature getFeatureToUpdate(String featureString) throws UnsupportedEncodingException {
        SCSpatialFeature feature = new SCGeometryFactory().getSpatialFeatureFromFeatureJson(featureString);
        SCKeyTuple decodedTuple = new SCKeyTuple(feature.getId());
        // update feature with decoded values
        feature.setStoreId(decodedTuple.getStoreId());
        feature.setLayerId(decodedTuple.getLayerId());
        feature.setId(decodedTuple.getFeatureId());
        return feature;
    }

    // builds a query filter based on the filter in payload
    private SCQueryFilter getFilter(ReadableMap message) {
        ReadableArray extent = message.getMap("payload").getMap("filter").getArray("$geocontains");
        SCBoundingBox bbox = new SCBoundingBox(
                extent.getDouble(0),
                extent.getDouble(1),
                extent.getDouble(2),
                extent.getDouble(3)
        );
        SCQueryFilter filter = new SCQueryFilter(
                new SCPredicate(bbox, SCGeometryPredicateComparison.SCPREDICATE_OPERATOR_WITHIN)
        );
        return filter;
    }

    // creates a WriteableMap of the SCDataStore attributes
    private WritableMap getStoreMap(SCDataStore store) {
        WritableMap params = Arguments.createMap();
        params.putString("storeId", store.getStoreId());
        params.putString("name", store.getName());
        params.putString("type", store.getType());
        params.putInt("version", store.getVersion());
        params.putString("key", store.getKey());
        return params;
    }
}
