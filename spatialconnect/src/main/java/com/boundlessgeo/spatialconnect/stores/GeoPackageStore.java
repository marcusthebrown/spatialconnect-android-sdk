/**
 * Copyright 2015-2017 Boundless, http://boundlessgeo.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License
 */
package com.boundlessgeo.spatialconnect.stores;

import android.content.Context;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

import com.boundlessgeo.spatialconnect.config.SCStoreConfig;
import com.boundlessgeo.spatialconnect.db.GeoPackage;
import com.boundlessgeo.spatialconnect.db.GeoPackageContents;
import com.boundlessgeo.spatialconnect.db.SCGpkgFeatureSource;
import com.boundlessgeo.spatialconnect.db.SCSqliteHelper;
import com.boundlessgeo.spatialconnect.geometries.SCBoundingBox;
import com.boundlessgeo.spatialconnect.geometries.SCGeometry;
import com.boundlessgeo.spatialconnect.geometries.SCPolygon;
import com.boundlessgeo.spatialconnect.geometries.SCSpatialFeature;
import com.boundlessgeo.spatialconnect.query.SCQueryFilter;
import com.boundlessgeo.spatialconnect.style.SCStyle;
import com.boundlessgeo.spatialconnect.tiles.GpkgTileProvider;
import com.boundlessgeo.spatialconnect.tiles.SCGpkgTileSource;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.SqlBrite;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;

import java.util.Arrays;
import org.sqlite.database.SQLException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Provides capabilities for interacting with a single GeoPackage.
 */
public class GeoPackageStore extends SCDataStore implements ISCSpatialStore, SCDataStoreLifeCycle, SCRasterStore, ISyncableStore {

    private static final String LOG_TAG = GeoPackageStore.class.getSimpleName();
    public static final String TYPE = "gpkg";
    private static final String VERSION = "1";
    protected GeoPackage gpkg;
    protected SCStoreConfig scStoreConfig;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    protected static final String NO_IMAGE = "R0lGODdhyACWAOMAAMzMzJaWlsXFxb6+vqOjo5ycnLe3t6qqqrGxsQAAAAAAAAAAAAAAAAAAAAAAAAAAACwAAAAAyACWAAAE/hDISau9OOvNu/9gKI5kaZ5oqq5s675wLM90bd94ru987//AoHBILBqPyKRyyWw6n9CodEqtWq/YrHbL7Xq/4LB4TC6bz+i0es1uu9/wuHxOr9vv+Lx+z+/7/4CBgoOEhYaHiImKi4yNjo+QkZKTlJWWl5iZmpucnZ6foKGio6SlpqeoqaqrrK2ur7CxsrO0tba3TAMFBQO4LAUBAQW+K8DCxCoGu73IzSUCwQECAwQBBAIVCMAFCBrRxwDQwQLKvOHV1xbUwQfYEwIHwO3BBBTawu2BA9HGwcMT1b7Vw/Dt3z563xAIrHCQnzsAAf0F6ybhwDdwgAx8OxDQgASN/sKUBWNmwQDIfwBAThRoMYDHCRYJGAhI8eRMf+4OFrgZgCKgaB4PHqg4EoBQbxgBROtlrJu4ofYm0JMQkJk/mOMkTA10Vas1CcakJrXQ1eu/sF4HWhB3NphYlNsmxOWKsWtZtASTdsVb1mhEu3UDX3RLFyVguITzolQKji/GhgXNvhU7OICgsoflJr7Qd2/isgEPGGAruTTjnSZTXw7c1rJpznobf2Y9GYBjxIsJYQbXstfRDJ1luz6t2TDvosSJSpMw4GXG3TtT+hPpEoPJ6R89B7AaUrnolgWwnUQQEKVOAy199mlonPDfr3m/GeUHFjBhAf0SUh28+P12QOIIgDbcPdwgJV+Arf0jnwTwsHOQT/Hs1BcABObjDAcTXhiCOGppKAJI6nnIwQGiKZSViB2YqB+KHtxjjXMsxijjjDTWaOONOOao44489ujjj0AGKeSQRBZp5JFIJqnkkkw26eSTUEYp5ZRUVmnllVhmqeWWXHbp5ZdghinmmGSW6UsEADs=";

