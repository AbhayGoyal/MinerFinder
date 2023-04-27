package com.example.minerfinder

//import com.google.android.gms.common.util.IOUtils.copyStream

import android.Manifest
import android.content.Context
import android.content.Intent
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
import java.nio.charset.StandardCharsets
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

    val links = mutableListOf<List<String>>() // endpointid, usernumber
    val lost = mutableListOf<String>()
    val offline = mutableListOf<String>()

    // send photo
    private val READ_REQUEST_CODE = 42
    private val ENDPOINT_ID_EXTRA = "com.foo.myapp.EndpointId"
    val global = applicationContext as Global

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

//        viewBinding.discoverButton.setOnClickListener {
//            startDiscovery()
//        }
//        viewBinding.advertiseButton.setOnClickListener {
//            startAdvertising()
//        }
        viewBinding.offButton.setOnClickListener {
            for (i in links.indices) {
                disconnectEndpoint(links[i][0])
            }
            modeOff()
        }
//        viewBinding.disconnectButton.setOnClickListener {
//            for (i in links.indices) {
//                disconnectEndpoint(links[i][0])
//            }
//        }
        viewBinding.bothButton.setOnClickListener {
            startAdvertising(false)
            startDiscovery(false)
            modeDisplay()
        }

        viewBinding.sendPhotoButton.setOnClickListener {
            if (global.found_eid.isNotEmpty()) {
                val firstEid = global.found_eid[0]
                // sendPhoto
                Log.d("haseid", firstEid)
                sendPhoto(firstEid)
            }
            Log.d("haseid", "no :(")
        }
    }

      ////////////////
     // SEND PHOTO //
    ////////////////
    private fun sendPhoto(endpointId: String) {
        showImageChooser(endpointId)
    }


    private fun showImageChooser(endpointId: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        intent.putExtra(ENDPOINT_ID_EXTRA, endpointId)
        startActivityForResult(intent, READ_REQUEST_CODE)
        Log.d(TAG, "end img")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK && resultData != null) {
//            val endpointId = resultData.getStringExtra(ENDPOINT_ID_EXTRA)
            val endpointId = this.eid
            Log.d("EID", endpointId.toString())

            // The URI of the file selected by the user.
            val uri = resultData.data
            val filePayload: Payload
            filePayload = try {
                // Open the ParcelFileDescriptor for this URI with read access.
                val pfd = contentResolver.openFileDescriptor(uri!!, "r")
                Payload.fromFile(pfd!!)
            } catch (e: FileNotFoundException) {
                Log.e("MyApp", "File not found", e)
                return
            }

            // Construct a simple message mapping the ID of the file payload to the desired filename.
            val filenameMessage = filePayload.id.toString() + ":" + uri.lastPathSegment

            Log.d("FILENAME", filenameMessage)

            // Send the filename message as a bytes payload.
            val filenameBytesPayload =
                Payload.fromBytes(filenameMessage.toByteArray(StandardCharsets.UTF_8))
            Nearby.getConnectionsClient(context).sendPayload(endpointId!!, filenameBytesPayload)

            // Finally, send the file payload.
            if(endpointId != null) {
                Log.d(TAG, "in result")

                Nearby.getConnectionsClient(context).sendPayload(endpointId, filePayload).addOnSuccessListener {
                    Log.d(TAG, "successful send?")
                }
            }
        }
    }

      //////end///////
     // SEND PHOTO //
    ////////////////



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
//        Log.d("errorlog", e)
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

    private fun linksDisplay() {
        runOnUiThread {
            val linksDisplay: TextView = findViewById<TextView>(R.id.links)
            val linksNumbers = links.map { it[1] }
            linksDisplay.text = "Links/lost: $linksNumbers / $lost"
        }
    }

    private fun offlineDisplay() {
        runOnUiThread {
            val offlineDisplay: TextView = findViewById<TextView>(R.id.offline)
            offlineDisplay.text = "Universal offline: $offline"
        }
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
        val lostNumber = links.find { it[0] == endpointId }

        connectionDisplay("Disconnected from $lostNumber[0]")

        if (lostNumber != null) {
            val alreadyExists = lost.contains(lostNumber[0])
            if(!alreadyExists) {
                lost.add(lostNumber[1])
//                offline.add(lostNumber[1])
                links.remove(lostNumber)
            }
            sendLostMessage(lostNumber[1].toString())
        }
        linksDisplay()
//        offlineDisplay()

    }

    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                // An endpoint was found. We request a connection to it.
