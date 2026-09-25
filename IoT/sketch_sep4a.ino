#include <Wire.h>

#include "MAX30105.h"
#include "heartRate.h"

#include <TinyGPSPlus.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>


// ============================================================
// GEOSAFE BLE UUIDs
// ============================================================

#define SERVICE_UUID        "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID "6e400003-b5a3-f393-e0a9-e50e24dcca9e"


// ============================================================
// I2C - MAX30102
// ============================================================

#define I2C_SDA 21
#define I2C_SCL 22


// ============================================================
// GPS - NEO-6M
// ============================================================

#define GPS_RX 16
#define GPS_TX 17


// ============================================================
// SENSOR OBJECTS
// ============================================================

MAX30105 maxSensor;

TinyGPSPlus gps;

HardwareSerial GPSserial(2);


// ============================================================
// BLE OBJECTS
// ============================================================

BLECharacteristic *dataCharacteristic;

bool deviceConnected = false;


// ============================================================
// HEART RATE VARIABLES
// ============================================================

const byte RATE_SIZE = 8;

byte rates[RATE_SIZE];

byte rateSpot = 0;

long lastBeat = 0;

float currentBPM = 0;

int averageBPM = 0;


// ============================================================
// TIMERS
// ============================================================

unsigned long lastBLESend = 0;

unsigned long lastSerialPrint = 0;


// ============================================================
// BLE CALLBACKS
// ============================================================

class MyServerCallbacks : public BLEServerCallbacks {

  void onConnect(BLEServer *pServer) {

    deviceConnected = true;

    Serial.println();
    Serial.println("======================================");
    Serial.println("BLE: PHONE CONNECTED");
    Serial.println("======================================");
  }


  void onDisconnect(BLEServer *pServer) {

    deviceConnected = false;

    Serial.println();
    Serial.println("BLE: PHONE DISCONNECTED");

    delay(500);

    pServer->startAdvertising();

    Serial.println("BLE: ADVERTISING AGAIN");
    Serial.println("Device: GeoSafe-ESP32");
  }
};


// ============================================================
// SETUP
// ============================================================

