/*
  GeoSafe ESP32 firmware
  Hardware:
    MAX30100 -> ESP32 I2C (SDA GPIO21, SCL GPIO22)
    NEO-6M  -> ESP32 UART2 (RX2 GPIO16, TX2 GPIO17)

  BLE:
    Device name: GeoSafe-ESP32
    Service UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e
    Notify characteristic UUID: 6e400003-b5a3-f393-e0a9-e50e24dcca9e

  The phone receives JSON:
    {"hr":82,"lat":12.345678,"lon":77.123456}

  Install Arduino library:
    MAX30100lib (MAX30100_PulseOximeter.h)
  ESP32 uses built-in BLE library.
*/

#include <Wire.h>
#include <MAX30100_PulseOximeter.h>
#include <TinyGPSPlus.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SDA_PIN 21
#define SCL_PIN 22
#define GPS_RX 16
#define GPS_TX 17

PulseOximeter pox;
TinyGPSPlus gps;
HardwareSerial GPS(2);
BLECharacteristic *notifyChar = nullptr;
bool deviceConnected = false;
uint32_t lastSend = 0;
float lastLat = 0.0, lastLon = 0.0;

#define SERVICE_UUID "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHAR_UUID    "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*) override { deviceConnected = true; }
  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    server->getAdvertising()->start();
  }
};

void onBeatDetected() {
  Serial.println("Beat!");
}

void setup() {
  Serial.begin(115200);
  Wire.begin(SDA_PIN, SCL_PIN);
  GPS.begin(9600, SERIAL_8N1, GPS_RX, GPS_TX);

  Serial.println("Starting MAX30100...");
  if (!pox.begin()) {
    Serial.println("MAX30100 not found. Check VCC/GND/SDA/SCL.");
    while (true) delay(1000);
  }
  pox.setIRLedCurrent(MAX30100_LED_CURR_7_6MA);
  pox.setOnBeatDetectedCallback(onBeatDetected);

  BLEDevice::init("GeoSafe-ESP32");
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  BLEService *service = server->createService(SERVICE_UUID);
  notifyChar = service->createCharacteristic(CHAR_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  notifyChar->addDescriptor(new BLE2902());
  service->start();
  server->getAdvertising()->start();

  Serial.println("GeoSafe ESP32 ready. Waiting for phone...");
}

void loop() {
  pox.update();

  while (GPS.available()) gps.encode(GPS.read());
  if (gps.location.isValid()) {
    lastLat = gps.location.lat();
    lastLon = gps.location.lng();
  }

  if (millis() - lastSend >= 1000) {
    lastSend = millis();
    float bpm = pox.getHeartRate();
    if (bpm > 30 && bpm < 220) {
      String json = "{\"hr\":" + String((int)round(bpm))
                  + ",\"lat\":" + String(lastLat, 6)
                  + ",\"lon\":" + String(lastLon, 6) + "}";
      Serial.println(json);
      if (deviceConnected && notifyChar) {
        notifyChar->setValue(json.c_str());
        notifyChar->notify();
      }
    }
  }
}