//                stopDiscovery()
                Nearby.getConnectionsClient(context)
                    .requestConnection(userNumber, endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener { unused: Void? ->
                        connectionDisplay("Found endpoint. Requesting connection.")
                        global.found_eid.add(endpointId)
                        Log.d("eidlist", global.found_eid.toString())
                    }
                    .addOnFailureListener { e: java.lang.Exception? ->
//                        connectionDisplay("Found endpoint. Failed to request connection.") // rm for display
                        errorDisplay(e.toString())
                    }
//                startDiscovery(false)
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
                val lostNumber = links.find { it[0] == endpointId }
                if (lostNumber != null) {
                    val alreadyExists = lost.contains(lostNumber[0])
                    if(!alreadyExists) {
                        lost.add(lostNumber[1])
                        offline.add(lostNumber[1])
                        links.remove(lostNumber)
                    }
                    sendLostMessage(lostNumber[1].toString())
                }
                linksDisplay()
                offlineDisplay()
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

        if (message.contains("lost connection to")) {
            messageDisplay(message)
            offline.add(message.last().toString())
            offlineDisplay()
        }
        else if (message.contains("regained connection to")) {
            messageDisplay(message)
            offline.remove(message.last().toString())
            offlineDisplay()
        }
        else if (message.last() == '0') {
            GlobalScope.launch(Dispatchers.IO) {
                val newMessage = message.dropLast(1).split(",")
                val otherUser = newMessage.last()

                if (lost.contains(otherUser)) {
                    lost.remove(otherUser)
                }

                val userNumber = links.find { it[1] == otherUser }
                if (userNumber == null) {
                    links.add(listOf(endpointId, otherUser))
                    linksDisplay()
                }
                if(offline.contains(otherUser)) {
                    offline.remove(otherUser)
                    sendOnline(otherUser)
                }


                Log.d("stampbug", newMessage.dropLast(1).toString())
                evalTimestamps(newMessage.dropLast(1).joinToString(), endpointId)
                runOnUiThread {
                    connectionDisplay("Received timestamp.csv from User #$otherUser")
                }
            }
        }
        else if (message.last() == '1') {
            readMiner(message.dropLast(1))
        }
    }

    fun sendTimestamps(endpointId: String) {
        val fileName = "timestamp.csv"
        val file = File(filesDir, fileName)
        var contents = ""
        if (file.exists()) {
            contents = file.bufferedReader().readText() + ",$userNumber" + "0"
        }
        else {
            contents = "$userNumber" + "0"
        }
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
        if (!file.exists()) {
            if (minerNumber.toString() == userNumber) {
                updateTimestampFile(minerNumber)
                return // maybe should create file
            }
            else {
                return
            }
        }
        Log.d("csv%", minerNumber.toString())
        val contents = file.readText() + ",$minerNumber,$timestamp" + "1"
        val bytesPayload = Payload.fromBytes(serialize(contents))
        Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)

    }

    fun readMiner(message: String) {
        val csv = message.split(",").toMutableList()
        Log.d("csvbug", csv.toString())
        val minerNumber: Int = csv[csv.size.toInt()-2].toInt()
        val timestamp: Timestamp = Timestamp.valueOf(csv[csv.size.toInt()-1])
        csv.removeAt(csv.size.toInt()-1)
        csv.removeAt(csv.size.toInt()-1)
        messageDisplay("Received $minerNumber.json")
        Log.d("csv", message)
        Log.d("csv#", minerNumber.toString())

//        filesDir.walk().forEach {
//            Log.d("dir", it.toString())
//        }

        // update miner data file
        val fileName = "$minerNumber.json"
        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
        fileOutputStream.write(csv.joinToString().toByteArray())
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

    fun sendLostMessage(number: String) {
        val linksNumbers = links.map { it[0] }
        for (endpointId in linksNumbers) {
            val contents = "lost connection to $number"
            val bytesPayload = Payload.fromBytes(serialize(contents))
            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
        }

    }

    fun sendOnline(number: String) {
        val linksNumbers = links.map { it[0] }
        for (endpointId in linksNumbers) {
            val contents = "regained connection to $number"
            val bytesPayload = Payload.fromBytes(serialize(contents))
            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
        }

    }
}