void setup() {

  Serial.begin(115200);
  pinMode(BOOT_BUTTON, INPUT_PULLUP);

  delay(2000);


  // ----------------------------------------------------------
  // HEADER
  // ----------------------------------------------------------

  Serial.println();
  Serial.println("==============================================");
  Serial.println("             GEOSAFE SYSTEM");
  Serial.println("==============================================");
  Serial.println("ESP32 + MAX30102 + NEO-6M + BLE");
  Serial.println("==============================================");
  Serial.println();


  // ----------------------------------------------------------
  // I2C START
  // ----------------------------------------------------------

  Wire.begin(
    I2C_SDA,
    I2C_SCL
  );

  Wire.setClock(100000);

  Serial.println("I2C started");
  Serial.println("SDA = GPIO21");
  Serial.println("SCL = GPIO22");
  Serial.println();


  // ----------------------------------------------------------
  // GPS START
  // ----------------------------------------------------------

  GPSserial.begin(
    9600,
    SERIAL_8N1,
    GPS_RX,
    GPS_TX
  );

  Serial.println("GPS started");
  Serial.println("GPS TX -> ESP32 GPIO16");
  Serial.println("GPS RX -> ESP32 GPIO17");
  Serial.println();


  // ----------------------------------------------------------
  // MAX30102 START
  // ----------------------------------------------------------

  Serial.println("Starting MAX30102...");


  if (!maxSensor.begin(
        Wire,
        I2C_SPEED_STANDARD
      )) {

    Serial.println();
    Serial.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    Serial.println("ERROR: MAX30102 NOT FOUND!");
    Serial.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    Serial.println();

    while (1) {

      delay(1000);
    }
  }


  Serial.println("MAX30102 FOUND!");


  // ----------------------------------------------------------
  // MAX30102 CONFIGURATION
  // ----------------------------------------------------------

  maxSensor.setup();


  // Red LED
  maxSensor.setPulseAmplitudeRed(0x0A);


  // IR LED
  maxSensor.setPulseAmplitudeIR(0x24);


  // Green LED OFF
  maxSensor.setPulseAmplitudeGreen(0);


  Serial.println("MAX30102 configured");
  Serial.println();


  // ----------------------------------------------------------
  // BLE START
  // ----------------------------------------------------------

  Serial.println("Starting BLE...");


  BLEDevice::init(
    "GeoSafe-ESP32"
  );


  // Request larger MTU
  BLEDevice::setMTU(247);


  // Create BLE server
  BLEServer *server =
    BLEDevice::createServer();


  server->setCallbacks(
    new MyServerCallbacks()
  );


  // ----------------------------------------------------------
  // CREATE BLE SERVICE
  // ----------------------------------------------------------

  BLEService *service =
    server->createService(
      SERVICE_UUID
    );


  // ----------------------------------------------------------
  // CREATE BLE CHARACTERISTIC
  // ----------------------------------------------------------

  dataCharacteristic =
    service->createCharacteristic(

      CHARACTERISTIC_UUID,

      BLECharacteristic::PROPERTY_READ |
      BLECharacteristic::PROPERTY_NOTIFY
    );


  // ----------------------------------------------------------
  // ADD NOTIFICATION DESCRIPTOR
  // ----------------------------------------------------------

  dataCharacteristic->addDescriptor(
    new BLE2902()
  );


  // ----------------------------------------------------------
  // INITIAL VALUE
  // ----------------------------------------------------------

  dataCharacteristic->setValue(
    "GeoSafe BLE Ready"
  );


  // ----------------------------------------------------------
  // START BLE SERVICE
  // ----------------------------------------------------------

  service->start();


  // ----------------------------------------------------------
  // BLE ADVERTISING
  // ----------------------------------------------------------

  BLEAdvertising *advertising =
    BLEDevice::getAdvertising();


  advertising->addServiceUUID(
    SERVICE_UUID
  );


  advertising->setScanResponse(
    true
  );


  advertising->setMinPreferred(
    0x06
  );


  advertising->setMinPreferred(
    0x12
  );


  BLEDevice::startAdvertising();


  // ----------------------------------------------------------
  // READY
  // ----------------------------------------------------------

  Serial.println();
  Serial.println("==============================================");
  Serial.println("             GEOSAFE BLE READY");
  Serial.println("==============================================");
  Serial.println("Device Name:");
  Serial.println("GeoSafe-ESP32");
  Serial.println();
  Serial.println("Service:");
  Serial.println(SERVICE_UUID);
  Serial.println();
  Serial.println("Characteristic:");
  Serial.println(CHARACTERISTIC_UUID);
  Serial.println();
  Serial.println("Waiting for Android phone...");
  Serial.println("==============================================");
  Serial.println();
}


// ============================================================
// LOOP
// ============================================================

