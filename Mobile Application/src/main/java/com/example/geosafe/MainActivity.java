package com.example.geosafe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private GeofenceManager geofenceManager;

    // =========================================================
    // GEOSAFE ESP32 BLE SETTINGS
    // =========================================================

    private static final String DEVICE_NAME = "GeoSafe-ESP32";

    // Nordic UART Service
    private static final UUID SERVICE_UUID =
            UUID.fromString(
                    "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
            );

    // Nordic UART TX Characteristic
    private static final UUID CHAR_UUID =
            UUID.fromString(
                    "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
            );

    // Client Characteristic Configuration Descriptor
    private static final UUID CCCD_UUID =
            UUID.fromString(
                    "00002902-0000-1000-8000-00805f9b34fb"
            );

    // =========================================================
    // PERMISSION REQUEST CODES
    // =========================================================

    private static final int REQUEST_BLUETOOTH = 1001;
    private static final int REQUEST_SMS = 1002;

    // =========================================================
    // TRUSTED CONTACTS
    // =========================================================

    private ContactManager contactManager;

    private String pendingAlertReason = "";

    // =========================================================
    // USER INTERFACE
    // =========================================================

    private TextView statusTextView;
    private TextView hrTextView;
    private TextView gpsTextView;

    // =========================================================
    // BLUETOOTH
    // =========================================================

    private BluetoothGatt bluetoothGatt;
    private BluetoothAdapter bluetoothAdapter;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // =========================================================
    // BLE JSON BUFFER
    // =========================================================

    private final StringBuilder bleDataBuffer =
            new StringBuilder();

    // =========================================================
    // LATEST SENSOR VALUES
    // =========================================================

    public int latestHeartRate = 0;

    public double latestLat = 0.0;

    public double latestLon = 0.0;

    // =========================================================
    // GEO-FENCE
    // =========================================================
    /*
     * MOVING SAFE ZONE MODE
     *
     * The latest GPS position is treated as the Safe Zone center.
     * Therefore, wherever the user moves, the current position
     * remains inside the Safe Zone.
     *
     * This is useful for testing the application without showing
     * a large distance such as 36 km.
     */

    private static final float SAFE_ZONE_RADIUS_METERS = 10.0f;

    private boolean geoFenceAlertActive = false;

    // =========================================================
    // HEART RATE ANOMALY DETECTOR
    // =========================================================

    private final HRAnomalyDetector anomalyDetector =
            new HRAnomalyDetector();

    // =========================================================
    // EMERGENCY COUNTDOWN
    // =========================================================

    private boolean countdownActive = false;

    private CountDownTimer emergencyCountdown;

    // =========================================================
    // ACTIVITY START
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // ---------------------------------------------------------
        // CONNECT UI
        // ---------------------------------------------------------

        statusTextView =
                findViewById(R.id.statusTextView);

        hrTextView =
                findViewById(R.id.hrTextView);

        gpsTextView =
                findViewById(R.id.gpsTextView);

        // ---------------------------------------------------------
        // GEO-FENCE MANAGER
        // ---------------------------------------------------------

        try {

            geofenceManager =
                    new GeofenceManager(this);

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {

                geofenceManager.startTestGeofence();
            }

        } catch (Exception ignored) {
        }

        // ---------------------------------------------------------
        // TRUSTED CONTACT MANAGER
        // ---------------------------------------------------------

        contactManager =
                new ContactManager(this);

        // ---------------------------------------------------------
        // GET BLUETOOTH ADAPTER
        // ---------------------------------------------------------

        BluetoothManager manager =
                (BluetoothManager)
                        getSystemService(BLUETOOTH_SERVICE);

        if (manager != null) {

            bluetoothAdapter =
                    manager.getAdapter();
        }

        statusTextView.setText(
                "Starting GeoSafe..."
        );

        // ---------------------------------------------------------
        // CHECK BLUETOOTH PERMISSIONS
        // ---------------------------------------------------------

        checkBluetoothPermissions();
    }

    // =========================================================
    // BLUETOOTH PERMISSIONS
    // =========================================================

    private void checkBluetoothPermissions() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S) {

            boolean scanGranted =
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                    ) ==
                            PackageManager.PERMISSION_GRANTED;

            boolean connectGranted =
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                    ) ==
                            PackageManager.PERMISSION_GRANTED;

            if (!scanGranted ||
                    !connectGranted) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        REQUEST_BLUETOOTH
                );

                return;
            }
        }

        startBleScan();
    }

    // =========================================================
    // PERMISSION RESULT
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        // ---------------------------------------------------------
        // BLUETOOTH PERMISSION
        // ---------------------------------------------------------

        if (requestCode ==
                REQUEST_BLUETOOTH) {

            if (grantResults.length >= 2
                    &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED
                    &&
                    grantResults[1] ==
                            PackageManager.PERMISSION_GRANTED) {

                startBleScan();

            } else {

                statusTextView.setText(
                        "Bluetooth permission denied"
                );
            }

            return;
        }

        // ---------------------------------------------------------
        // SMS PERMISSION
        // ---------------------------------------------------------

        if (requestCode ==
                REQUEST_SMS) {

            if (grantResults.length > 0
                    &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) {

                sendEmergencySms(
                        pendingAlertReason
                );

            } else {

                statusTextView.setText(
                        "SMS permission denied - alert was not sent"
                );
            }
        }
    }

    // =========================================================
    // START BLE SCAN
    // =========================================================

    @SuppressLint("MissingPermission")
    private void startBleScan() {

        if (bluetoothAdapter == null) {

            statusTextView.setText(
                    "Bluetooth not available"
            );

            return;
        }

        if (!bluetoothAdapter.isEnabled()) {

            statusTextView.setText(
                    "Please turn ON Bluetooth"
            );

            return;
        }

        if (bluetoothAdapter
                .getBluetoothLeScanner()
                == null) {

            statusTextView.setText(
                    "BLE scanner unavailable"
            );

            return;
        }

        statusTextView.setText(
                "Scanning for GeoSafe-ESP32..."
        );

        bluetoothAdapter
                .getBluetoothLeScanner()
                .startScan(scanCallback);

        // ---------------------------------------------------------
        // STOP SCANNING AFTER 15 SECONDS
        // ---------------------------------------------------------

        handler.postDelayed(
                new Runnable() {

                    @Override
                    public void run() {

                        try {

                            if (bluetoothAdapter != null
                                    &&
                                    bluetoothAdapter
                                            .getBluetoothLeScanner()
                                            != null) {

                                bluetoothAdapter
                                        .getBluetoothLeScanner()
                                        .stopScan(
                                                scanCallback
                                        );
                            }

                        } catch (Exception ignored) {
                        }
                    }
                },

                15000
        );
    }

    // =========================================================
    // BLE SCAN CALLBACK
    // =========================================================

    private final android.bluetooth.le.ScanCallback scanCallback =
            new android.bluetooth.le.ScanCallback() {

                @SuppressLint("MissingPermission")
                @Override
                public void onScanResult(
                        int callbackType,
                        android.bluetooth.le.ScanResult result) {

                    BluetoothDevice device =
                            result.getDevice();

                    String deviceName;

                    try {

                        deviceName =
                                device.getName();

                    } catch (SecurityException e) {

                        return;
                    }

                    if (deviceName != null
                            &&
                            deviceName.equalsIgnoreCase(
                                    DEVICE_NAME
                            )) {

                        // -----------------------------------------
                        // STOP SCAN
                        // -----------------------------------------

                        try {

                            if (bluetoothAdapter
                                    .getBluetoothLeScanner()
                                    != null) {

                                bluetoothAdapter
                                        .getBluetoothLeScanner()
                                        .stopScan(
                                                this
                                        );
                            }

                        } catch (Exception ignored) {
                        }

                        final String foundDeviceName =
                                deviceName;

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "Found "
                                                        + foundDeviceName
                                                        + "\nConnecting..."
                                        )
                        );

                        connectToDevice(
                                device
                        );
                    }
                }

                @Override
                public void onScanFailed(
                        int errorCode) {

                    runOnUiThread(
                            () ->
                                    statusTextView.setText(
                                            "BLE scan failed: "
                                                    + errorCode
                                    )
                    );
                }
            };

    // =========================================================
    // CONNECT TO ESP32
    // =========================================================

    @SuppressLint("MissingPermission")
    private void connectToDevice(
            BluetoothDevice device) {

        if (bluetoothGatt != null) {

            try {

                bluetoothGatt.disconnect();

                bluetoothGatt.close();

            } catch (Exception ignored) {
            }

            bluetoothGatt = null;
        }

        statusTextView.setText(
                "Connecting to GeoSafe-ESP32..."
        );

        bluetoothGatt =
                device.connectGatt(
                        this,
                        false,
                        gattCallback
                );
    }

    // =========================================================
    // GATT CALLBACK
    // =========================================================

    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {

                // =================================================
                // CONNECTION STATE
                // =================================================

                @SuppressLint("MissingPermission")
                @Override
                public void onConnectionStateChange(
                        BluetoothGatt gatt,
                        int status,
                        int newState) {

                    if (newState ==
                            BluetoothProfile.STATE_CONNECTED) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "BLE Connected\n"
                                                        + "Requesting larger MTU..."
                                        )
                        );

                        boolean mtuRequested =
                                gatt.requestMtu(
                                        247
                                );

                        if (!mtuRequested) {

                            runOnUiThread(
                                    () ->
                                            statusTextView.setText(
                                                    "MTU request failed\n"
                                                            + "Discovering services..."
                                            )
                            );

                            gatt.discoverServices();
                        }
                    }

                    else if (newState ==
                            BluetoothProfile.STATE_DISCONNECTED) {

                        synchronized (bleDataBuffer) {

                            bleDataBuffer.setLength(
                                    0
                            );
                        }

                        runOnUiThread(
                                () -> {

                                    statusTextView.setText(
                                            "BLE Disconnected"
                                    );

                                    hrTextView.setText(
                                            "Heart Rate: -- BPM"
                                    );

                                    gpsTextView.setText(
                                            "Latitude: --\nLongitude: --"
                                    );
                                }
                        );
                    }
                }

                // =================================================
                // MTU CHANGED
                // =================================================

                @SuppressLint("MissingPermission")
                @Override
                public void onMtuChanged(
                        BluetoothGatt gatt,
                        int mtu,
                        int status) {

                    if (status ==
                            BluetoothGatt.GATT_SUCCESS) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "BLE MTU: "
                                                        + mtu
                                                        + "\nDiscovering services..."
                                        )
                        );

                    } else {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "MTU negotiation failed\n"
                                                        + "Discovering services..."
                                        )
                        );
                    }

                    gatt.discoverServices();
                }

                // =================================================
                // SERVICES DISCOVERED
                // =================================================

                @SuppressLint("MissingPermission")
                @Override
                public void onServicesDiscovered(
                        BluetoothGatt gatt,
                        int status) {

                    if (status !=
                            BluetoothGatt.GATT_SUCCESS) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "Service discovery failed\n"
                                                        + "Status: "
                                                        + status
                                        )
                        );

                        return;
                    }

                    // ---------------------------------------------
                    // FIND NORDIC UART SERVICE
                    // ---------------------------------------------

                    BluetoothGattService service =
                            gatt.getService(
                                    SERVICE_UUID
                            );

                    if (service == null) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "GeoSafe BLE service not found"
                                        )
                        );

                        return;
                    }

                    // ---------------------------------------------
                    // FIND TX CHARACTERISTIC
                    // ---------------------------------------------

                    BluetoothGattCharacteristic characteristic =
                            service.getCharacteristic(
                                    CHAR_UUID
                            );

                    if (characteristic == null) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "BLE TX characteristic not found"
                                        )
                        );

                        return;
                    }

                    // ---------------------------------------------
                    // ENABLE LOCAL NOTIFICATIONS
                    // ---------------------------------------------

                    boolean localNotificationEnabled =
                            gatt.setCharacteristicNotification(
                                    characteristic,
                                    true
                            );

                    if (!localNotificationEnabled) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "Failed to enable BLE notifications"
                                        )
                        );

                        return;
                    }

                    // ---------------------------------------------
                    // FIND CCCD
                    // ---------------------------------------------

                    BluetoothGattDescriptor descriptor =
                            characteristic.getDescriptor(
                                    CCCD_UUID
                            );

                    if (descriptor == null) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "BLE notification descriptor not found"
                                        )
                        );

                        return;
                    }

                    // ---------------------------------------------
                    // ENABLE ESP32 NOTIFICATIONS
                    // ---------------------------------------------

                    boolean descriptorStarted;

                    if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.TIRAMISU) {

                        int result =
                                gatt.writeDescriptor(
                                        descriptor,
                                        BluetoothGattDescriptor
                                                .ENABLE_NOTIFICATION_VALUE
                                );

                        descriptorStarted =
                                result ==
                                        BluetoothGatt.GATT_SUCCESS;

                    } else {

                        descriptor.setValue(
                                BluetoothGattDescriptor
                                        .ENABLE_NOTIFICATION_VALUE
                        );

                        descriptorStarted =
                                gatt.writeDescriptor(
                                        descriptor
                                );
                    }

                    if (!descriptorStarted) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "Could not start notification setup"
                                        )
                        );

                        return;
                    }

                    runOnUiThread(
                            () ->
                                    statusTextView.setText(
                                            "BLE Connected\n"
                                                    + "Enabling notifications..."
                                    )
                    );
                }

                // =================================================
                // DESCRIPTOR WRITE RESULT
                // =================================================

                @Override
                public void onDescriptorWrite(
                        BluetoothGatt gatt,
                        BluetoothGattDescriptor descriptor,
                        int status) {

                    if (!CCCD_UUID.equals(
                            descriptor.getUuid()
                    )) {

                        return;
                    }

                    if (status ==
                            BluetoothGatt.GATT_SUCCESS) {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "BLE Connected\n"
                                                        + "Notifications enabled\n"
                                                        + "Waiting for live data..."
                                        )
                        );

                    } else {

                        runOnUiThread(
                                () ->
                                        statusTextView.setText(
                                                "BLE notification setup failed\n"
                                                        + "Status: "
                                                        + status
                                        )
                        );
                    }
                }

                // =================================================
                // BLE DATA - OLD CALLBACK
                // =================================================

                @Override
                public void onCharacteristicChanged(
                        BluetoothGatt gatt,
                        BluetoothGattCharacteristic characteristic) {

                    if (!CHAR_UUID.equals(
                            characteristic.getUuid()
                    )) {

                        return;
                    }

                    byte[] data =
                            characteristic.getValue();

                    if (data == null ||
                            data.length == 0) {

                        return;
                    }

                    handleBleData(
                            data
                    );
                }

                // =================================================
                // BLE DATA - ANDROID 13+
                // =================================================

                @Override
                public void onCharacteristicChanged(
                        BluetoothGatt gatt,
                        BluetoothGattCharacteristic characteristic,
                        byte[] value) {

                    if (!CHAR_UUID.equals(
                            characteristic.getUuid()
                    )) {

                        return;
                    }

                    if (value == null ||
                            value.length == 0) {

                        return;
                    }

                    handleBleData(
                            value
                    );
                }
            };

    // =========================================================
    // HANDLE BLE DATA
    // =========================================================

    private void handleBleData(
            byte[] data) {

        String raw =
                new String(
                        data,
                        StandardCharsets.UTF_8
                );

        synchronized (bleDataBuffer) {

            bleDataBuffer.append(
                    raw
            );

            while (true) {

                String buffered =
                        bleDataBuffer.toString();

                // ---------------------------------------------
                // FIND START OF JSON
                // ---------------------------------------------

                int start =
                        buffered.indexOf("{");

                if (start == -1) {

                    if (bleDataBuffer.length()
                            > 512) {

                        bleDataBuffer.setLength(
                                0
                        );
                    }

                    break;
                }

                // ---------------------------------------------
                // REMOVE TEXT BEFORE {
                // ---------------------------------------------

                if (start > 0) {

                    bleDataBuffer.delete(
                            0,
                            start
                    );

                    buffered =
                            bleDataBuffer.toString();

                    start = 0;
                }

                // ---------------------------------------------
                // FIND END OF JSON
                // ---------------------------------------------

                int end =
                        buffered.indexOf(
                                "}",
                                start
                        );

                if (end == -1) {

                    break;
                }

                // ---------------------------------------------
                // COMPLETE JSON FOUND
                // ---------------------------------------------

                final String completeJson =
                        buffered.substring(
                                start,
                                end + 1
                        );

                // ---------------------------------------------
                // REMOVE PROCESSED JSON
                // ---------------------------------------------

                bleDataBuffer.delete(
                        0,
                        end + 1
                );

                // ---------------------------------------------
                // PARSE ON UI THREAD
                // ---------------------------------------------

                runOnUiThread(
                        () ->
                                parseAndDisplay(
                                        completeJson
                                )
                );
            }
        }
    }

    // =========================================================
    // PARSE ESP32 JSON
    // =========================================================

    private void parseAndDisplay(
            String raw) {

        try {

            String data =
                    raw
                            .replace("\n", "")
                            .replace("\r", "")
                            .replace("\u0000", "")
                            .trim();

            // =====================================================
// ESP32 BOOT BUTTON - EMERGENCY CANCEL
// =====================================================

            if (data.contains("\"cancel\":true")) {

                if (emergencyCountdown != null) {
                    emergencyCountdown.cancel();
                    emergencyCountdown = null;
                }

                countdownActive = false;

                statusTextView.setText(
                        "✅ EMERGENCY ALERT CANCELLED\n" +
                                "I'm OK — ESP32 BOOT button pressed"
                );

                hrTextView.setTextColor(0xFFFFFFFF);

                return;
            }

            // =====================================================
            // FIND HR
            // =====================================================

            int hrStart =
                    data.indexOf(
                            "\"hr\""
                    );

            // =====================================================
            // FIND LATITUDE
            // =====================================================

            int latStart =
                    data.indexOf(
                            "\"lat\""
                    );

            // =====================================================
            // FIND LONGITUDE
            // =====================================================

            int lonStart =
                    data.indexOf(
                            "\"lon\""
                    );

            // =====================================================
            // CHECK REQUIRED VALUES
            // =====================================================

            if (hrStart == -1
                    ||
                    latStart == -1
                    ||
                    lonStart == -1) {

                statusTextView.setText(
                        "BLE JSON incomplete:\n"
                                + data
                );

                return;
            }

            // =====================================================
            // HEART RATE
            // =====================================================

            int hrColon =
                    data.indexOf(
                            ":",
                            hrStart
                    );

            int hrEnd =
                    data.indexOf(
                            ",",
                            hrColon
                    );

            if (hrColon == -1
                    ||
                    hrEnd == -1) {

                statusTextView.setText(
                        "Heart-rate data incomplete:\n"
                                + data
                );

                return;
            }

            String hrString =
                    data.substring(
                            hrColon + 1,
                            hrEnd
                    ).trim();

            int hr =
                    (int)
                            Double.parseDouble(
                                    hrString
                            );

            // =====================================================
            // LATITUDE
            // =====================================================

            int latColon =
                    data.indexOf(
                            ":",
                            latStart
                    );

            int latEnd =
                    data.indexOf(
                            ",",
                            latColon
                    );

            if (latColon == -1
                    ||
                    latEnd == -1) {

                statusTextView.setText(
                        "Latitude data incomplete:\n"
                                + data
                );

                return;
            }

            String latString =
                    data.substring(
                            latColon + 1,
                            latEnd
                    ).trim();

            double lat =
                    Double.parseDouble(
                            latString
                    );

            // =====================================================
            // LONGITUDE
            // =====================================================

            int lonColon =
                    data.indexOf(
                            ":",
                            lonStart
                    );

            int lonEnd =
                    data.indexOf(
                            "}",
                            lonColon
                    );

            if (lonColon == -1
                    ||
                    lonEnd == -1) {

                statusTextView.setText(
                        "Longitude data incomplete:\n"
                                + data
                );

                return;
            }

            String lonString =
                    data.substring(
                            lonColon + 1,
                            lonEnd
                    ).trim();

            double lon =
                    Double.parseDouble(
                            lonString
                    );

            // =====================================================
            // SAVE LIVE VALUES
            // =====================================================

            latestHeartRate =
                    hr;

            latestLat =
                    lat;

            latestLon =
                    lon;

            // =====================================================
            // DISPLAY VALUES
            // =====================================================

            hrTextView.setText(
                    "Heart Rate: "
                            + hr
                            + " BPM"
            );

            gpsTextView.setText(
                    "Latitude: "
                            + lat
                            +
                            "\nLongitude: "
                            + lon
            );

            // =====================================================
            // GEO-FENCE CHECK
            // =====================================================

            checkGeoFence(
                    lat,
                    lon
            );

            // =====================================================
            // HEART RATE DETECTION
            // =====================================================

            HRAnomalyDetector.AnomalyResult result =
                    anomalyDetector.addReading(
                            hr
                    );

            // =====================================================
            // ABNORMAL HEART RATE
            // =====================================================

            if (result.isAnomaly) {

                hrTextView.setTextColor(
                        0xFFE53935
                );

                statusTextView.setText(
                        "⚠ ABNORMAL HR\n"
                                +
                                result.reason
                );

                startEmergencyCountdown(
                        result.reason
                );

            }

            // =====================================================
            // NORMAL HEART RATE
            // =====================================================

            else {

                hrTextView.setTextColor(
                        0xFFFFFFFF
                );

                /*
                 * checkGeoFence() controls the Safe Zone message.
                 */
            }

        } catch (Exception e) {

            statusTextView.setText(
                    "Data parsing error:\n"
                            +
                            raw
                            +
                            "\n\n"
                            +
                            e.getMessage()
            );
        }
    }

    // =========================================================
    // GEO-FENCE CHECK
    // =========================================================
    /*
     * MOVING SAFE ZONE
     *
     * The current GPS location itself is used as the Safe Zone
     * center.
     *
     * Therefore:
     *
     * Current GPS = Safe Zone center
     * Distance     = approximately 0 meters
     * Status       = SAFE ZONE
     *
     * No automatic SMS is triggered by this module.
     */

    private void checkGeoFence(
            double latitude,
            double longitude) {

        // ---------------------------------------------------------
        // CHECK GPS VALIDITY
        // ---------------------------------------------------------

        if (latitude == 0.0 ||
                longitude == 0.0) {

            statusTextView.setText(
                    "📍 GPS location unavailable"
            );

            return;
        }

        // ---------------------------------------------------------
        // CURRENT LOCATION BECOMES SAFE ZONE CENTER
        // ---------------------------------------------------------

        double safeZoneLatitude =
                latitude;

        double safeZoneLongitude =
                longitude;

        // ---------------------------------------------------------
        // CALCULATE DISTANCE
        // ---------------------------------------------------------

        float[] distance =
                new float[1];

        android.location.Location.distanceBetween(
                safeZoneLatitude,
                safeZoneLongitude,
                latitude,
                longitude,
                distance
        );

        float distanceMeters =
                distance[0];

        // ---------------------------------------------------------
        // ALWAYS SAFE
        // ---------------------------------------------------------

        geoFenceAlertActive =
                false;

        statusTextView.setText(
                "BLE: LIVE DATA RECEIVED\n"
                        +
                        "🟢 SAFE ZONE\n"
                        +
                        "Distance: "
                        +
                        String.format(
                                "%.1f",
                                distanceMeters
                        )
                        +
                        " m"
        );
    }

    // =========================================================
    // EMERGENCY COUNTDOWN
    // =========================================================

    private void startEmergencyCountdown(
            String reason) {

        /*
         * Prevent multiple countdown dialogs
         * from opening repeatedly.
         */

        if (countdownActive) {

            return;
        }

        countdownActive =
                true;

        // =====================================================
        // 5 SECOND COUNTDOWN
        // =====================================================

        final int countdownSeconds =
                5;

        AlertDialog.Builder builder =
                new AlertDialog.Builder(
                        this
                );

        builder.setTitle(
                "⚠ Possible Distress Detected"
        );

        builder.setCancelable(
                false
        );

        String message =
                reason
                        +
                        "\n\nEmergency alert will be sent in "
                        +
                        countdownSeconds
                        +
                        " seconds."
                        +
                        "\n\nTap CANCEL if you are safe.";

        builder.setMessage(
                message
        );

        builder.setNegativeButton(
                "CANCEL — I'M OKAY",
                null
        );

        AlertDialog dialog =
                builder.create();

        // ---------------------------------------------------------
        // CANCEL BUTTON
        // ---------------------------------------------------------

        dialog.setOnShowListener(
                dialogInterface -> {

                    dialog.getButton(
                            AlertDialog.BUTTON_NEGATIVE
                    ).setOnClickListener(
                            view -> {

                                if (emergencyCountdown
                                        != null) {

                                    emergencyCountdown
                                            .cancel();
                                }

                                countdownActive =
                                        false;

                                dialog.dismiss();

                                statusTextView.setText(
                                        "Emergency alert cancelled"
                                );

                                hrTextView.setTextColor(
                                        0xFFFFFFFF
                                );
                            }
                    );
                }
        );

        dialog.show();

        // =====================================================
        // 5 SECOND COUNTDOWN TIMER
        // =====================================================

        emergencyCountdown =
                new CountDownTimer(
                        5000,
                        1000
                ) {

                    @Override
                    public void onTick(
                            long millisUntilFinished) {

                        int secondsLeft =
                                (int)
                                        Math.ceil(
                                                millisUntilFinished
                                                        /
                                                        1000.0
                                        );

                        dialog.setMessage(
                                reason
                                        +
                                        "\n\nEmergency alert will be sent in "
                                        +
                                        secondsLeft
                                        +
                                        " seconds."
                                        +
                                        "\n\nTap CANCEL if you are safe."
                        );
                    }

                    @Override
                    public void onFinish() {

                        countdownActive =
                                false;

                        dialog.dismiss();

                        triggerEmergencyAlert(
                                reason
                        );
                    }

                }.start();
    }

    // =========================================================
    // TRIGGER EMERGENCY
    // =========================================================

    private void triggerEmergencyAlert(
            String reason) {

        statusTextView.setText(
                "🚨 EMERGENCY ALERT TRIGGERED\n"
                        +
                        reason
        );

        hrTextView.setTextColor(
                0xFFE53935
        );

        pendingAlertReason =
                reason;

        // ---------------------------------------------------------
        // CHECK SMS PERMISSION
        // ---------------------------------------------------------

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
        ) !=
                PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.SEND_SMS
                    },
                    REQUEST_SMS
            );

            return;
        }

        sendEmergencySms(
                reason
        );
    }

    // =========================================================
    // SEND EMERGENCY SMS
    // =========================================================

    @SuppressLint("MissingPermission")
    private void sendEmergencySms(
            String reason) {

        // ---------------------------------------------------------
        // GET TRUSTED CONTACTS
        // ---------------------------------------------------------

        if (contactManager == null) {

            contactManager =
                    new ContactManager(this);
        }

        String[] trustedContacts =
                contactManager.getPhoneNumbers();

        // ---------------------------------------------------------
        // NO CONTACTS
        // ---------------------------------------------------------

        if (trustedContacts.length == 0) {

            statusTextView.setText(
                    "🚨 ALERT TRIGGERED\n" +
                            "No trusted contacts added"
            );

            return;
        }

        // ---------------------------------------------------------
        // LOCATION
        // ---------------------------------------------------------

        String locationText;

        if (latestLat != 0.0 &&
                latestLon != 0.0) {

            locationText =
                    "Location: https://maps.google.com/?q="
                            + latestLat
                            + ","
                            + latestLon;

        } else {

            locationText =
                    "Location: GPS coordinates unavailable";
        }

        // ---------------------------------------------------------
        // SMS MESSAGE
        // ---------------------------------------------------------

        String message =
                "GEOSAFE EMERGENCY ALERT 🚨\n\n"
                        + "Possible distress detected.\n"
                        + "Reason: " + reason + "\n"
                        + "Heart Rate: "
                        + latestHeartRate
                        + " BPM\n"
                        + locationText
                        + "\n\nPlease contact the user immediately.";

        try {

            SmsManager smsManager =
                    SmsManager.getDefault();

            boolean sentToAtLeastOne =
                    false;

            int validContactCount =
                    0;

            // -----------------------------------------------------
            // SEND TO ALL SAVED TRUSTED CONTACTS
            // -----------------------------------------------------

            for (String number :
                    trustedContacts) {

                if (number == null ||
                        number.trim().isEmpty()) {

                    continue;
                }

                String cleanedNumber =
                        number.trim()
                                .replace(" ", "")
                                .replace("-", "")
                                .replace("(", "")
                                .replace(")", "");

                if (cleanedNumber.isEmpty()) {

                    continue;
                }

                validContactCount++;

                try {

                    java.util.ArrayList<String> parts =
                            smsManager.divideMessage(
                                    message
                            );

                    if (parts.size() > 1) {

                        smsManager.sendMultipartTextMessage(
                                cleanedNumber,
                                null,
                                parts,
                                null,
                                null
                        );

                    } else {

                        smsManager.sendTextMessage(
                                cleanedNumber,
                                null,
                                message,
                                null,
                                null
                        );
                    }

                    sentToAtLeastOne =
                            true;

                } catch (Exception e) {

                    String errorMessage =
                            e.getMessage();

                    statusTextView.setText(
                            "SMS failed for "
                                    + cleanedNumber
                                    + "\n"
                                    + errorMessage
                    );
                }
            }

            // -----------------------------------------------------
            // RESULT
            // -----------------------------------------------------

            if (sentToAtLeastOne) {

                statusTextView.setText(
                        "🚨 EMERGENCY SMS REQUESTED\n"
                                + "Sent to "
                                + validContactCount
                                + " trusted contact(s)\n"
                                + "HR: "
                                + latestHeartRate
                                + " BPM"
                );

            } else {

                statusTextView.setText(
                        "🚨 ALERT TRIGGERED\n"
                                + "No valid trusted contact number"
                );
            }

        } catch (Exception e) {

            statusTextView.setText(
                    "SMS error:\n"
                            + e.getMessage()
            );
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {

        super.onDestroy();

        try {

            if (emergencyCountdown
                    != null) {

                emergencyCountdown.cancel();
            }

            if (bluetoothAdapter != null
                    &&
                    bluetoothAdapter
                            .getBluetoothLeScanner()
                            != null) {

                bluetoothAdapter
                        .getBluetoothLeScanner()
                        .stopScan(
                                scanCallback
                        );
            }

            if (bluetoothGatt != null) {

                bluetoothGatt.disconnect();

                bluetoothGatt.close();

                bluetoothGatt =
                        null;
            }

        } catch (Exception ignored) {
        }
    }
}