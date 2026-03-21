package com.singaseongapp.neuronicleviewer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    @Volatile private var isReading = false

    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var ch1TextView: TextView
    private lateinit var ch2TextView: TextView
    private lateinit var connectButton: Button
    private lateinit var settingsButton: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val deniedPermissions = results.filterValues { granted -> !granted }.keys
        if (deniedPermissions.isEmpty()) {
            updateStatus(getString(R.string.status_permissions_granted))
            startBluetoothConnection()
        } else {
            updateStatus(getString(R.string.status_permissions_denied))
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startBluetoothConnection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        deviceText = findViewById(R.id.deviceText)
        ch1TextView = findViewById(R.id.ch1Text)
        ch2TextView = findViewById(R.id.ch2Text)
        connectButton = findViewById(R.id.connectButton)
        settingsButton = findViewById(R.id.settingsButton)

        val mainView = findViewById<android.view.View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        connectButton.setOnClickListener { ensureReadyAndConnect() }
        settingsButton.setOnClickListener { openAppSettings() }

        updateStatus(getString(R.string.status_idle))
        updateSelectedDevice(null)
        updateChannelReadings(PacketParser.ChannelReading(0, 0))

        ensureReadyAndConnect()
    }

    private fun ensureReadyAndConnect() {
        if (!hasRequiredPermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }
        startBluetoothConnection()
    }

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        else -> arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun hasRequiredPermissions(): Boolean = requiredPermissions().all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothConnection() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        val adapter = bluetoothAdapter

        if (adapter == null) {
            updateStatus(getString(R.string.status_bluetooth_unavailable))
            return
        }

        if (!adapter.isEnabled) {
            updateStatus(getString(R.string.status_enable_bluetooth))
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            updateStatus(getString(R.string.status_permission_missing))
            return
        }

        val bondedDevices = adapter.bondedDevices.sortedBy { it.name ?: it.address }
        if (bondedDevices.isEmpty()) {
            updateStatus(getString(R.string.status_no_paired_device))
            return
        }

        val preferredDevice = bondedDevices.singleOrNull { it.name?.contains("neuroNicle", ignoreCase = true) == true }
        if (preferredDevice != null) {
            connectToDevice(preferredDevice)
        } else {
            showDeviceChooser(bondedDevices)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDeviceChooser(devices: List<BluetoothDevice>) {
        val labels = devices.map { device ->
            val name = device.name ?: getString(R.string.unknown_device)
            getString(R.string.device_label, name, device.address)
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_device)
            .setItems(labels) { _, which ->
                connectToDevice(devices[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        updateSelectedDevice(device)
        updateStatus(getString(R.string.status_connecting))

        Thread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    runOnUiThread { updateStatus(getString(R.string.status_permission_missing)) }
                    return@Thread
                }

                bluetoothAdapter?.cancelDiscovery()
                closeConnection()

                bluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                bluetoothSocket?.connect()
                inputStream = bluetoothSocket?.inputStream

                runOnUiThread { updateStatus(getString(R.string.status_connected)) }
                readData()
            } catch (_: SecurityException) {
                runOnUiThread { updateStatus(getString(R.string.status_permission_missing)) }
            } catch (_: IOException) {
                closeConnection()
                runOnUiThread { updateStatus(getString(R.string.status_failed_connect)) }
            }
        }.start()
    }

    private fun readData() {
        isReading = true
        val buffer = ByteArray(1024)
        val decoder = PacketParser.StreamDecoder()

        while (isReading) {
            try {
                val bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead <= 0) {
                    throw IOException("Bluetooth stream closed")
                }

                val readings = decoder.consume(buffer, bytesRead)
                readings.lastOrNull()?.let { reading ->
                    runOnUiThread { updateChannelReadings(reading) }
                }
            } catch (_: IOException) {
                isReading = false
                closeConnection()
                runOnUiThread { updateStatus(getString(R.string.status_disconnected)) }
            }
        }
    }

    private fun updateChannelReadings(reading: PacketParser.ChannelReading) {
        ch1TextView.text = getString(R.string.channel_1_value, reading.ch1)
        ch2TextView.text = getString(R.string.channel_2_value, reading.ch2)
    }

    private fun updateSelectedDevice(device: BluetoothDevice?) {
        val label = if (device == null) {
            getString(R.string.device_none)
        } else {
            getString(
                R.string.device_label,
                device.name ?: getString(R.string.unknown_device),
                device.address
            )
        }
        deviceText.text = label
    }

    private fun updateStatus(message: String) {
        statusText.text = message
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    private fun closeConnection() {
        isReading = false
        try {
            inputStream?.close()
        } catch (_: IOException) {
        }
        inputStream = null

        try {
            bluetoothSocket?.close()
        } catch (_: IOException) {
        }
        bluetoothSocket = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeConnection()
    }
}
