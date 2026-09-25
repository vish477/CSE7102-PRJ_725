package com.example.geosafe;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.telephony.SmsManager;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/**
 * GeoSafe BLE gateway.
 * ESP32 sends: {"hr":160,"lat":12.345,"lon":77.123}
 * The service keeps monitoring and triggers an automatic alert after
 * a sustained abnormal pattern. It uses the phone SMS capability.
 */
public class BleService extends Service {
    public static final String ACTION_DATA = "com.example.geosafe.BLE_DATA";
    public static final String ACTION_STATUS = "com.example.geosafe.BLE_STATUS";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_STATUS = "status";

    private static final String DEVICE_NAME = "GeoSafe-ESP32";
    private static final UUID SERVICE_UUID =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_UUID =
            UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    private BluetoothGatt gatt;
    private boolean scanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int abnormalCount = 0;
    private boolean alertPending = false;
    private String lastLat = "";
    private String lastLon = "";

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt = g;
                sendStatus("Connected to ESP32");
                if (Build.VERSION.SDK_INT >= 31 &&
                        ActivityCompat.checkSelfPermission(BleService.this,
                                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendStatus("Disconnected - reconnecting");
                closeGatt();
                handler.postDelayed(() -> startScan(), 3000);
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService s = g.getService(SERVICE_UUID);
            if (s == null) { sendStatus("ESP32 service not found"); return; }
            BluetoothGattCharacteristic c = s.getCharacteristic(RX_UUID);
            if (c == null) { sendStatus("ESP32 data characteristic not found"); return; }
            if (Build.VERSION.SDK_INT >= 31 &&
                    ActivityCompat.checkSelfPermission(BleService.this,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
            g.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor d = c.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            if (d != null) {
                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(d);
            }
            sendStatus("Receiving sensor data");
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            String text = new String(c.getValue(), StandardCharsets.UTF_8).trim();
            processSensorData(text);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(7, notification("GeoSafe monitoring is active"));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startScan();
        return START_STICKY;
    }

    private void startScan() {
        if (scanning) return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            sendStatus("Bluetooth is off");
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            sendStatus("Bluetooth permission required");
            return;
        }
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;
        scanning = true;
        sendStatus("Scanning for GeoSafe-ESP32");
        scanner.startScan(scanCallback);
        handler.postDelayed(() -> {
            if (scanning) {
                scanner.stopScan(scanCallback);
                scanning = false;
                if (gatt == null) handler.postDelayed(this::startScan, 2000);
            }
        }, 10000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            String name = null;
            if (Build.VERSION.SDK_INT >= 31 &&
                    ActivityCompat.checkSelfPermission(BleService.this,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
            try { name = d.getName(); } catch (Exception ignored) {}
            if (DEVICE_NAME.equals(name)) {
                BluetoothLeScanner scanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
                if (scanner != null) {
                    try { scanner.stopScan(this); } catch (Exception ignored) {}
                }
                scanning = false;
                connect(d);
            }
        }
    };

    private void connect(BluetoothDevice d) {
        if (Build.VERSION.SDK_INT >= 31 &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        sendStatus("Connecting to ESP32");
        gatt = d.connectGatt(this, false, callback);
    }

    private void processSensorData(String text) {
        try {
            JSONObject o = new JSONObject(text);
            int hr = o.optInt("hr", -1);
            lastLat = String.valueOf(o.optDouble("lat", 0));
            lastLon = String.valueOf(o.optDouble("lon", 0));
            if (hr >= 0) {
                getSharedPreferences("geosafe", MODE_PRIVATE).edit()
                        .putInt("live_hr", hr).putString("live_lat", lastLat)
                        .putString("live_lon", lastLon).apply();
                sendData(text);
                if (hr >= 150) abnormalCount++; else abnormalCount = 0;
                // Require three consecutive abnormal readings.
                if (abnormalCount >= 3 && !alertPending) {
                    alertPending = true;
                    sendStatus("ABNORMAL HEART RATE - automatic alert pending");
                    showWarningNotification();
                    handler.postDelayed(() -> {
                        if (alertPending) {
                            alertPending = false;
                            sendAutomaticSms(hr);
                        }
                    }, 10000);
                }
            }
        } catch (Exception e) {
            sendData(text);
        }
    }

    private void sendAutomaticSms(int hr) {
        String phone = getSharedPreferences("geosafe", MODE_PRIVATE)
                .getString("contact_number", "");
        if (phone.isEmpty()) {
            sendStatus("Alert detected, but no trusted contact is saved");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            sendStatus("SMS permission is required");
            return;
        }
        String msg = "GeoSafe Emergency Alert: Possible distress detected. Sustained abnormal heart rate: "
                + hr + " BPM. Location: " + lastLat + "," + lastLon
                + " https://maps.google.com/?q=" + lastLat + "," + lastLon;
        try {
            SmsManager.getDefault().sendTextMessage(phone, null, msg, null, null);
            sendStatus("Emergency SMS sent automatically");
            getSharedPreferences("geosafe", MODE_PRIVATE).edit()
                    .putLong("last_alert", System.currentTimeMillis()).apply();
        } catch (Exception e) {
            sendStatus("SMS failed: " + e.getMessage());
        }
    }

    public void cancelPendingAlert() { alertPending = false; abnormalCount = 0; }

    private void sendData(String data) {
        Intent i = new Intent(ACTION_DATA).setPackage(getPackageName());
        i.putExtra(EXTRA_DATA, data);
        sendBroadcast(i);
    }

    private void sendStatus(String status) {
        Intent i = new Intent(ACTION_STATUS).setPackage(getPackageName());
        i.putExtra(EXTRA_STATUS, status);
        sendBroadcast(i);
        updateNotification(status);
    }

    private void closeGatt() {
        if (gatt != null) {
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
    }

    private Notification notification(String text) {
        return new Notification.Builder(this, "geosafe")
                .setContentTitle("GeoSafe")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true).build();
    }
    private void updateNotification(String text) {
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7, notification(text));
    }
    private void showWarningNotification() {
        Notification n = new Notification.Builder(this, "geosafe")
                .setContentTitle("GeoSafe: Possible Distress")
                .setContentText("Abnormal heart rate detected. Alert will be sent unless cancelled.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(false).build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(8, n);
    }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel("geosafe", "GeoSafe Monitoring",
                    NotificationManager.IMPORTANCE_HIGH);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { closeGatt(); super.onDestroy(); }
}