    /**
     * Constructor for GeoPackageStore that initializes the data store adapter
     * based on the scStoreConfig.
     *
     * @param context       instance of the current activity's context
     * @param scStoreConfig instance of the configuration needed to configure the store
     */
    public GeoPackageStore(Context context, SCStoreConfig scStoreConfig) {
        this(context, scStoreConfig, null);
    }

    public GeoPackageStore(Context context, SCStoreConfig scStoreConfig, SCStyle style) {
        super(context, scStoreConfig);
        this.scStoreConfig = scStoreConfig;
        this.setName(scStoreConfig.getName());
        this.setType(TYPE);
        this.setVersion(scStoreConfig.getVersion());
        this.getKey();
        this.style = style;
    }

    public List<String> layers() {
        List<String> allLayers = new ArrayList<>();
        allLayers.addAll(this.vectorLayers());
        allLayers.addAll(this.rasterLayers());
        return allLayers;
    }

    public List<String> vectorLayers() {
        Map<String, SCGpkgFeatureSource> fs = getFeatureSources();
        return new ArrayList<>(fs.keySet());
    }

    public List<String> rasterLayers() {
        Map<String, SCGpkgTileSource> fs = getTileSources();
        return new ArrayList<>(fs.keySet());
    }

    public Set<GeoPackageContents> getGeoPackageContents() {
        return gpkg.getGeoPackageContents();
    }

    public Map<String, SCGpkgFeatureSource> getFeatureSources() {
        if (gpkg != null) {
            return gpkg.getFeatureSources();
        } else {
            return new HashMap<>();
        }
    }

    public Map<String, SCGpkgTileSource> getTileSources() {
        if (gpkg !=null) {
            return gpkg.getTileSources();
        } else {
            return new HashMap<>();
        }
    }

    public void addLayer(String layer, Map<String,String>  fields) {
        gpkg.addFeatureSource(layer, fields);
    }

    public void deleteLayer(String layer) {
        BriteDatabase.Transaction tx = gpkg.newTransaction();
        // first remove from gpkg_geometry_columns
        gpkg.removeFromGpkgGeometryColumns(layer);
        // then remove it from geopackage contents
        gpkg.removeFromGpkgContents(layer);
        // lastly, remove the table itself so there are no FK constraint violations
        gpkg.query("DROP TABLE " + layer);
        tx.markSuccessful();
        tx.end();
        gpkg.refreshFeatureSources();
    }

    public String getFilePath() {
        return getContext().getDatabasePath(scStoreConfig.getUniqueId()).getPath();
    }

    @Override
    public DataStorePermissionEnum getAuthorization() {
        return DataStorePermissionEnum.READ_WRITE;
    }

    @Override
    public Observable<SCSpatialFeature> query(final SCQueryFilter queryFilter) {
        final Map<String, SCGpkgFeatureSource> layers = gpkg.getFeatureSources();

        if (layers.size() > 0) {  // ensure only layers with feature sources are queried
            // if there are no layer names supplied in the query filter, then search on all feature sources
            final List<String> featureTableNames = queryFilter.getLayerIds().size() > 0 ?
                    queryFilter.getLayerIds() :
                    new ArrayList<>(layers.keySet());
            // TODO: decide on what to do if queryLimit division is 0
            final int queryLimit = queryFilter.getLimit() / featureTableNames.size();
            Log.d(LOG_TAG, "querying on feature tables: " + featureTableNames.toString());
            return Observable.from(featureTableNames)
                    .filter(new Func1<String, Boolean>() {
                        @Override
                        public Boolean call(String featureTableName) {
                            return layers.containsKey(featureTableName);
                        }
                    })
                    .flatMap(new Func1<String, Observable<SCSpatialFeature>>() {
                        @Override
                        public Observable<SCSpatialFeature> call(final String featureTableName) {
                            final SCGpkgFeatureSource featureSource = gpkg.getFeatureSourceByName(featureTableName);
                            String sql = String.format(
                                "SELECT %s FROM %s WHERE %s IN (%s) LIMIT %d",
                                getSelectColumnsString(featureSource),
                                featureTableName,
                                featureSource.getPrimaryKeyName(),
                                createRtreeSubQuery(featureSource, queryFilter.getPredicate().getBoundingBox()),
                                queryLimit
                            );
                            return gpkg.createQuery(featureTableName, sql)
                                .flatMap(getFeatureMapper(featureSource))
                                .onBackpressureBuffer(queryFilter.getLimit());
                        }
                    });
        }
        else {
            Log.w(LOG_TAG, "no feature sources to query for gpkg " + this.getName());
            // can't query on geopackages with no features
            return Observable.empty();
        }
    }

