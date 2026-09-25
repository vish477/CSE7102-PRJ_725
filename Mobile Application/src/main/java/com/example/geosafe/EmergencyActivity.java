package com.example.geosafe;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class EmergencyActivity extends AppCompatActivity {
    SharedPreferences prefs;
    TextView status;
    final int SMS_REQ = 201, CALL_REQ = 202;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_emergency);
        prefs = getSharedPreferences("geosafe", MODE_PRIVATE);
        status = findViewById(R.id.txtEmergencyStatus);

        findViewById(R.id.btnEmergencyAlert).setOnClickListener(v -> sendEmergencySms());
        findViewById(R.id.btnEmergencyCall).setOnClickListener(v -> callContact());
    }

    private String number() { return prefs.getString("contact_number", ""); }

    private void sendEmergencySms() {
        String phone = number();
        if (phone.isEmpty()) {
            Toast.makeText(this, "Add a trusted contact first", Toast.LENGTH_LONG).show();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SMS_REQ);
            return;
        }
        String msg = "GeoSafe Emergency Alert — Possible distress detected. Please check on the user. Location will be attached when available.";
        try {
            SmsManager.getDefault().sendTextMessage(phone, null, msg, null, null);
            status.setText("🚨 Emergency SMS sent to trusted contact.");
        } catch (Exception e) {
            status.setText("SMS failed: " + e.getMessage());
        }
    }

    private void callContact() {
        String phone = number();
        if (phone.isEmpty()) {
            Toast.makeText(this, "Add a trusted contact first", Toast.LENGTH_LONG).show();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, CALL_REQ);
            return;
        }
        startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone)));
    }
}
