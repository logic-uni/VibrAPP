package com.example.success

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _receivedData = MutableStateFlow<List<AccelerometerData>>(emptyList())
    val receivedData: StateFlow<List<AccelerometerData>> = _receivedData

    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var bluetoothSocket: BluetoothSocket? = null

    private var job: Job? = null

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                // You can check device name or MAC address here
                Log.d("Bluetooth", "Device found: ${device?.name}")
            }
        }
    }

    init {
        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(bluetoothReceiver, filter)
    }

    fun startDiscovery() {
        // Check if bluetoothAdapter is null before checking for permissions
        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Bluetooth not supported")
            return
        }

        // Check for permissions before starting discovery
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("Bluetooth", "Bluetooth scan permission not granted")
            return
        }

        // Check if discovery is already in progress and cancel it if needed
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        bluetoothAdapter.startDiscovery()
    }

    fun connectToDevice(address: String) {
        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Bluetooth not supported")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("Bluetooth", "Bluetooth connect permission not granted")
            return
        }

        val device = bluetoothAdapter.getRemoteDevice(address)

        if (device == null) {
            Log.e("Bluetooth", "Device not found")
            return
        }

        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    fun disconnect() {
        _isConnected.value = false
        connectedThread?.cancel()
        connectThread?.cancel()
        job?.cancel()
    }

    fun sendData(message: String) {
        if (_isConnected.value) {
            connectedThread?.write(message.toByteArray())
        } else {
            Log.e("Bluetooth", "Not connected to any device")
        }
    }

    fun cleanup() {
        context.unregisterReceiver(bluetoothReceiver)
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private var shouldProceed: Boolean = true
        init {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("Bluetooth", "Bluetooth connect permission not granted")
                shouldProceed = false
            }

            if(shouldProceed){
                var tmp: BluetoothSocket? = null
                try {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID)
                } catch (e: IOException) {
                    Log.e("Bluetooth", "Socket's create() method failed", e)
                }
                bluetoothSocket = tmp
            }
            else{
                bluetoothSocket = null
            }
        }

        override fun run() {
            if (!shouldProceed) {
                return
            }

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("Bluetooth", "Bluetooth scan permission not granted")
                return
            }
            bluetoothAdapter?.cancelDiscovery()

            try {
                bluetoothSocket?.connect()
            } catch (connectException: IOException) {
                try {
                    bluetoothSocket?.close()
                } catch (closeException: IOException) {
                    Log.e("Bluetooth", "Could not close the client socket", closeException)
                }
                return
            }
            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            manageMyConnectedSocket(bluetoothSocket)
        }

        fun cancel() {
            try {
                bluetoothSocket?.close()
            } catch (e: IOException) {
                Log.e("Bluetooth", "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket?) : Thread() {
        private val mmInStream: InputStream? = mmSocket?.inputStream
        private val mmOutStream: OutputStream? = mmSocket?.outputStream

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                try {
                    bytes = mmInStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        // Process the received data
                        val receivedString = String(buffer, 0, bytes)
                        val data = parseData(receivedString)
                        addData(data)
                        Log.d("Bluetooth", "Received: $receivedString")
                    }
                } catch (e: IOException) {
                    Log.e("Bluetooth", "Error reading from stream", e)
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream?.write(bytes)
            } catch (e: IOException) {
                Log.e("Bluetooth", "Error sending data", e)
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("Bluetooth", "Could not close the connect socket", e)
            }
        }
    }

    private fun addData(data: AccelerometerData) {
        _receivedData.value = _receivedData.value + data
    }

    private fun parseData(data: String): AccelerometerData {
        val splitData = data.trim().split(",")
        if (splitData.size == 3) {
            return try {
                val ax = splitData[0].toFloat()
                val ay = splitData[1].toFloat()
                val az = splitData[2].toFloat()
                AccelerometerData(ax, ay, az)
            } catch (e: NumberFormatException) {
                Log.e("Bluetooth", "Error parsing accelerometer data", e)
                AccelerometerData(0f, 0f, 0f)
            }
        } else {
            Log.e("Bluetooth", "Invalid data format: $data")
            return AccelerometerData(0f, 0f, 0f)
        }
    }

    private fun manageMyConnectedSocket(socket: BluetoothSocket?) {
        _isConnected.value = true
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
    }
}