    @Override
    public Observable<SCSpatialFeature> queryById(final SCKeyTuple keyTuple) {
        final String tableName = keyTuple.getLayerId();
        final SCGpkgFeatureSource featureSource = gpkg.getFeatureSourceByName(tableName);
        if (featureSource == null) {
            return Observable.error(
                    new SCDataStoreException(
                            SCDataStoreException.ExceptionType.LAYER_NOT_FOUND,
                            String.format("%s was not a valid feature table name.", tableName)
                    )
            );
        }
        else {
            return gpkg.createQuery(
                    tableName,
                    String.format(
                            "SELECT %s FROM %s WHERE %s = %s LIMIT 1",
                            getSelectColumnsString(featureSource),
                            tableName,
                            featureSource.getPrimaryKeyName(),
                            keyTuple.getFeatureId()
                    )
            ).flatMap(getFeatureMapper(featureSource));
        }
    }

    @Override
    public Observable<SCSpatialFeature> create(final SCSpatialFeature scSpatialFeature) {
        final String tableName = scSpatialFeature.getKey().getLayerId();
        final SCGpkgFeatureSource featureSource = gpkg.getFeatureSourceByName(tableName);
        if (featureSource == null) {
            Log.e("Sync", "featureSource null");
            return Observable.error(
                    new SCDataStoreException(
                            SCDataStoreException.ExceptionType.LAYER_NOT_FOUND,
                            String.format("%s was not a valid feature table name.", tableName)
                    )
            );
        }
        else {
            return Observable.create(new Observable.OnSubscribe<SCSpatialFeature>() {
                @Override
                public void call(Subscriber<? super SCSpatialFeature> subscriber) {
                    try {
                        Set<String> columns = featureSource.getColumns().keySet();
                        Map<String, Object> props = new HashMap<>();
                        for (String col : columns) {
                            if (!col.equals(featureSource.getGeomColumnName()) && !col.equals(featureSource.getPrimaryKeyName())) {
                                props.put(col, null);
                            }
                        }
                        String columnNames = featureSource.getColumnNamesForInsert(scSpatialFeature);
                        String columnValues = featureSource.getColumnValuesForInsert(scSpatialFeature);

                        String[] properties = columnNames.split(",");
                        //ensure feature columns + geom column equal the column names otherwise something is wrong
                        if ((props.size() + 1 )!= properties.length) {
                            subscriber.onError(new Throwable("Invalid column names or values"));
                        }

                        gpkg.executeAndTrigger(tableName,
                                String.format("INSERT OR REPLACE INTO %s (%s) VALUES (%s)",
                                        tableName,
                                        columnNames,
                                        columnValues
                                )
                        );
                        // get the id of the last inserted row: https://www.sqlite.org/lang_corefunc.html#last_insert_rowid
                        Cursor cursor = gpkg.query("SELECT last_insert_rowid()");
                        if (cursor != null) {
                            cursor.moveToFirst();  // force query to execute
                            final Integer pk = cursor.getInt(0);
                            scSpatialFeature.setId(String.valueOf(pk));
                            subscriber.onNext(scSpatialFeature);
                            subscriber.onCompleted();
                        }

                        storeEdited.onNext(scSpatialFeature);
                    }
                    catch (SQLException ex) {
                        subscriber.onError(new Throwable("Could not create the the feature.", ex));
                    }
                }
            });
        }
    }

