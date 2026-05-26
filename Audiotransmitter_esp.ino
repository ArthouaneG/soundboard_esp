#include "AudioTools.h"           // Must be included BEFORE BluetoothA2DPSink
#include "BluetoothA2DPSink.h"

using namespace audio_tools;

BluetoothA2DPSink a2dp_sink;
I2SStream i2s;

// I2S pins for UDA1334A
#define I2S_BCLK 26
#define I2S_WS   25
#define I2S_DOUT 22

void connection_state_changed(esp_a2d_connection_state_t state, void *ptr) {
  if (state == ESP_A2D_CONNECTION_STATE_CONNECTED) {
    Serial.println("CONNECTED - audio playing to UDA1334A");
  } else if (state == ESP_A2D_CONNECTION_STATE_DISCONNECTED) {
    Serial.println("DISCONNECTED");
  } else {
    Serial.println("CONNECTING...");
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  // Disable watchdog
  esp_task_wdt_deinit();

  Serial.println("\n=== ESP32 Bluetooth Audio Receiver ===");

  // Configure I2S output using AudioTools
  auto cfg = i2s.defaultConfig(TX_MODE);
  cfg.pin_bck  = I2S_BCLK;
  cfg.pin_ws   = I2S_WS;
  cfg.pin_data = I2S_DOUT;
  cfg.sample_rate   = 44100;
  cfg.bits_per_sample = 16;
  cfg.channels = 2;
  i2s.begin(cfg);
  Serial.println("I2S configured - BCLK:26, WS:25, DIN:22");

  // Route A2DP audio to I2S stream
  a2dp_sink.set_output(i2s);
  a2dp_sink.set_on_connection_state_changed(connection_state_changed);
  a2dp_sink.set_auto_reconnect(false);
  a2dp_sink.start("ESP_Speaker");

  Serial.println("Bluetooth started - search for 'ESP_Speaker'");
}

void loop() {
  static unsigned long last_print = 0;
  if (millis() - last_print > 5000) {
    Serial.println("Waiting for connection...");
    last_print = millis();
  }
  delay(100);
}