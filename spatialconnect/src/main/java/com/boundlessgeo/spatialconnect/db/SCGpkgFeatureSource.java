package com.boundlessgeo.spatialconnect.db;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;
import com.boundlessgeo.spatialconnect.geometries.SCGeometry;
import com.boundlessgeo.spatialconnect.geometries.SCSpatialFeature;
import com.squareup.sqlbrite.BriteDatabase;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import rx.Observable;
import rx.Subscriber;

import static com.boundlessgeo.spatialconnect.db.GeoPackage.RECEIVED_AUDIT_COL;
import static com.boundlessgeo.spatialconnect.db.GeoPackage.SENT_AUDIT_COL;

/**
 * This class is responsible for reading and writing {@link SCSpatialFeature}s to a feature table in a GeoPackage.
 */
public class SCGpkgFeatureSource {

    /**
     * The log tag for this class.
     */
    private final String LOG_TAG = SCGpkgFeatureSource.class.getSimpleName();

    /**
     * The instance of the GeoPackage used to connect to the database.
     */
    private GeoPackage gpkg;

    /**
     * The name of the table.
     */
    private String tableName;

    private String auditName;

    /**
     * The name of the primary key column.
     */
    private String primaryKeyName;

    /**
     * The name of the geometry column.
     */
    private String geomColumnName;


    /**
     * A map of the columns and their database types.
     */
    private Map<String, String> columns = new LinkedHashMap<>();

    private BriteDatabase db;

    /**
     * Creates and instance of the {@link SCGpkgFeatureSource} using the {@link GeoPackage} containing the db
     * connection and the database table name.
     *
     * @param geoPackage
     * @param tableName
     */
    public SCGpkgFeatureSource(GeoPackage geoPackage, String tableName) {
        this.gpkg = geoPackage;
        this.tableName = tableName;
        this.auditName = String.format("%s_audit", tableName);
    }

    public Map<String, String> getColumns() {
        return columns;
    }

    public void addColumn(String columnName, String columnType) {
        this.columns.put(columnName, columnType);
    }

    public void setGeomColumnName(String geomColumnName) {
        this.geomColumnName = geomColumnName;
    }

    public void setPrimaryKeyName(String primaryKeyName) {
        this.primaryKeyName = primaryKeyName;
    }

    public String getTableName() {
        return tableName;
    }

    public String getPrimaryKeyName() {
        return primaryKeyName;
    }

    public String getGeomColumnName() {
        return geomColumnName;
    }

    /**
     * Builds the column names part of the INSERT statement.  Only adds the columns from the feature, which may not be
     * all columns in the schema.  Note that the INSERT statement doesn't use the primary key column b/c it should be
     * auto generated by the database.
     *
     * @return a CSV string of all column names used in the INSERT statement
     */
    public String getColumnNamesForInsert(SCSpatialFeature feature) {
        StringBuilder sb = new StringBuilder();
        List<String> columnNames = new ArrayList<>(columns.keySet());
        Collections.sort(columnNames, ALPHABETICAL_ORDER);
        for (String columnName : columnNames) {
            sb.append(columnName + ",");
        }
        sb.append(geomColumnName);
        return sb.toString();
    }

    /**
     * Builds the column values part of the INSERT statement.  In order to ensure that the columns match the values, we
     * sort the column names alphabetically.
     *
     * @return a CSV string of all column values used in the INSERT statement
     */
    public String getColumnValuesForInsert(final SCSpatialFeature feature) {
        StringBuilder sb = new StringBuilder();
        List<String> columnNames = new ArrayList<>(columns.keySet());
        Collections.sort(columnNames, ALPHABETICAL_ORDER);
        boolean firstIteration = true;
        for (String columnName : columnNames) {
            if (!firstIteration) {
                sb.append(",");
            }
            if (feature.getProperties().get(columnName) != null) {
                sb.append(DatabaseUtils.sqlEscapeString(String.valueOf(feature.getProperties().get(columnName))));
            } else {
                sb.append("NULL");
            }
            firstIteration = false;
        }
        if (sb.toString().length() > 0) {
            sb.append(",");
        }
        if (feature instanceof SCGeometry && ((SCGeometry)feature).getGeometry() != null) {
            sb.append("ST_GeomFromText('")
                    .append(((SCGeometry)feature).getGeometry().toString())
                    .append("')");
        }
        else {
            sb.append("NULL");
        }
        return sb.toString();
    }