    @Override
    public Observable<SCSpatialFeature> update(final SCSpatialFeature scSpatialFeature) {
        final String tableName = scSpatialFeature.getKey().getLayerId();
        final SCGpkgFeatureSource featureSource = gpkg.getFeatureSourceByName(tableName);
        if (featureSource == null) {
            return Observable.error(
                    new SCDataStoreException(
                            SCDataStoreException.ExceptionType.LAYER_NOT_FOUND,
                            String.format("%s was not a valid feature table name.", tableName)
                    )
            );
        }
        else {
            return Observable.create(new Observable.OnSubscribe<SCSpatialFeature>() {
                @Override
                public void call(Subscriber<? super SCSpatialFeature> subscriber) {
                    try {
                        gpkg.executeAndTrigger(tableName,
                                String.format("UPDATE %s SET %s WHERE %s = %s",
                                        tableName,
                                        featureSource.getUpdateSetClause(scSpatialFeature),
                                        featureSource.getPrimaryKeyName(),
                                        scSpatialFeature.getId()
                                )
                        );
                        subscriber.onNext(scSpatialFeature);
                        subscriber.onCompleted();
                    }
                    catch (SQLException ex) {
                        subscriber.onError(new Throwable("Could not update the feature.", ex));
                    }
                }
            });
        }
    }

    @Override
    public Observable<Void> delete(final SCKeyTuple keyTuple) {
        final String tableName = keyTuple.getLayerId();
        final SCGpkgFeatureSource featureSource = gpkg.getFeatureSourceByName(tableName);
        if (featureSource == null) {
            return Observable.error(
                    new SCDataStoreException(
                            SCDataStoreException.ExceptionType.LAYER_NOT_FOUND,
                            String.format("%s was not a valid feature table name.", tableName)
                    )
            );
        }
        else {
            return Observable.create(new Observable.OnSubscribe<Void>() {
                @Override
                public void call(Subscriber<? super Void> subscriber) {
                    try {
                        gpkg.executeAndTrigger(tableName,
                                String.format("DELETE FROM %s WHERE %s = %s",
                                        tableName,
                                        featureSource.getPrimaryKeyName(),
                                        keyTuple.getFeatureId()
                                )
                        );
                        subscriber.onCompleted();
                    }
                    catch (SQLException ex) {
                        subscriber.onError(new Throwable("Could not delete the feature.", ex));
                    }
                }
            });
        }
    }