void loop() {

  // Check ESP32 BOOT button
if (digitalRead(BOOT_BUTTON) == LOW) {
    if (deviceConnected) {
        dataCharacteristic->setValue("{\"cancel\":true}");
        dataCharacteristic->notify();

        Serial.println("======================================");
        Serial.println("BOOT BUTTON PRESSED");
        Serial.println("EMERGENCY CANCEL SENT");
        Serial.println("======================================");

        delay(500);
    }
}


  // ==========================================================
  // 1. READ GPS DATA CONTINUOUSLY
  // ==========================================================

  while (GPSserial.available() > 0) {

    gps.encode(
      GPSserial.read()
    );
  }


  // ==========================================================
  // 2. READ MAX30102 IR VALUE
  // ==========================================================

  long irValue =
    maxSensor.getIR();


  // ==========================================================
  // 3. DETECT HEART BEAT
  // ==========================================================

  if (checkForBeat(irValue)) {


    long delta =
      millis() - lastBeat;


    lastBeat =
      millis();


    if (delta > 0) {


      currentBPM =
        60.0 /
        (delta / 1000.0);


      // Accept realistic prototype range
      if (currentBPM >= 40 &&
          currentBPM <= 200) {


        rates[rateSpot] =
          (byte)currentBPM;


        rateSpot++;


        rateSpot %=
          RATE_SIZE;


        // ----------------------------------------------
        // Calculate average BPM
        // ----------------------------------------------

        averageBPM = 0;


        for (byte i = 0;
             i < RATE_SIZE;
             i++) {

          averageBPM +=
            rates[i];
        }


        averageBPM /=
          RATE_SIZE;
      }
    }
  }


  // ==========================================================
  // 4. SEND BLE DATA EVERY 2 SECONDS
  // ==========================================================

  if (deviceConnected &&
      millis() - lastBLESend >= 2000) {


    lastBLESend =
      millis();


    // --------------------------------------------------------
    // Determine heart rate
    // --------------------------------------------------------

    int hrToSend =
      averageBPM;


    // If BPM has not been calculated yet
    if (hrToSend <= 0) {

      hrToSend = 0;
    }


    // --------------------------------------------------------
    // Determine GPS
    // --------------------------------------------------------

    double latitude = 0.0;

    double longitude = 0.0;


    if (gps.location.isValid()) {

      latitude =
        gps.location.lat();

      longitude =
        gps.location.lng();
    }


    // --------------------------------------------------------
    // Create JSON
    // --------------------------------------------------------

    char jsonData[160];


    snprintf(
      jsonData,
      sizeof(jsonData),

      "{\"hr\":%d,\"lat\":%.6f,\"lon\":%.6f}",

      hrToSend,

      latitude,

      longitude
    );


    // --------------------------------------------------------
    // SEND BLE NOTIFICATION
    // --------------------------------------------------------

    dataCharacteristic->setValue(
      jsonData
    );


    dataCharacteristic->notify();


    // --------------------------------------------------------
    // SERIAL MONITOR
    // --------------------------------------------------------

    Serial.println();
    Serial.println("--------------------------------------");

    Serial.print(
      "BLE JSON SENT: "
    );

    Serial.println(
      jsonData
    );

    Serial.println("--------------------------------------");
  }


  // ==========================================================
  // 5. SERIAL MONITOR STATUS EVERY 1 SECOND
  // ==========================================================

  if (millis() - lastSerialPrint >= 1000) {


    lastSerialPrint =
      millis();


    Serial.println();


    Serial.println(
      "========== GEOSAFE STATUS =========="
    );


    // --------------------------------------------------------
    // HEART RATE
    // --------------------------------------------------------

    Serial.print(
      "IR Value: "
    );

    Serial.println(
      irValue
    );


    if (irValue < 50000) {

      Serial.println(
        "Heart Rate: No finger detected"
      );

    } else {

      if (averageBPM > 0) {

        Serial.print(
          "Heart Rate: "
        );

        Serial.print(
          averageBPM
        );

        Serial.println(
          " BPM"
        );

      } else {

        Serial.println(
          "Heart Rate: Calculating..."
        );
      }
    }


    // --------------------------------------------------------
    // GPS
    // --------------------------------------------------------

    if (gps.location.isValid()) {


      Serial.println(
        "GPS: FIXED"
      );


      Serial.print(
        "Latitude: "
      );

      Serial.println(
        gps.location.lat(),
        6
      );


      Serial.print(
        "Longitude: "
      );

      Serial.println(
        gps.location.lng(),
        6
      );


      Serial.print(
        "Satellites: "
      );

      Serial.println(
        gps.satellites.value()
      );


    } else {


      Serial.println(
        "GPS: Searching..."
      );


      Serial.print(
        "Satellites: "
      );


      if (gps.satellites.isValid()) {

        Serial.println(
          gps.satellites.value()
        );

      } else {

        Serial.println(
          "Searching..."
        );
      }
    }


    // --------------------------------------------------------
    // BLE STATUS
    // --------------------------------------------------------

    if (deviceConnected) {

      Serial.println(
        "BLE: PHONE CONNECTED"
      );

    } else {

      Serial.println(
        "BLE: WAITING FOR PHONE"
      );
    }


    Serial.println(
      "===================================="
    );
  }


  // Small delay
  delay(5);
}