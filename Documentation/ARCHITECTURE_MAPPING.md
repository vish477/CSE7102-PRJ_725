# GeoSafe Architecture Mapping

This package follows the architecture shown in the Review-1 diagram:

MAX30100/MAX30102 sensor + NEO-6M GPS
        ↓
ESP32 controller
        ↓
Local processing / alert trigger logic
        ↓
Smartphone gateway (GeoSafe Android app via BLE)
        ↓
Internet / optional server
        ↓
Application server
        ↓
Database
        ↓
Notification / emergency workflow
        ↓
Trusted contact / Guardian dashboard

## Implemented in this package
- Real MAX30100 firmware path
- NEO-6M latitude/longitude path
- ESP32 BLE GATT notifications
- Android BLE scanning and foreground monitoring service
- Sustained abnormal HR detection (3 readings >= 150 BPM)
- 10-second cancellation period
- Automatic SMS from Android to saved trusted contact
- GPS coordinates in emergency message
- Trusted contacts
- Risk-zone configuration screen
- Incident history screen
- Prototype server and guardian dashboard

## Not claimed as medical diagnosis
Heart rate is used as a possible safety indicator only. Exercise, anxiety, movement and sensor error can also raise readings.

## Android permission note
Automatic SMS requires the user to grant SMS permission. Modern Android and Play Store distribution impose restrictions on SMS permissions; this project is intended as a college prototype/test build.