    @Override
    public Observable<SCStoreStatusEvent> start() {
        if (this.getStatus().compareTo(SCDataStoreStatus.SC_DATA_STORE_STARTED) == 0 ||
            this.getStatus().compareTo(SCDataStoreStatus.SC_DATA_STORE_RUNNING) == 0) {
            Log.d(LOG_TAG, String.format("GeoPackageStore %s already started", this.getName()));
            return Observable.empty();
        }
        final String storeId = this.getStoreId();
        final GeoPackageStore storeInstance = this;

        Log.d(LOG_TAG, "Starting store " + this.getName());
        storeInstance.setStatus(SCDataStoreStatus.SC_DATA_STORE_STARTED);


        return Observable.create(new Observable.OnSubscribe<SCStoreStatusEvent>() {

            @Override
            public void call(final Subscriber<? super SCStoreStatusEvent> subscriber) {

            // The db name on disk is its store ID to guarantee uniqueness on disk
            if (getContext().getDatabasePath(scStoreConfig.getUniqueId()).exists()) {
                Log.d(LOG_TAG, "GeoPackage " + scStoreConfig.getUniqueId() + " already exists.  Not downloading.");
                // create new GeoPackage for the file that's already on disk
                gpkg = new GeoPackage(getContext(), scStoreConfig.getUniqueId());
                if (gpkg.isValid()) {
                    storeInstance.setStatus(SCDataStoreStatus.SC_DATA_STORE_RUNNING);
                    subscriber.onCompleted();
                }
                else {
                    Log.w(LOG_TAG, "GeoPackage was not valid, "+ gpkg.getName());
                    subscriber.onError(new Throwable("GeoPackage was not valid."));
                }
            }
            else {
                // download geopackage and store it locally
                URL theUrl = null;
                if (scStoreConfig.getUri().startsWith("http")) {
                    try {
                        theUrl = new URL(scStoreConfig.getUri());
                        File dbDirectory = getContext()
                            .getDatabasePath(scStoreConfig.getUniqueId()).getParentFile();
                        if (!dbDirectory.exists()) {
                            // force initialization if database directory doesn't exist
                            dbDirectory.mkdir();
                        }
                        download(theUrl.toString(), getContext().getDatabasePath(scStoreConfig.getUniqueId()))
                                .sample(1, TimeUnit.SECONDS)
                                .subscribe(
                                        new Action1<Float>() {
                                            @Override
                                            public void call(Float progress) {
                                                setDownloadProgress(progress);
                                                if (progress < 1) {
                                                    setStatus(SCDataStoreStatus.SC_DATA_STORE_DOWNLOADING_DATA);
                                                    subscriber.onNext(new SCStoreStatusEvent(SCDataStoreStatus.SC_DATA_STORE_DOWNLOADING_DATA));
                                                } else {
                                                    setStatus(SCDataStoreStatus.SC_DATA_STORE_RUNNING);
                                                    gpkg = new GeoPackage(getContext(), scStoreConfig.getUniqueId());
                                                    if (gpkg.isValid()) {
                                                        subscriber.onCompleted();
                                                    }
                                                    else {
                                                        Log.w(LOG_TAG, "GeoPackage was not valid, " + gpkg.getName());
                                                        subscriber.onError(new Throwable("GeoPackage was not valid."));
                                                    }
                                                }
                                            }
                                        },
                                        new Action1<Throwable>() {
                                            @Override
                                            public void call(Throwable t) {
                                                subscriber.onNext(new SCStoreStatusEvent(SCDataStoreStatus.SC_DATA_STORE_START_FAILED));
                                            }
                                        }
                                );
                    }
                    catch (MalformedURLException e) {
                        Log.e(LOG_TAG, "URL was malformed. Check the syntax: " + theUrl);
                        subscriber.onNext(new SCStoreStatusEvent(SCDataStoreStatus.SC_DATA_STORE_START_FAILED));
                        subscriber.onError(e);
                    }
                }
                else if (scStoreConfig.getUri().startsWith("file")) {
                    gpkg = new GeoPackage(getContext(), scStoreConfig.getUniqueId());
                    if (gpkg.isValid()) {
                        subscriber.onNext(new SCStoreStatusEvent(SCDataStoreStatus.SC_DATA_STORE_RUNNING));
                        subscriber.onCompleted();
                    }
                    else {
                        Log.w(LOG_TAG, "GeoPackage was not valid, "+ gpkg.getName());
                        subscriber.onNext(new SCStoreStatusEvent(SCDataStoreStatus.SC_DATA_STORE_START_FAILED));
                        subscriber.onError(new Throwable("GeoPackage was not valid."));
                    }
                }
            }
            }
        });


    }

    @Override
    public void stop() {
        this.setStatus(SCDataStoreStatus.SC_DATA_STORE_STOPPED);
    }

    @Override
    public void resume() {

    }

    @Override
    public void pause() {

    }

    @Override
    public void destroy() {
        deleteFile(getFilePath());
    }