    /**
     * Builds the {@code SET column_name = value1, column2 = value2} part of the UPDATE statement.
     *
     * @param feature
     * @return a string containing the SET clause of the UPDATE statement
     */
    public String getUpdateSetClause(SCSpatialFeature feature) {
        StringBuilder sb = new StringBuilder();
        for (String columnName : columns.keySet()) {
            if (feature.getProperties().get(columnName) != null) {
                sb.append(columnName);
                sb.append("=");
                sb.append((DatabaseUtils.sqlEscapeString(String.valueOf(feature.getProperties().get(columnName)))));
                sb.append(", ");
            }
        }
        sb.append(geomColumnName);
        sb.append("=");
        sb.append("ST_GeomFromText('").append(((SCGeometry)feature).getGeometry().toString()).append("')");
        return sb.toString();
    }

    public SCSpatialFeature featureFromResultSet(Cursor rs) {
        Map<String,Object> properties = new HashMap<>();
        String data;
        String columnName;

        //check if blob and create SCGeometry
        SCSpatialFeature spatialFeature = null;
        SCGeometry geometry = null;
        // deserialize byte[] to Geometry object
        byte[] wkb = SCSqliteHelper.getBlob(rs, getGeomColumnName());
        if (wkb != null && wkb.length > 0) {
            try {
                geometry = new SCGeometry(
                        new WKBReader(new GeometryFactory(new PrecisionModel(), 0)).read(wkb));
                spatialFeature = geometry;
            } catch (ParseException e) {
                e.printStackTrace();
                Log.e(LOG_TAG, "e: " + e.getMessage());
                spatialFeature = new SCGeometry();
            }
        } else {
            spatialFeature = new SCGeometry();
        }

        for (int i=0; i<rs.getColumnCount(); i++) {
            columnName = rs.getColumnName(i);
            if (!columnName.equalsIgnoreCase(geomColumnName)) {
                data = rs.getString(i);
                properties.put(columnName, data);
            }
        }

        spatialFeature.setId(properties.get(primaryKeyName).toString());
        spatialFeature.setLayerId(tableName);

        properties.remove(primaryKeyName);
        properties.remove(RECEIVED_AUDIT_COL);
        properties.remove(SENT_AUDIT_COL);
        spatialFeature.setProperties(properties);

        return spatialFeature;
    }

    public Observable<SCSpatialFeature> unSent() {
        Log.d(LOG_TAG, "Fetching unsent features");
        final String sql = String.format("SELECT %s FROM %s WHERE sent IS NULL",
                gpkg.getSelectColumnsString(this),
                auditName);

        return Observable.create(new Observable.OnSubscribe<SCSpatialFeature>() {
            @Override
            public void call(final Subscriber subscriber) {
                query(sql, subscriber);
            }
        });
    }

    public void updateAuditTable(SCSpatialFeature feature) {
        Cursor cursor = null;
        try {
            String query =
                    String.format(Locale.US, "UPDATE %s SET sent = datetime() WHERE %s = %s",
                            this.auditName, this.primaryKeyName, feature.getId());
            cursor = gpkg.query(query);
            cursor.moveToFirst();
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Something went wrong updating audit table: " + ex.getMessage());
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void updateAuditTableFromLatest(SCSpatialFeature feature) {
        Cursor cursor = null;
        try {
            String query =
                    String.format(Locale.US, "UPDATE %s SET sent = datetime() WHERE %s <= %s AND SENT IS NULL",
                            this.auditName, this.primaryKeyName, feature.getId());
            cursor = gpkg.query(query);
            cursor.moveToFirst();
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Something went wrong updating audit table: " + ex.getMessage());
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void query(String sql, Subscriber subscriber) {
        Cursor layerCursor = gpkg.query(sql);
        try {
            while (layerCursor.moveToNext()) {
                SCSpatialFeature f = featureFromResultSet(layerCursor);
                subscriber.onNext(f);

            }
            subscriber.onCompleted();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Something went wrong trying to get unsynced features: " + e.getMessage());
            subscriber.onError(e);
        } finally {
            layerCursor.close();
        }
    }

    private static Comparator<String> ALPHABETICAL_ORDER = new Comparator<String>() {
        public int compare(String str1, String str2) {
            int res = String.CASE_INSENSITIVE_ORDER.compare(str1, str2);
            if (res == 0) {
                res = str1.compareTo(str2);
            }
            return res;
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SCGpkgFeatureSource that = (SCGpkgFeatureSource) o;

        if (!gpkg.equals(that.gpkg)) return false;
        return tableName.equals(that.tableName);

    }

    @Override
    public int hashCode() {
        int result = gpkg.hashCode();
        result = 31 * result + tableName.hashCode();
        return result;
    }
}
