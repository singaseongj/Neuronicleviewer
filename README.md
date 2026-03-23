# Neuronicle E2 Viewer

Neuronicle E2 Viewer is an Android app for connecting to a paired neuroNicle/Neuronicle EEG device over classic Bluetooth, decoding its incoming data stream, filtering the raw signal, and showing the results in a simple live monitoring screen.

## What the app does

- Connects to a paired Bluetooth EEG device using the Serial Port Profile (SPP) UUID.
- Reads the device's byte stream and synchronizes on packet headers.
- Decodes two EEG channels from each packet.
- Applies a small real-time signal-processing pipeline to make the graph easier to read.
- Draws both EEG channels on a scrolling-style waveform view.
- Shows device state such as:
  - battery percentage
  - low-battery warning
  - band worn status
  - clip electrode status
  - electrode contact state for CH1, CH2, and REF

## Main user flow

1. Launch the app.
2. Tap **Connect**.
3. Grant Bluetooth permissions if Android asks.
4. If Bluetooth is off, allow Android to enable it.
5. The app tries to auto-select a paired device whose name contains `neuroNicle`.
6. If no matching device is found, the app shows a chooser for paired Bluetooth devices.
7. Once connected, the screen updates continuously with filtered channel values, battery/status fields, and the live graph.
8. Tap **App settings** to open the Android system settings page for the app if permissions need to be changed manually.

## Screen overview

The main screen contains:

- a connection status label
- the selected device name and address
- **Connect** and **App settings** buttons
- a custom EEG graph area
- numeric CH1 and CH2 readings in microvolts
- a battery percentage label and progress bar
- text indicators for electrode contact, low battery, band worn state, and clip electrode health

## How the data pipeline works

### 1. Bluetooth connection

`MainActivity` handles runtime permissions, Bluetooth adapter checks, paired-device selection, socket creation, and stream reading. The app connects using the standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.

### 2. Packet decoding

`PacketParser` looks for the sync bytes `0xFF 0xFE`, then reads a 9-byte payload. From that packet it extracts:

- channel 1 sample
- channel 2 sample
- packet counter
- cyclic status data
- electrode contact bits

It also merges multi-packet status information into a single `DeviceStatus` model so the UI can show battery and sensor state alongside the EEG readings.

### 3. Signal filtering

`SignalFilterPipeline` processes each channel independently with:

- a 1 Hz high-pass filter to reduce drift
- a 40 Hz low-pass filter to limit higher-frequency noise
- a 60 Hz notch filter to reduce mains hum
- a moving average smoother

This helps stabilize the live display without changing the overall structure of the incoming signal too aggressively.

### 4. Visualization

`EegGraphView` stores a rolling window of up to 500 samples per channel and redraws a dual-channel waveform on every update:

- CH1 is drawn in green
- CH2 is drawn in blue
- the graph includes a center axis and grid lines
- vertical scaling adapts to the current signal amplitude

## Project structure

```text
.
├── app/
│   ├── src/main/java/com/singaseongapp/neuronicleviewer/
│   │   ├── MainActivity.kt        # Bluetooth flow and UI updates
│   │   ├── PacketParser.kt        # Stream synchronization and packet decoding
│   │   ├── SignalFilter.kt        # Real-time filtering pipeline
│   │   └── EegGraphView.kt        # Custom waveform renderer
│   └── src/main/res/
│       ├── layout/activity_main.xml
│       └── values/strings.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## Requirements

- Android device running Android 7.0+ (minSdk 24)
- A paired Bluetooth EEG device compatible with the packet format this app expects
- Bluetooth enabled on the phone or tablet
- Required Bluetooth permissions granted by the user

## Building the app

### Android Studio

1. Open the project in Android Studio.
2. Let Gradle sync.
3. Build or run the `app` module on a device.

### Command line

On Linux/macOS:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

## Running tests

```bash
./gradlew test
```

The repository includes unit tests for the app and the signal filter pipeline.

## Notes and limitations

- The app expects a very specific packet format and status-byte layout.
- Device auto-selection only prefers already paired devices whose name contains `neuroNicle`.
- The graph shows filtered live values for monitoring; it is not presented as a clinical analysis tool.
- Connection handling is intentionally simple and built around a single Bluetooth socket and input stream.

## Future improvement ideas

- Add data recording/export.
- Add reconnect logic and clearer error reporting.
- Show timestamps and sampling-rate diagnostics.
- Add calibration or gain controls for display scaling.
- Support more device models or packet variants.
