package com.example.geosafe;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class GpsActivity extends AppCompatActivity {
    private TextView txtGps, txtGeo;
    private LocationManager lm;
    private final int REQ = 100;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_gps);
        txtGps = findViewById(R.id.txtGps);
        txtGeo = findViewById(R.id.txtGeofence);
        lm = (LocationManager)getSystemService(LOCATION_SERVICE);

        findViewById(R.id.btnGetLocation).setOnClickListener(v -> getLocation());
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ);
            return;
        }
        try {
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) showLocation(last);
            else txtGps.setText("No last location. Enable GPS and try again outdoors.");
        } catch (SecurityException e) {
            txtGps.setText("Location permission required.");
        }
    }

    private void showLocation(Location l) {
        txtGps.setText(String.format("Latitude: %.6f\\nLongitude: %.6f\\nAccuracy: %.1f m",
                l.getLatitude(), l.getLongitude(), l.getAccuracy()));
        txtGeo.setText("⚠️ Geo-fence demo: location received. Configure a real zone after GPS integration.");
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if (r == REQ && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) getLocation();
    }
}
