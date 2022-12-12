package com.example.minerfinder

//import com.google.android.gms.common.util.IOUtils.copyStream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SimpleArrayMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.minerfinder.Connection.SerializationHelper.serialize
import com.example.minerfinder.databinding.ActivityConnectionBinding
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import java.io.*
import java.sql.Timestamp


// RENAME TO CONNECTION IF USING AGAIN
class Connection : AppCompatActivity() {
    private val TAG = "Connection"
    private val SERVICE_ID = "Nearby"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this

    private var isAdvertising = false;
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
        viewBinding.randomButton.setOnClickListener {
            randomMode = true
            GlobalScope.launch(Dispatchers.IO) {
                mode_handler()
            }
        }
    }

    private suspend fun mode_handler() {
        while(randomMode) {
            val rnds = (0..5).random()
            Log.d("confun", rnds.toString())
            if(rnds == 1) {
                if (isAdvertising) {
                    stopAdvertising()
                    startDiscovery()
                } else {
                    stopDiscovery()
                    startAdvertising()
                }
            }
            delay(5000)
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

    private fun startAdvertising() {
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val connectionMode: TextView = findViewById<TextView>(R.id.connection_mode)

        Nearby.getConnectionsClient(context)
            .startAdvertising(
                getLocalUserName(), SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused: Void? ->
                connectionMode.text = "Advertising..."
                this.isAdvertising = true
            }
            .addOnFailureListener { e: Exception? -> }
    }

    private fun startDiscovery() {
        val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        val connectionMode: TextView = findViewById<TextView>(R.id.connection_mode)
        this.isAdvertising = false

        Log.d("FUNCTION", "sd")

        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                connectionMode.text = "Discovering..."
            }
            .addOnFailureListener { e: java.lang.Exception? ->
                connectionMode.text = "Discovery Failed: " + e.toString()
            }
    }

    private fun stopAdvertising() {
        Nearby.getConnectionsClient(context).stopAdvertising()
    }

    private fun stopDiscovery() {
        Nearby.getConnectionsClient(context).stopDiscovery()
    }

    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                // An endpoint was found. We request a connection to it.
                Nearby.getConnectionsClient(context)
                    .requestConnection(getLocalUserName(), endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener { unused: Void? ->
                        found_eid.add(endpointId)
                        Log.d("eidlist", found_eid.toString())
                    }
                    .addOnFailureListener { e: java.lang.Exception? -> }
            }

            override fun onEndpointLost(endpointId: String) {
                // A previously discovered endpoint has gone away.
                Log.d("status", "lost")
                val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
                connectionReport.text = "Not connected"
            }
        }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // Automatically accept the connection on both sides.
                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)

                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        connectionReport.text = "Connection Made!"
                        val timestamp = Timestamp(System.currentTimeMillis())

                        val bytesPayload = Payload.fromBytes(serialize(timestamp))
                        Log.d("MESSAGE", bytesPayload.toString())
                        if(isAdvertising)
                            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)

                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {}
                    ConnectionsStatusCodes.STATUS_ERROR -> {}
                    else -> {}
                }
            }

            override fun onDisconnected(endpointId: String) {
                // We've been disconnected from this endpoint. No more data can be
                // sent or received.
                Log.d("status", "disconnected")
                val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
                connectionReport.text = "Not connected"
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
                Nearby.getConnectionsClient(context).disconnectFromEndpoint(endpointId)
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
