package com.example.geosafe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeoSafeGeofence";

    @Override
    public void onReceive(Context context, Intent intent) {

        GeofencingEvent geofencingEvent =
                GeofencingEvent.fromIntent(intent);

        if (geofencingEvent == null) {
            Log.e(TAG, "Geofencing event is null");
            return;
        }

        if (geofencingEvent.hasError()) {
            Log.e(
                    TAG,
                    "Geofence error: " +
                            geofencingEvent.getErrorCode()
            );
            return;
        }

        int transition =
                geofencingEvent.getGeofenceTransition();

        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {

            Log.d(TAG, "User entered GeoSafe zone");

            Toast.makeText(
                    context,
                    "⚠️ GeoSafe: You entered a safety zone",
                    Toast.LENGTH_LONG
            ).show();

        } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            Log.d(TAG, "User exited GeoSafe zone");

            Toast.makeText(
                    context,
                    "GeoSafe: You exited the safety zone",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}