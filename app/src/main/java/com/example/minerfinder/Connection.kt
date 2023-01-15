package com.example.minerfinder

//import com.google.android.gms.common.util.IOUtils.copyStream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.minerfinder.Connection.SerializationHelper.serialize
import com.example.minerfinder.databinding.ActivityConnectionBinding
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.*
import java.sql.Timestamp

enum class Mode {
    OFF, DISCOVERING, ADVERTISING, BOTH
}

// RENAME TO CONNECTION IF USING AGAIN
class Connection : AppCompatActivity() {
    private val TAG = "Connection"
    private val SERVICE_ID = "Nearby"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this

    private var mode = Mode.OFF;
    private var eid : String = ""
    private var randomMode = false

    private lateinit var viewBinding: ActivityConnectionBinding

    private val found_eid = mutableListOf<String>()

    companion object {
        private const val LOCATION_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_CODE)

        viewBinding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.discoverButton.setOnClickListener {
            randomMode = false
            startDiscovery()
        }
        viewBinding.advertiseButton.setOnClickListener {
            randomMode = false
            startAdvertising()
        }
        viewBinding.offButton.setOnClickListener {
            randomMode = false
            modeOff()
        }
        viewBinding.disconnectButton.setOnClickListener {
            disconnectEndpoint()
        }
        viewBinding.randomButton.setOnClickListener {
//            if(!randomMode) {
//                randomMode = true
//                GlobalScope.launch(Dispatchers.IO) {
//                    modeHandler()
//                }
//            }
            startAdvertising(false)
            startDiscovery(false)
            mode = Mode.BOTH
            modeDisplay()
        }
    }

    private suspend fun modeHandler() {
        if(mode == Mode.OFF)
            startDiscovery()

        while(randomMode) {
            val rnds = (0..5).random()
            Log.d("confun", rnds.toString())
            if(rnds == 1) {
                if (mode == Mode.DISCOVERING) {
                    runOnUiThread {
                        startAdvertising()
                    }
                } else {
                    runOnUiThread {
                        startDiscovery()
                    }
                }
            }
            delay(5000)
        }
    }

    private suspend fun constantSend(endpointId: String) {
        var flag = true
        while(flag){
            val timestamp = Timestamp(System.currentTimeMillis())
            val bytesPayload = Payload.fromBytes(serialize(timestamp))
            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
                .addOnSuccessListener { unused: Void? -> }
                .addOnFailureListener { e: java.lang.Exception? ->
                    flag = false
                }
            delay(1000)
        }
    }

    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this,permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
        else {
            Log.d(TAG, "Permissions not denied")
        }
    }

    private fun getLocalUserName(): String {
        return "1"
    }

    private fun modeDisplay() {
        val connectionMode: TextView = findViewById<TextView>(R.id.connection_mode)
        connectionMode.text = "Connection Mode: $mode"
    }

    private fun errorDisplay(e: String) {
        val errorLog: TextView = findViewById<TextView>(R.id.error_log)
        errorLog.text = "Error Log: $e"
    }

    private fun connectionDisplay(m: String) {
        val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
        connectionReport.text = "Connection Report: $m"
    }

    private fun startAdvertising(singleMode: Boolean = true) {
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        if(mode == Mode.DISCOVERING && singleMode)
            stopDiscovery()

        Nearby.getConnectionsClient(context)
            .startAdvertising(
                getLocalUserName(), SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused: Void? ->
                mode = Mode.ADVERTISING
                modeDisplay()
            }
            .addOnFailureListener { e: Exception? ->
                errorDisplay("Advertising Failed: " + e.toString())
            }
    }

    private fun startDiscovery(singleMode: Boolean = true) {
        val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        if(mode == Mode.ADVERTISING && singleMode)
            stopAdvertising()

        Log.d("FUNCTION", "sd")

        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                mode = Mode.DISCOVERING
                modeDisplay()
            }
            .addOnFailureListener { e: java.lang.Exception? ->
                errorDisplay("Discovery Failed: " + e.toString())
            }
    }

    private fun modeOff() {
        if(mode == Mode.ADVERTISING)
            stopAdvertising()
        else if (mode == Mode.DISCOVERING)
            stopDiscovery()
        modeDisplay()
    }

    private fun stopAdvertising() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        mode = Mode.OFF
        modeDisplay()
    }

    private fun stopDiscovery() {
        Nearby.getConnectionsClient(context).stopDiscovery()
        mode = Mode.OFF
        modeDisplay()
    }

    private fun disconnectEndpoint(endpointId: String = eid) {
        Nearby.getConnectionsClient(context).disconnectFromEndpoint(endpointId)
        connectionDisplay("Disconnected from $endpointId")
    }

    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                // An endpoint was found. We request a connection to it.
                Nearby.getConnectionsClient(context)
                    .requestConnection(getLocalUserName(), endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener { unused: Void? ->
                        connectionDisplay("Found endpoint. Requesting connection.")
                        found_eid.add(endpointId)
                        Log.d("eidlist", found_eid.toString())
                    }
                    .addOnFailureListener { e: java.lang.Exception? ->
                        connectionDisplay("Found endpoint. Failed to request connection.")
                        errorDisplay(e.toString())
                    }
            }

            override fun onEndpointLost(endpointId: String) {
                // A previously discovered endpoint has gone away.
                Log.d("status", "lost")
                connectionDisplay("Lost endpoint")
            }
        }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // Automatically accept the connection on both sides.
                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        connectionDisplay("Made a connection")
                        val timestamp = Timestamp(System.currentTimeMillis())

                        val bytesPayload = Payload.fromBytes(serialize(timestamp))
                        Log.d("MESSAGE", bytesPayload.toString())
                        if(mode == Mode.ADVERTISING) {
                            GlobalScope.launch(Dispatchers.IO) {
                                constantSend(endpointId)
                            }
                        }
