package com.example.minerfinder

//import com.google.android.gms.common.util.IOUtils.copyStream

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SimpleArrayMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.minerfinder.databinding.ActivityConnectionBinding
import com.example.minerfinder.databinding.ActivityPhotoConnectionBinding
import com.example.minerfinder.db.AppDatabase
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import java.io.*
import java.nio.charset.StandardCharsets


class PhotoConnection : AppCompatActivity() {
    private val TAG = "Connection"
    private val SERVICE_ID = "Nearby"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this

    private var isAdvertising = false;
    private var eid : String = ""

    private lateinit var viewBinding: ActivityConnectionBinding

    private val READ_REQUEST_CODE = 42
    private val ENDPOINT_ID_EXTRA = "com.foo.myapp.EndpointId"

    companion object {
        private const val LOCATION_PERMISSION_CODE = 100
        private const val READ_PERMISSION_CODE = 101
        private const val REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 1
        private const val REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_connection)

        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_CODE)
        //checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION)
        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION)

        val viewBinding = ActivityPhotoConnectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.discoverButton.setOnClickListener { startDiscovery() }
        viewBinding.advertiseButton.setOnClickListener { startAdvertising() }


        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val requestCode = 1

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // Permission has already been granted
        } else {
            // Permission has not been granted yet, request it at runtime
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission has been granted, do something
                    Log.d("perm", "got write permission")
                } else {
                    // Permission has been denied, show a message or disable functionality
                    Log.d("perm", "filed to get write permission")
                }
                return
            }
        }
    }


    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this,permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            Log.d("perm", "denied $permission")
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
        else {
            Log.d(TAG, "Permissions granted")
        }
    }

    private fun getLocalUserName(): String {
        val db : AppDatabase = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()

        val user = db.userDao().findActive()

        if (user != null) {
            return user.username.toString()
        }

        return ""
    }

    private fun startAdvertising() {
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)

        Nearby.getConnectionsClient(context)
            .startAdvertising(
                getLocalUserName(), SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused: Void? ->
                connectionReport.text = "Advertising..." + getLocalUserName()
                this.isAdvertising = true
            }
            .addOnFailureListener { e: Exception? -> }
    }

    private fun startDiscovery() {
        val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)

        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                connectionReport.text = "Discovering..."
            }
            .addOnFailureListener { e: java.lang.Exception? -> }
    }

    private fun stopAdvertising() {
        Nearby.getConnectionsClient(context).stopAdvertising()
    }

    private fun stopDiscovery() {
        Nearby.getConnectionsClient(context).stopDiscovery()
    }

    /**
     * Fires an intent to spin up the file chooser UI and select an image for sending to endpointId.
     */
    private fun showImageChooser(endpointId: String) {
        this.eid = endpointId
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
    private fun sendPayLoad(endPointId: String, filePayload: Payload) {
        Log.d(TAG, context.filesDir.toString())
        try {
            Log.d(TAG, "sending file?")
            Nearby.getConnectionsClient(context).sendPayload(endPointId, filePayload)
        } catch (e: FileNotFoundException) {
            Log.e("MyApp", "File not found", e)
        }

    }

    internal class ReceiveFilePayloadCallback(private val context: Context) :
        PayloadCallback() {
        private val incomingFilePayloads = SimpleArrayMap<Long, Payload>()
        private val completedFilePayloads = SimpleArrayMap<Long, Payload?>()
        private val filePayloadFilenames = SimpleArrayMap<Long, String>()
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d("saveimage", "in pay rec")
            if (payload.type == Payload.Type.BYTES) {
                val payloadFilenameMessage = String(payload.asBytes()!!, StandardCharsets.UTF_8)
                val payloadId = addPayloadFilename(payloadFilenameMessage)
                processFilePayload(payloadId)
            } else if (payload.type == Payload.Type.FILE) {
//                Log.d("CON", "IN PAY REC")
//                // Add this to our tracking map, so that we can retrieve the payload later.
//                incomingFilePayloads.put(payload.id, payload)

                // Save the photo file
            }
        }


        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is payloadId:filename.
         */
        private fun addPayloadFilename(payloadFilenameMessage: String): Long {
            val parts = payloadFilenameMessage.split(":").toTypedArray()
            val payloadId = parts[0].toLong()
            val filename = parts[1]
            Log.d("NAME", filename)

            filePayloadFilenames.put(payloadId, filename)
            return payloadId
        }

        // add removed tag back to fix b/183037922
        private fun processFilePayload(payloadId: Long) {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            val filePayload = completedFilePayloads[payloadId]
            val filename = filePayloadFilenames[payloadId]
            if (filePayload != null && filename != null) {
                completedFilePayloads.remove(payloadId)
                filePayloadFilenames.remove(payloadId)

                // Get the received file (which will be in the Downloads folder)
                if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                    // Because of https://developer.android.com/preview/privacy/scoped-storage, we are not
                    // allowed to access filepaths from another process directly. Instead, we must open the
                    // uri using our ContentResolver.
                    val uri = filePayload.asFile()!!.asUri()
                    saveToPhotos(uri)

                } else {
                    val payloadFile = filePayload.asFile()!!.asJavaFile()

                    // Rename the file.
                    payloadFile!!.renameTo(File(payloadFile.parentFile, filename))
                }
            }
        }

        private fun saveToPhotos(uri: Uri?) {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val destFile = File(picturesDir, "testfile.jpg")
            val inputStream = uri?.let { context.contentResolver.openInputStream(it) }
            val outputStream = FileOutputStream(destFile)
            if (inputStream != null) {
                try {
                    inputStream.copyTo(outputStream)
                    outputStream.flush()

                    // Add the image to the MediaStore
                    val savedImage = BitmapFactory.decodeFile(destFile.absolutePath)
                    val imageTitle = "My Image Title"
                    val imageDescription = "My Image Description"
                    val savedUri = MediaStore.Images.Media.insertImage(
                        context.contentResolver,
                        savedImage,
                        imageTitle,
                        imageDescription
                    )

                    // Use the savedUri to open the image in the gallery app
//                    val intent = Intent(Intent.ACTION_VIEW, savedUri)
//                    startActivity(intent)

                } catch (e: Exception) {
                    Log.d("picstream", "Exception occurred: ${e.message}")
                } finally {
                    // Close the input and output streams
                    try {
                        inputStream?.close()
                    } catch (e: IOException) {
                        Log.d("picstream", "Error closing input stream: ${e.message}")
                    }
                    try {
                        outputStream?.close()
                    } catch (e: IOException) {
                        Log.d("picstream", "Error closing output stream: ${e.message}")
                    }
                }
            }
            Log.d("photosteam", "end of save photo fun")
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payloadId = update.payloadId
                val payload = incomingFilePayloads.remove(payloadId)
                completedFilePayloads.put(payloadId, payload)
                if (payload!!.type == Payload.Type.FILE) {
                    processFilePayload(payloadId)
                }
            }
        }

        companion object {
            /** Copies a stream from one location to another.  */
            @Throws(IOException::class)
            private fun copyStream(`in`: InputStream?, out: OutputStream) {
                try {
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (`in`!!.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                } finally {
                    `in`!!.close()
                    out.close()
                }
            }
        }
    }

    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                // An endpoint was found. We request a connection to it.
                Nearby.getConnectionsClient(context)
                    .requestConnection(getLocalUserName(), endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener(
                        OnSuccessListener { unused: Void? -> })
                    .addOnFailureListener(
                        OnFailureListener { e: java.lang.Exception? -> })
            }

            override fun onEndpointLost(endpointId: String) {
                // A previously discovered endpoint has gone away.
            }
        }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {

            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // Automatically accept the connection on both sides.
                Log.d("CONINFO", connectionInfo.toString())
                Log.d("CONINFO", endpointId.toString())
                Log.d("CONINFO", context.toString())

                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)

                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        connectionReport.text = "Connection Made!"
                        if (isAdvertising) {
//                            sendPayLoad(endpointId)
                            stopAdvertising()
                            showImageChooser(endpointId)
                        }
                        else {
                            stopDiscovery()
                        }
                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {}
                    ConnectionsStatusCodes.STATUS_ERROR -> {}
                    else -> {}
                }
            }

            override fun onDisconnected(endpointId: String) {
                // We've been disconnected from this endpoint. No more data can be
                // sent or received.
            }
        }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        //        private val context: Context? = null
        private var incomingFilePayloads = SimpleArrayMap<Long, Payload>()
        private var completedFilePayloads = SimpleArrayMap<Long, Payload>()
        private var filePayloadFilenames = SimpleArrayMap<Long, String>()
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // A new payload is being sent over.
            Log.d(TAG, "Payload Received")
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val payloadFilenameMessage = String(payload.asBytes()!!, StandardCharsets.UTF_8)

