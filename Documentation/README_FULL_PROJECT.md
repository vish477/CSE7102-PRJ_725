# GeoSafe - Full Architecture Prototype

This ZIP contains:
1. Android Studio GeoSafe app
2. ESP32 + MAX30100 + NEO-6M firmware
3. BLE communication between ESP32 and Android
4. Automatic abnormal-heart-rate emergency SMS workflow
5. Trusted contacts
6. Geo-fence zone configuration
7. Incident history
8. Prototype application server + database file + guardian web dashboard

Core data flow:
MAX30100 + NEO-6M -> ESP32 -> BLE -> Android -> alert/GPS/SMS -> optional server -> guardian dashboard.

The Android app is a college prototype. Real emergency deployment requires secure backend authentication, robust location handling, permissions review, testing, and compliance with platform/legal requirements.