//                            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)

                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                        connectionDisplay("Connection Rejected")
                    }
                    ConnectionsStatusCodes.STATUS_ERROR -> {
                        errorDisplay("Failed to connect. Status Error.")
                    }
                    else -> {}
                }
            }

            override fun onDisconnected(endpointId: String) {
                // We've been disconnected from this endpoint. No more data can be
                // sent or received.
                Log.d("status", "disconnected")
                connectionDisplay("Disconnected from endpoint.")
            }
        }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // This always gets the full data of the payload. Is null if it's not a BYTES payload.
            if (payload.type == Payload.Type.BYTES) {
                val receivedBytes = SerializationHelper.deserialize(payload.asBytes())
                Log.d("MESSAGE", receivedBytes.toString())

                val dataDisplay: TextView = findViewById<TextView>(R.id.data_received)
                dataDisplay.text = "Message: $receivedBytes"
                connectionDisplay("Message received.")

                // send a message back for TESTING
                if(mode == Mode.DISCOVERING) {
                    val bytesPayload = Payload.fromBytes(serialize("RECEIPT: $receivedBytes"))
                    Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
                }

                eid = endpointId
//                Nearby.getConnectionsClient(context).disconnectFromEndpoint(endpointId)
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    /** Helper class to serialize and deserialize an Object to byte[] and vice-versa  */
    object SerializationHelper {
        @Throws(IOException::class)
        fun serialize(`object`: Any?): ByteArray {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
            // transform object to stream and then to a byte array
            objectOutputStream.writeObject(`object`)
            objectOutputStream.flush()
            objectOutputStream.close()
            return byteArrayOutputStream.toByteArray()
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        fun deserialize(bytes: ByteArray?): Any {
            val byteArrayInputStream = ByteArrayInputStream(bytes)
            val objectInputStream = ObjectInputStream(byteArrayInputStream)
            return objectInputStream.readObject()
        }
    }
}
