package com.example.geosafe;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class DeviceConnectionActivity extends AppCompatActivity {
    TextView status;
    static final int REQ = 501;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_device_connection);
        status = findViewById(R.id.txtDeviceStatus);
        findViewById(R.id.btnConnectDevice).setOnClickListener(v -> connect());
        findViewById(R.id.btnStartMonitoring).setOnClickListener(v -> startMonitoring());
    }
    private void connect() {
        if (Build.VERSION.SDK_INT >= 31 &&
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                 ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ);
            return;
        }
        startMonitoring();
    }
    private void startMonitoring() {
        Intent i = new Intent(this, BleService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        status.setText("Device Status: Scanning for GeoSafe-ESP32...");
        Toast.makeText(this, "BLE monitoring started", Toast.LENGTH_SHORT).show();
    }
}
