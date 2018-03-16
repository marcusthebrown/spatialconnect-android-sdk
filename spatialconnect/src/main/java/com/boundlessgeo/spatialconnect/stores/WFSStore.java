/**
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
package com.boundlessgeo.spatialconnect.stores;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.boundlessgeo.spatialconnect.config.SCStoreConfig;
import com.boundlessgeo.spatialconnect.geometries.SCGeometryCollection;
import com.boundlessgeo.spatialconnect.geometries.SCGeometryFactory;
import com.boundlessgeo.spatialconnect.geometries.SCSpatialFeature;
import com.boundlessgeo.spatialconnect.query.SCQueryFilter;
import com.boundlessgeo.spatialconnect.scutilities.HttpHandler;
import com.boundlessgeo.spatialconnect.style.SCStyle;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Response;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

/**
 * Provides capabilities to interact with a server implementing
 * <a href="http://www.opengeospatial.org/standards/wfs">WFS</a>.  The config must specify a uri property to the
 * endpoint of the WFS server, for example, http://efc-dev.boundlessgeo.com:8080/geoserver/ows.
 * <p></p>
 * Note that all features will be queried and created in a workspace named <i>spatialconnect</i>.
 */
public class WFSStore extends SCRemoteDataStore implements ISCSpatialStore {

    private static final String LOG_TAG = WFSStore.class.getSimpleName();
    private String baseUrl;
    private List<String> vectorLayers = new ArrayList<>();
    private List<String> defaultLayers;
    public static final String TYPE = "wfs";
    private static final String VERSION = "1.1.0";

    public WFSStore(Context context, SCStoreConfig scStoreConfig) {
        this(context, scStoreConfig, null);
    }

    public WFSStore(Context context, SCStoreConfig scStoreConfig, SCStyle style) {
        super(context, scStoreConfig);
        this.setName(scStoreConfig.getName());
        this.setType(TYPE);
        this.setVersion(scStoreConfig.getVersion());
        this.defaultLayers = scStoreConfig.getDefaultLayers();
        baseUrl = scStoreConfig.getUri();
        if (baseUrl == null) {
            throw new IllegalArgumentException("WFS store must have a uri property.");
        }
        getLayers();
        this.style = style;
    }

    public List<String> layers() {
        return this.vectorLayers();
    }

    public List<String> vectorLayers() {
        return vectorLayers;
    }

    public String getGetCapabilitiesUrl() {
        return String.format(
                "%s?service=WFS&version=%s&request=GetCapabilities",
                baseUrl,
                this.getVersion()
        );
    }

    public static String getVersionKey() {
        return String.format("%s.%s",TYPE, VERSION);
    }

    @Override
    public Observable<SCSpatialFeature> query(SCQueryFilter scFilter) {
        // if there are no layer names supplied in the query filter, then search only on the default layers
        final List<String> layerNames = scFilter.getLayerIds().size() > 0 ?
                scFilter.getLayerIds() :
                defaultLayers;

        // TODO: when implementing version 2.0.0, "maxFeatures" has been changed to "count"
        // see: http://docs.geoserver.org/latest/en/user/services/wfs/reference.html#getfeature
        String getFeatureUrl = String.format(Locale.US, "%s?service=WFS&version=%s&request=GetFeature&typeName=%s" +
                        "&outputFormat=application/json&srsname=EPSG:4326&maxFeatures=%d",
                baseUrl,
                getVersion(),
                TextUtils.join(",", layerNames),
                scFilter.getLimit()
        );
        if (scFilter.getPredicate() != null) {
            getFeatureUrl = String.format(Locale.US, "%s&bbox=%f,%f,%f,%f,EPSG:4326",
                    getFeatureUrl,
                    scFilter.getPredicate().getBoundingBox().getMinX(),
                    scFilter.getPredicate().getBoundingBox().getMinY(),
                    scFilter.getPredicate().getBoundingBox().getMaxX(),
                    scFilter.getPredicate().getBoundingBox().getMaxY()
            );
        }
        final String featureUrl = getFeatureUrl;
        return Observable.create(new Observable.OnSubscribe<SCSpatialFeature>() {
            @Override public void call(final Subscriber<? super SCSpatialFeature> subscriber) {
                final SCGeometryFactory factory = new SCGeometryFactory();
                HttpHandler.getInstance().get(featureUrl).subscribe(new Action1<Response>() {
                    @Override public void call(Response res) {
                        try {
                            String response = res.body().string();
                            SCGeometryCollection collection =
                                factory.getGeometryCollectionFromFeatureCollectionJson(response);
                            for (SCSpatialFeature feature : collection.getFeatures()) {
                                feature.setLayerId(feature.getId()
                                    .split(
                                        "\\.")[0]);  // the first part of the id is the layer name
                                feature.setStoreId(getStoreId());
                                subscriber.onNext(feature);
                            }
                            subscriber.onCompleted();
                        } catch (IOException ioe) {
                            subscriber.onError(ioe);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override public void call(Throwable throwable) {
                        Log.e(LOG_TAG,
                            "something went wrong querying wfs: " + throwable.getMessage());
                    }
                });
            }
        });
    }

    @Override
    public Observable queryById(SCKeyTuple keyTuple) {
        return null;
    }

    @Override
    public Observable create(SCSpatialFeature scSpatialFeature) {
        return null;
    }

    @Override
    public Observable update(SCSpatialFeature scSpatialFeature) {
        return null;
    }

    @Override
    public Observable delete(SCKeyTuple keyTuple) {
        return null;
    }

    @Override
    public void stop() {
        super.stop();
        this.vectorLayers.clear();
    }

    private List<String> getLayerNames(InputStream response) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(response, null);
            parser.nextTag();
            return parseLayerNames(parser);
        }
        catch (XmlPullParserException | IOException ex) {
            Log.e(LOG_TAG, "Could not parse xml and get layer names.", ex);
        }
        finally {
            try {
                response.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private List<String> parseLayerNames(XmlPullParser parser) throws XmlPullParserException, IOException {
        List entries = new ArrayList();
        parser.require(XmlPullParser.START_TAG, null, "wfs:WFS_Capabilities");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("FeatureTypeList")) {
                parser.require(XmlPullParser.START_TAG, null, "FeatureTypeList");
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    name = parser.getName();
                    if (name.equals("FeatureType")) {
                        entries.add(readFeatureType(parser));
                    }
                    else {
                        skip(parser);
                    }
                }
            }
            else {
                skip(parser);
            }
        }
        return entries;
    }

    private String readFeatureType(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, "FeatureType");
        String layerName = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("Title")) {
                layerName = readLayerName(parser);
            }
            else {
                skip(parser);
            }
        }
        return layerName;
    }

    private String readLayerName(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, "Title");
        String title = readText(parser);
        parser.require(XmlPullParser.END_TAG, null, "Title");
        return title;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    private void getLayers() {
        HttpHandler.getInstance().get(getGetCapabilitiesUrl()).subscribe(new Action1<Response>() {
            @Override public void call(Response response) {
                vectorLayers = getLayerNames(response.body().byteStream());
                if (vectorLayers != null) {
                    setStatus(SCDataStoreStatus.SC_DATA_STORE_RUNNING);
                }
            }
        }, new Action1<Throwable>() {
            @Override public void call(Throwable t) {
                setStatus(SCDataStoreStatus.SC_DATA_STORE_START_FAILED);
            }
        });
    }
}