    @Override
    public TileOverlay overlayFromLayer(String layer, GoogleMap map) {
        Map<String, SCGpkgTileSource> tileSources = getTileSources();
        if (tileSources.size() > 0 && tileSources.keySet().contains(layer)) {
            return map.addTileOverlay(
                    new TileOverlayOptions().tileProvider(
                            new GpkgTileProvider(tileSources.get(layer))
                    )
            );
        }
        return null;
    }

    @Override
    public SCPolygon getCoverage() {
        return null;
    }

    @Override
    public Observable<SCSpatialFeature> unSent() {
        return gpkg.unSent().map(new Func1<SCSpatialFeature, SCSpatialFeature>() {
            @Override
            public SCSpatialFeature call(SCSpatialFeature feature) {
                feature.setStoreId(storeId);
                return feature;
            }
        });
    }

    @Override
    public void updateAuditTable(SCSpatialFeature scSpatialFeature) {
        SCGpkgFeatureSource fs = gpkg.getFeatureSourceByName(scSpatialFeature.getLayerId());
        fs.updateAuditTable(scSpatialFeature);
    }

    @Override
    public String syncChannel() {
        return String.format(Locale.US, "/store/%s", this.storeId);
    }

    @Override
    public Map<String, Object> generateSendPayload(SCSpatialFeature scSpatialFeature) {
        return null;
    }

    private String createRtreeSubQuery(SCGpkgFeatureSource source, SCBoundingBox bbox) {
        return String.format("SELECT id FROM rtree_%s_%s WHERE minx > %f AND maxx < %f AND miny > %f AND maxy < %f",
                source.getTableName(),
                source.getGeomColumnName(),
                bbox.getMinX(),
                bbox.getMaxX(),
                bbox.getMinY(),
                bbox.getMaxY()
        );
    }

    private String getSelectColumnsString(SCGpkgFeatureSource featureSource) {
        StringBuilder sb = new StringBuilder();
        for (String columnName : featureSource.getColumns().keySet()) {
            sb.append(columnName).append(",");
        }
        sb.append(featureSource.getPrimaryKeyName()).append(",");
        String geomColumnName = featureSource.getGeomColumnName();
        sb.append("ST_AsBinary(").append(geomColumnName).append(") AS ").append(geomColumnName);
        return sb.toString();

    }

    private void saveFileToFilesystem(InputStream is) throws IOException {
        File dbFile = getContext().getDatabasePath(scStoreConfig.getUniqueId());
        FileOutputStream fos = new FileOutputStream(dbFile);
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len = is.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
        Log.d(LOG_TAG, "Saved file to " + dbFile.getPath());
        Log.d(LOG_TAG, "Size of file in bytes " + dbFile.length());
    }

