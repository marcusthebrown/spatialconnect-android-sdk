/**
 * Copyright 2015-2017 Boundless, http://boundlessgeo.com
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
package com.boundlessgeo.spatialconnect.scutilities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import com.google.android.gms.maps.GoogleMap;
import rx.Observable;
import rx.subjects.AsyncSubject;
import rx.subjects.PublishSubject;


/**
 * LocationHelper is used to provide GPS functionality to a GoogleMap or other component that needs the users current
 * location.  It can enable/disable GPS listening, can get the users location using the GPS provider, and also handles
 * the process of asking the user for permission to use the GPS to obtain their current location.
 *
 * For Android 6.0 (API 23) you need to explicitly get permission to use GPS location at runtime.  See
 * <a href="http://developer.android.com/training/permissions/requesting.html"> the docs </a> for more info.
 *
 *
 * http://developer.android.com/training/location/retrieve-current.html
 */
public class LocationHelper implements LocationListener {

    private Context context;
    private GoogleMap map;
    private LocationManager locationManager;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1; // 1 is an arbitrary request code
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters
    private static final long MIN_TIME_BETWEEN_UPDATES = 15000; // every 15 seconds
    private static final PublishSubject<Location> LOCATION_SUBJECT = PublishSubject.create();
    private static final AsyncSubject<Boolean> PERMISSION_SUBJECT = AsyncSubject.create();


    public LocationHelper(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public LocationHelper(Context context, GoogleMap map) {
        this.context = context;
        this.map = map;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Creates a Observable stream that wraps the LocationListener callbacks to emit a Location instance when the
     * onLocationChanged() callback is triggered.
     *
     * @return an Observable of Locations updated while the GPS is enabled
     * @see LocationHelper#onLocationChanged(Location)
     */
    public Observable<Location> getLocation() {
        return LOCATION_SUBJECT.asObservable();
    }

    /**
     * Convenience method to obtain the users last known location from the GPS or request the permission to do so if
     * it hasn't yet been granted.
     *
     * @return the last known Location instance or null if the permission was not granted
     */
    private Location getLastKnownLocation() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // check for permissions
            // http://developer.android.com/training/permissions/requesting.html
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {

                return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } else {
                ActivityCompat.requestPermissions(
                        (Activity) context,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                );
            }
        }
        return null;
    }

    /**
     * Convenience method to check if the GPS
     * @return boolean
     */
    public boolean isGPSPermissionGranted() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Notify all subscribers of the LOCATION_SUBJECT instance with the latest location.
     *
     * @param location
     */
    @Override
    public void onLocationChanged(Location location) {
        LOCATION_SUBJECT.onNext(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    /**
     * Enable GPS updates.  (start listening)
     */
    public void enableGps(int accuracy, float distance) {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(accuracy);
        String provider = locationManager.getBestProvider(criteria, true);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager.requestLocationUpdates(
                provider,
                MIN_TIME_BETWEEN_UPDATES,
                0,
                this
        );
    }

    /**
     * Disable GPS updates. (stop listening)
     */
    public void disableGps() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

}

