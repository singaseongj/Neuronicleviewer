package com.singaseongapp.neuronicleviewer

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var isReading = false

    // UI Elements
    private var statusText: TextView? = null
    private var ch1TextView: TextView? = null
    private var ch2TextView: TextView? = null

    // Launcher for permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            startBluetoothConnection()
        } else {
            statusText?.text = "Status: Permissions Denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Enable Edge-to-Edge BEFORE setContentView
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 2. Link UI elements with safety null-checks
        statusText = findViewById(R.id.statusText)
        ch1TextView = findViewById(R.id.ch1Text)
        ch2TextView = findViewById(R.id.ch2Text)

        // 3. Handle System Bars (using your xml id "main")
        val mainView = findViewById<android.view.View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        requestPermissionLauncher.launch(permissions)
    }

    private fun startBluetoothConnection() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            statusText?.text = "Status: Enable Bluetooth"
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return
        }

        val device = adapter.bondedDevices.find { it.name?.contains("neuroNicle", ignoreCase = true) == true }

        if (device != null) {
            statusText?.text = "Status: Connecting..."
            connectToDevice(device)
        } else {
            statusText?.text = "Status: neuroNicle not paired"
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Thread {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    bluetoothSocket?.connect()
                    inputStream = bluetoothSocket?.inputStream

                    runOnUiThread { statusText?.text = "Status: Connected!" }
                    readData()
                }
            } catch (e: IOException) {
                runOnUiThread { statusText?.text = "Status: Failed to Connect" }
            }
        }.start()
    }

    private fun readData() {
        isReading = true
        val buffer = ByteArray(1024)
        var lastByte = -1
        val packet = IntArray(15)
        var packetIdx = 0
        var isSync = false

        while (isReading) {
            try {
                val bytesRead = inputStream?.read(buffer) ?: -1
                for (i in 0 until bytesRead) {
                    val currentByte = buffer[i].toInt() and 0xFF
                    if (lastByte == 255 && currentByte == 254) {
                        isSync = true
                        packetIdx = 0
                    } else if (isSync) {
                        packet[packetIdx++] = currentByte
                        if (packetIdx >= 10) {
                            val ch1 = (packet[1] shl 7) or (packet[2] and 0x7F)
                            val ch2 = (packet[3] shl 7) or (packet[4] and 0x7F)
                            runOnUiThread {
                                ch1TextView?.text = "CH1: $ch1"
                                ch2TextView?.text = "CH2: $ch2"
                            }
                            isSync = false
                        }
                    }
                    lastByte = currentByte
                }
            } catch (e: IOException) {
                isReading = false
                runOnUiThread { statusText?.text = "Status: Disconnected" }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isReading = false
        try { bluetoothSocket?.close() } catch (e: Exception) {}
    }
}