//                    val payloadFilenameMessage = SerializationHelper.deserialize(payload.asBytes())

                    val payloadId: Long = addPayloadFilename(payloadFilenameMessage)
                    processFilePayload(payloadId)
//                    var rcvdFilename = String(payload.asBytes()!!, StandardCharsets.UTF_8)
                    Log.d(TAG, payload.asBytes().toString())
                    val dataDisplay: TextView = findViewById<TextView>(R.id.data_received)
                    dataDisplay.text = payloadFilenameMessage
                }
                Payload.Type.FILE -> {
                    Log.d(TAG, "receiving file?")
                    // Add this to our tracking map, so that we can retrieve the payload later.
                    incomingFilePayloads.put(payload.id, payload);

                    val fileUri = payload.asFile()!!.asUri()
                    Log.d("saveimage", fileUri.toString())

                }
//                Payload.Type.STREAM -> {
//                    Log.d(TAG, "Inside file mode")
//                }
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is payloadId:filename.
         */
        private fun addPayloadFilename(payloadFilenameMessage: String): Long {
            Log.d("PATH", "IN ADDPAYFIL")
            val parts = payloadFilenameMessage.split(":").toTypedArray()
            val payloadId = parts[0].toLong()
            val filename = parts[1]
            filePayloadFilenames.put(payloadId, filename)
            return payloadId
        }

        private fun copyStream(`in`: InputStream?, out: OutputStream) {
            try {
                val buffer = ByteArray(1024)
                var read: Int
                while (`in`!!.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.flush()
            } finally {
                `in`!!.close()
                out.close()
            }
        }

        private fun processFilePayload(payloadId: Long) {
            Log.d("PATH", "IN PROCFILE")
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            val filePayload = completedFilePayloads[payloadId]
            val filename: String? = filePayloadFilenames.get(payloadId)
            if(filename != null)
                Log.d("PFP", filename)
            if (filePayload != null && filename != null) {
                completedFilePayloads.remove(payloadId)
                filePayloadFilenames.remove(payloadId)

                Log.d("DOWN", "ABOVE REMOVE DOWN")

                // Get the received file (which will be in the Downloads folder)
                // Because of https://developer.android.com/preview/privacy/scoped-storage, we are not
                // allowed to access filepaths from another process directly. Instead, we must open the
                // uri using our ContentResolver.
                val uri: Uri? = filePayload.asFile()!!.asUri()

                lateinit var imageView: ImageView
                imageView = findViewById(R.id.imageView)
                imageView.setImageURI(uri)

                saveToPhotos(uri)
            }
        }

        private fun saveToPhotos(uri: Uri?) {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val destFile = File(picturesDir, "testfile.jpg")
            val inputStream = uri?.let { context.contentResolver.openInputStream(it) }
            val outputStream = FileOutputStream(destFile)
            if (inputStream != null) {
                try {
                    inputStream.copyTo(outputStream)
                    outputStream.flush()

                    // Add the image to the MediaStore
                    val savedImage = BitmapFactory.decodeFile(destFile.absolutePath)
                    val imageTitle = "My Image Title"
                    val imageDescription = "My Image Description"
                    val savedUri = MediaStore.Images.Media.insertImage(
                        context.contentResolver,
                        savedImage,
                        imageTitle,
                        imageDescription
                    )

                    // Use the savedUri to open the image in the gallery app
//                    val intent = Intent(Intent.ACTION_VIEW, savedUri)
//                    startActivity(intent)

                } catch (e: Exception) {
                    Log.d("picstream", "Exception occurred: ${e.message}")
                } finally {
                    // Close the input and output streams
                    try {
                        inputStream?.close()
                    } catch (e: IOException) {
                        Log.d("picstream", "Error closing input stream: ${e.message}")
                    }
                    try {
                        outputStream?.close()
                    } catch (e: IOException) {
                        Log.d("picstream", "Error closing output stream: ${e.message}")
                    }
                }
            }
            Log.d("photosteam", "end of save photo fun")
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d("PATH", "ST SUCC")

                val payloadId = update.payloadId
                val payload = incomingFilePayloads.remove(payloadId)
                completedFilePayloads.put(payloadId, payload)

                Log.d("PATH", completedFilePayloads.toString())

                if (payload != null && payload.type == Payload.Type.FILE) {
                    processFilePayload(payloadId)
                }
            }
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