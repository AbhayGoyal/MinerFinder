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
import org.json.JSONObject
import java.io.*
import java.sql.Timestamp


// RENAME TO CONNECTION IF USING AGAIN
class Connection : AppCompatActivity() {
    private val TAG = "Connection"
    private val SERVICE_ID = "Nearby"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this

    private var isAdvertising: Boolean = false
    private var isDiscovering: Boolean = false
    private var eid : String = ""

    var userNumber: String = "x"

    private lateinit var viewBinding: ActivityConnectionBinding

    private val found_eid = mutableListOf<String>()

    companion object {
        private const val LOCATION_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        userNumber = Helper().getLocalUserName(applicationContext)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_CODE)

        viewBinding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

//        viewBinding.discoverButton.setOnClickListener {
//            startDiscovery()
//        }
//        viewBinding.advertiseButton.setOnClickListener {
//            startAdvertising()
//        }
        viewBinding.offButton.setOnClickListener {
            modeOff()
        }
//        viewBinding.disconnectButton.setOnClickListener {
//            disconnectEndpoint()
//        }
        viewBinding.bothButton.setOnClickListener {
            startAdvertising(false)
            startDiscovery(false)
            modeDisplay()
        }
    }


    // For testing a constant connection
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

//    private fun getLocalUserName(): String {
//        return "1"
//    }

    private fun modeDisplay() {
        var mode: String = "OFF"
        if (isAdvertising && isDiscovering) {
            mode = "ON"
        }
        else if (isAdvertising) {
            mode = "ADVERTISING"
        }
        else if (isDiscovering) {
            mode = "DISCOVERING"
        }
        val connectionMode: TextView = findViewById<TextView>(R.id.connection_mode)
        connectionMode.text = "Connection Mode: $mode"
    }

    private fun errorDisplay(e: String) {
//        val errorLog: TextView = findViewById<TextView>(R.id.error_log)
//        errorLog.text = "Error Log: $e"
    }

    private fun connectionDisplay(m: String) {
        val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
        connectionReport.text = "Connection Report: $m"
    }

    private fun messageDisplay(m: String) {
        val dataDisplay: TextView = findViewById<TextView>(R.id.data_received)
        dataDisplay.text = "Message: $m"
    }

    private fun startAdvertising(singleMode: Boolean = true) {
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        if(isDiscovering && singleMode)
            stopDiscovery()

        Nearby.getConnectionsClient(context)
            .startAdvertising(
                userNumber, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused: Void? ->
                isAdvertising = true
                modeDisplay()
            }
            .addOnFailureListener { e: Exception? ->
                errorDisplay("Advertising Failed: " + e.toString())
            }
    }

    private fun startDiscovery(singleMode: Boolean = true) {
        val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        if(isAdvertising && singleMode)
            stopAdvertising()

        Log.d("FUNCTION", "sd")

        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                isDiscovering = true
                modeDisplay()
            }
            .addOnFailureListener { e: java.lang.Exception? ->
                errorDisplay("Discovery Failed: " + e.toString())
            }
    }

    private fun modeOff() {
        if(isAdvertising)
            stopAdvertising()
        if (isDiscovering)
            stopDiscovery()
        modeDisplay()
    }

    private fun stopAdvertising() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        isAdvertising = false
        modeDisplay()
    }

    private fun stopDiscovery() {
        Nearby.getConnectionsClient(context).stopDiscovery()
        isDiscovering = false
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
                    .requestConnection(userNumber, endpointId, connectionLifecycleCallback)
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
                        sendTimestamps(endpointId)
//                        val timestamp = Timestamp(System.currentTimeMillis())
//
//                        val bytesPayload = Payload.fromBytes(serialize(timestamp))
//                        Log.d("MESSAGE", bytesPayload.toString())
//                        if(isAdvertising) {
//                            GlobalScope.launch(Dispatchers.IO) {
//                                constantSend(endpointId)
//                            }
//                        }
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

//                val dataDisplay: TextView = findViewById<TextView>(R.id.data_received)
//                dataDisplay.text = "Message: $receivedBytes"
                connectionDisplay("Message received.")

                evalMessage(receivedBytes.toString(), endpointId)

                // send a message back for TESTING
//                if(isDiscovering) {
//                    val bytesPayload = Payload.fromBytes(serialize("RECEIPT: $receivedBytes"))
//                    Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
//                }

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


    // HANDLE FILE TRANSFERS

    // add 0 to end of file if its timestamps; 1 if its miner data

    fun evalMessage(message: String, endpointId: String) {
        Log.d("evalmes", message)
        if (message.last() == '0') {
            GlobalScope.launch(Dispatchers.IO) {
                evalTimestamps(message.dropLast(1), endpointId)
            }
            messageDisplay("Received timestamp.csv")
        }
        else if (message.last() == '1') {
            readMiner(message.dropLast(1))
        }
    }

    fun sendTimestamps(endpointId: String) {
        val fileName = "timestamp.csv"
        val file = File(filesDir, fileName)
        val contents = file.bufferedReader().readText() + "0"
        val bytesPayload = Payload.fromBytes(serialize(contents))
        Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
    }

    suspend fun evalTimestamps(partnerStamps: String, endpointId: String) {
        val partnerCSV = partnerStamps.split(",").toMutableList()

        val fileName = "timestamp.csv"
        val file = File(filesDir, fileName)
        var timestampString: String

        if (file.exists()) {
            val rows = file.bufferedReader().readText()
            val myCSV = rows.split(",").toMutableList()

            for (i in 0 until myCSV.size) {
                // if partner doesn't have that file or mine is newer send it to them
                if (i > partnerCSV.size-1 || Timestamp.valueOf(myCSV[i]) > Timestamp.valueOf(partnerCSV[i])) {
                    sendMiner(endpointId, i+1, Timestamp.valueOf(myCSV[i]))
                    delay(1000)
                }
            }
        }
    }

    fun sendMiner(endpointId: String, minerNumber: Int, timestamp: Timestamp) {
        val fileName = "$minerNumber.json"
        val file = File(filesDir, fileName)
        if (file.exists()) {
            Log.d("csv%", minerNumber.toString())
            val contents = file.readText() + ",$minerNumber,$timestamp" + "1"
            val bytesPayload = Payload.fromBytes(serialize(contents))
            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
        }
    }

    fun readMiner(message: String) {
        val csv = message.split(",").toMutableList()
        val minerNumber: Int = csv[csv.size.toInt()-2].toInt()
        val timestamp: Timestamp = Timestamp.valueOf(csv[csv.size.toInt()-1])
//        csv.removeAt(csv.size.toInt()-1)
//        csv.removeAt(csv.size.toInt()-1)
        messageDisplay("Received $minerNumber.json")
        Log.d("csv", message)
        Log.d("csv#", minerNumber.toString())

//        filesDir.walk().forEach {
//            Log.d("dir", it.toString())
//        }

        // update miner data file
        val fileName = "$minerNumber.json"
        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
        fileOutputStream.write(csv[0].toByteArray())
        fileOutputStream.close()

        // update timestamp file
        updateTimestampFile(minerNumber, timestamp)
    }

    fun updateTimestampFile(userNumber: Int, currentTimestamp: Timestamp = Timestamp(System.currentTimeMillis())){
        val userNumberIdx = userNumber - 1
        val fileName = "timestamp.csv"
        val file = File(filesDir, fileName)
        var timestampString: String

        if (file.exists()) {
            val rows = file.bufferedReader().readText()
            val csv = rows.split(",").toMutableList()
            Log.d("json", userNumber.toString())
            while (csv.size < userNumber) {
                csv.add(Timestamp(0).toString())
            }
            csv[userNumberIdx] = currentTimestamp.toString()
            timestampString = csv.joinToString(",")
            Log.d("json timestamp", timestampString.toString())
        }
        else {
            timestampString = ""
            for (i in 0 .. userNumberIdx) {
                timestampString += if (i == userNumberIdx) {
                    Timestamp(System.currentTimeMillis()).toString()
                } else {
                    Timestamp(0).toString() + ","
                }
            }
        }

        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
        fileOutputStream.write(timestampString.toByteArray())
        fileOutputStream.close()

    }
}
