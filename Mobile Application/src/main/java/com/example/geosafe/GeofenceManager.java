package com.example.geosafe;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

public class GeofenceManager {

    private static final String GEOFENCE_ID = "GEOSAFE_RISK_ZONE";

    // TEST ZONE
    // Replace these later with coordinates chosen from your Zones screen.
    private static final double TEST_LATITUDE = 13.492228;
    private static final double TEST_LONGITUDE = 77.530285;

    // 200 metre test radius
    private static final float TEST_RADIUS = 10.0f;

    private final Context context;
    private final GeofencingClient geofencingClient;
    private PendingIntent geofencePendingIntent;

    public GeofenceManager(Context context) {
        this.context = context.getApplicationContext();
        this.geofencingClient =
                LocationServices.getGeofencingClient(this.context);
    }

    private PendingIntent getGeofencePendingIntent() {

        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }

        Intent intent =
                new Intent(context, GeofenceBroadcastReceiver.class);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        geofencePendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        1001,
                        intent,
                        flags
                );

        return geofencePendingIntent;
    }

    public void startTestGeofence() {

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            throw new SecurityException(
                    "Location permission is not granted"
            );
        }

        Geofence geofence =
                new Geofence.Builder()

                        .setRequestId(GEOFENCE_ID)

                        .setCircularRegion(
                                TEST_LATITUDE,
                                TEST_LONGITUDE,
                                TEST_RADIUS
                        )

                        .setExpirationDuration(
                                Geofence.NEVER_EXPIRE
                        )

                        .setTransitionTypes(
                                Geofence.GEOFENCE_TRANSITION_ENTER
                                        | Geofence.GEOFENCE_TRANSITION_EXIT
                        )

                        .build();

        GeofencingRequest geofencingRequest =
                new GeofencingRequest.Builder()

                        .setInitialTrigger(
                                GeofencingRequest.INITIAL_TRIGGER_ENTER
                        )

                        .addGeofence(geofence)

                        .build();

        geofencingClient
                .addGeofences(
                        geofencingRequest,
                        getGeofencePendingIntent()
                )
                .addOnSuccessListener(unused -> {

                    android.util.Log.d(
                            "GeoSafeGeofence",
                            "TEST GEO-FENCE ADDED"
                    );

                })
                .addOnFailureListener(e -> {

                    android.util.Log.e(
                            "GeoSafeGeofence",
                            "FAILED TO ADD GEO-FENCE: "
                                    + e.getMessage()
                    );

                });
    }

    public void stopGeofence() {

        geofencingClient
                .removeGeofences(
                        getGeofencePendingIntent()
                )
                .addOnSuccessListener(unused -> {

                    android.util.Log.d(
                            "GeoSafeGeofence",
                            "GEO-FENCE REMOVED"
                    );

                })
                .addOnFailureListener(e -> {

                    android.util.Log.e(
                            "GeoSafeGeofence",
                            "FAILED TO REMOVE GEO-FENCE: "
                                    + e.getMessage()
                    );

                });
    }
}