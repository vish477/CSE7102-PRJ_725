package com.example.geosafe;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class IncidentActivity extends AppCompatActivity {
    LinearLayout list;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_incidents);
        list = findViewById(R.id.incidentList);
        String data = getSharedPreferences("geosafe", MODE_PRIVATE)
                .getString("incidents", "No incidents recorded yet.");
        TextView t = new TextView(this);
        t.setText(data);
        t.setTextSize(18);
        t.setPadding(16,16,16,16);
        list.addView(t);
    }
}
