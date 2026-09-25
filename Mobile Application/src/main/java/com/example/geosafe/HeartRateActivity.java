package com.example.geosafe;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.telephony.SmsManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class HeartRateActivity extends AppCompatActivity {
    TextView bpm, status, countdown;
    Button cancelButton;
    CountDownTimer alertTimer;
    SharedPreferences prefs;
    static final int SMS_REQ = 301;
    boolean emergencyPending = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_heart_rate);

        bpm = findViewById(R.id.txtBpm);
        status = findViewById(R.id.txtHrStatus);
        countdown = findViewById(R.id.txtCountdown);
        cancelButton = findViewById(R.id.btnCancelAlert);
        prefs = getSharedPreferences("geosafe", MODE_PRIVATE);

        findViewById(R.id.btnDemoNormal).setOnClickListener(v -> setHr(82));
        findViewById(R.id.btnDemoAbnormal).setOnClickListener(v -> startAbnormalDemo());
        cancelButton.setOnClickListener(v -> cancelEmergency());
        cancelButton.setVisibility(Button.GONE);
        countdown.setVisibility(TextView.GONE);
    }

    private void setHr(int value) {
        if (alertTimer != null) alertTimer.cancel();
        emergencyPending = false;
        cancelButton.setVisibility(Button.GONE);
        countdown.setVisibility(TextView.GONE);
        bpm.setText("❤️ " + value + " BPM");
        if (value >= 150) {
            status.setText("⚠️ Abnormal reading detected");
        } else {
            status.setText("✅ NORMAL");
        }
    }

    // Demonstrates the intended project flow: 150 -> 155 -> 160 BPM,
    // then a 10-second cancellation window, then automatic SMS.
    private void startAbnormalDemo() {
        if (alertTimer != null) alertTimer.cancel();
        emergencyPending = true;
        cancelButton.setVisibility(Button.VISIBLE);
        countdown.setVisibility(TextView.VISIBLE);

        status.setText("⚠️ Sustained abnormal pattern detected\nEmergency alert will be sent automatically unless cancelled.");
        bpm.setText("❤️ 150 → 155 → 160 BPM");

        alertTimer = new CountDownTimer(10000, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                countdown.setText("⏱️ Sending emergency alert in " +
                        ((millisUntilFinished + 999) / 1000) + " seconds");
            }

            @Override public void onFinish() {
                if (emergencyPending) {
                    emergencyPending = false;
                    cancelButton.setVisibility(Button.GONE);
                    countdown.setText("🚨 Emergency alert triggered");
                    sendAutomaticSms();
                }
            }
        }.start();
    }

    private void cancelEmergency() {
        emergencyPending = false;
        if (alertTimer != null) alertTimer.cancel();
        cancelButton.setVisibility(Button.GONE);
        countdown.setVisibility(TextView.GONE);
        status.setText("✅ Alert cancelled by user");
    }

    private void sendAutomaticSms() {
        String phone = prefs.getString("contact_number", "");
        if (phone.isEmpty()) {
            status.setText("⚠️ Alert triggered, but no trusted contact is saved. Add a contact first.");
            Toast.makeText(this, "Add a trusted contact first", Toast.LENGTH_LONG).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, SMS_REQ);
            return;
        }
        sendSmsNow(phone);
    }

    private void sendSmsNow(String phone) {
        String message = "GeoSafe Emergency Alert: Possible distress detected from sustained abnormal heart-rate pattern. Please check on the user.";
        String locationText = getLastLocationText();
        if (!locationText.isEmpty()) {
            message += " Location: " + locationText;
        }

        try {
            SmsManager.getDefault().sendTextMessage(phone, null, message, null, null);
            status.setText("🚨 Emergency SMS sent automatically to trusted contact.");
            Toast.makeText(this, "Emergency SMS sent", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            status.setText("⚠️ Emergency alert triggered, but SMS failed: " + e.getMessage());
        }
    }

    private String getLastLocationText() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "";
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location location = null;
            if (lm != null) {
                location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location != null) {
                return location.getLatitude() + "," + location.getLongitude()
                        + " https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
            }
        } catch (Exception ignored) { }
        return "";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_REQ && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            String phone = prefs.getString("contact_number", "");
            if (!phone.isEmpty()) sendSmsNow(phone);
            else status.setText("⚠️ Alert triggered, but no trusted contact is saved.");
        } else if (requestCode == SMS_REQ) {
            status.setText("⚠️ Emergency detected, but SMS permission was denied.");
        }
    }
}
