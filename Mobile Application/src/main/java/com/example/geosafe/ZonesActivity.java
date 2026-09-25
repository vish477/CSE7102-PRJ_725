package com.example.geosafe;

import android.location.Location;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ZonesActivity extends AppCompatActivity {
    EditText name, lat, lon, radius;
    TextView result;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_zones);
        name=findViewById(R.id.zoneName); lat=findViewById(R.id.zoneLat);
        lon=findViewById(R.id.zoneLon); radius=findViewById(R.id.zoneRadius);
        result=findViewById(R.id.zoneResult);
        findViewById(R.id.btnSaveZone).setOnClickListener(v -> {
            try {
                getSharedPreferences("geosafe",MODE_PRIVATE).edit()
                        .putString("zone_name",name.getText().toString())
                        .putString("zone_lat",lat.getText().toString())
                        .putString("zone_lon",lon.getText().toString())
                        .putString("zone_radius",radius.getText().toString()).apply();
                result.setText("Risk zone saved successfully");
            } catch(Exception e) { result.setText("Enter valid zone values"); }
        });
    }
}