    private Func1<SqlBrite.Query, Observable<SCSpatialFeature>> getFeatureMapper(final SCGpkgFeatureSource source) {
        return new Func1<SqlBrite.Query, Observable<SCSpatialFeature>>() {

            @Override
            public Observable<SCSpatialFeature> call(SqlBrite.Query query) {
                return query.asRows(new Func1<Cursor, SCSpatialFeature>() {
                    @Override
                    public SCSpatialFeature call(final Cursor cursor) {
                        String rowId = SCSqliteHelper.getString(cursor, source.getPrimaryKeyName());
                        SCSpatialFeature feature = new SCSpatialFeature();
                        // deserialize byte[] to Geometry object
                        byte[] wkb = SCSqliteHelper.getBlob(cursor, source.getGeomColumnName());
                        try {
                            if (wkb != null && wkb.length > 0) {
                                feature = new SCGeometry(
                                        new WKBReader(GEOMETRY_FACTORY).read(wkb)
                                );
                            }
                        }
                        catch (ParseException e) {
                            Log.w(LOG_TAG, String.format(
                                "Could not parse geometry for feature %s.%s",
                                source.getTableName(), rowId), e);
                            Log.v(LOG_TAG, "Invalid geometry was: " + SCSqliteHelper.getString(cursor, source.getGeomColumnName()));
                        }
                        feature.setStoreId(scStoreConfig.getUniqueId());
                        feature.setLayerId(source.getTableName());
                        feature.setId(rowId);
                        for (Map.Entry<String, String> column : source.getColumns().entrySet()) {
                            if (column.getValue().equalsIgnoreCase("BLOB")
                                    || column.getValue().equalsIgnoreCase("GEOMETRY")
                                    || column.getValue().equalsIgnoreCase("POINT")
                                    || column.getValue().equalsIgnoreCase("LINESTRING")
                                    || column.getValue().equalsIgnoreCase("POLYGON")) {
                                feature.getProperties().put(
                                        column.getKey(),
                                        SCSqliteHelper.getBlob(cursor, column.getKey())
                                );
                            }
                            else if (column.getValue().startsWith("INTEGER")) {
                                feature.getProperties().put(
                                        column.getKey(),
                                        SCSqliteHelper.getInt(cursor, column.getKey())
                                );
                            }
                            else if (column.getValue().startsWith("REAL")) {
                                feature.getProperties().put(
                                        column.getKey(),
                                        SCSqliteHelper.getLong(cursor, column.getKey())
                                );
                            }
                            else if (column.getValue().startsWith("TEXT")) {
                                // handle photos column
                                if (column.getKey().equals("photos")) {
                                    String photosValue = getBase64StringsForPhotos(
                                        source.getTableName() + GeoPackage.PHOTOS_MAPPING_TABLE_SUFFIX,
                                        SCSqliteHelper.getString(cursor, column.getKey())
                                    );
                                    feature.getProperties().put(column.getKey(), photosValue);
                                } else {
                                    feature.getProperties().put(
                                        column.getKey(),
                                        SCSqliteHelper.getString(cursor, column.getKey())
                                    );
                                }
                            }
                            else if (column.getValue().startsWith("DATE") ||
                                column.getValue().startsWith("TIMESTAMP")) {
                                // assume dates and times are stored as strings
                                feature.getProperties().put(
                                    column.getKey(),
                                    SCSqliteHelper.getString(cursor, column.getKey())
                                );
                            }
                            else {
                                Log.w(LOG_TAG, "The column type " + column.getValue() + " did not match any supported" +
                                        " column type so it wasn't added to the feature.");
                            }
                        }
                        return feature;
                    }
                });
            }
        };
    }

    /**
     * Accepts a String representing a JavaScript array of Exchange fileservice urls and returns
     * another String representing a JavaScript array of base64 encoded images fetched from the
     * mappingTable.
     *
     * @param photos a string that contains a JS array of urls
     * @returna string that contains a JS array of base64 encoded images
     */
    protected String getBase64StringsForPhotos(String mappingTableName, String photos) {
        StringBuilder sb = new StringBuilder(photos.length());
        sb.append("[");
        List<String> photoUrls =
            Arrays.asList(photos.replace("[", "").replace("]", "").split("\\,"));
        List<String> base64Images = new ArrayList<>(photoUrls.size());
        for (int i = 0; i < photoUrls.size(); i++) {
            String photoUrl = photoUrls.get(i);
            photoUrl = photoUrl.replace("\"", "");
            if (photoUrl.startsWith("http") && photoUrl.contains("fileservice")) {
                String sql =
                    String.format("SELECT blob FROM %s WHERE url = %s", mappingTableName, photoUrl);
                Cursor cursor = gpkg.query(sql);
                if (cursor != null && cursor.moveToFirst()) {
                    String imgString = Base64.encodeToString(cursor.getBlob(0), Base64.DEFAULT);
                    base64Images.add(imgString);
                    sb.append("\"").append(imgString).append("\"");
                } else {
                    base64Images.add(NO_IMAGE);
                    sb.append("\"").append(NO_IMAGE).append("\"");
                }
                if (i != photoUrls.size() - 1) {
                    sb.append(",");
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static String getVersionKey() {
        return String.format("%s.%s",TYPE, VERSION);
    }

    public SCStoreConfig getStoreConfig() {
        return scStoreConfig;
    }
}
