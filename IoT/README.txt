GEOSAFE ESP32 HARDWARE SETUP

1) MAX30100
   VIN -> 3V3
   GND -> GND
   SDA -> GPIO21
   SCL -> GPIO22
   INT -> not required

2) NEO-6M GPS
   GPS TX -> ESP32 GPIO16 (RX2)
   GPS RX -> ESP32 GPIO17 (TX2)
   GND -> GND
   VCC -> use the voltage recommended for your GPS breakout

3) Arduino libraries
   - MAX30100lib / MAX30100_PulseOximeter
   - TinyGPSPlus
   ESP32 BLE is included with the ESP32 Arduino core.

4) Android
   Install GeoSafe app.
   Save a trusted contact.
   Allow Bluetooth, Nearby devices, Notifications, Location and SMS permissions.
   Open Device Connection -> Connect / Scan ESP32.
   The phone looks for the BLE device named GeoSafe-ESP32.

5) Automatic alert
   Three consecutive readings >= 150 BPM start a 10-second cancellation period.
   If not cancelled, the Android phone sends an SMS to the saved trusted contact.
   The SMS contains the GPS coordinates supplied by the ESP32.

NOTE:
   Heart rate is a safety indicator, not a medical diagnosis.
   MAX30100 readings can be affected by finger placement, motion and sensor quality.
