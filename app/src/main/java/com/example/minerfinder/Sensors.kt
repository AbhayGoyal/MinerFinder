package com.example.minerfinder

//import com.example.minecomms.databinding.ActivityConnectionBinding
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.minerfinder.databinding.ActivitySensorsBinding
import com.example.minerfinder.db.AppDatabase
import org.json.JSONObject
import java.io.*
import java.sql.Timestamp
import kotlin.math.pow


class Sensors : AppCompatActivity(), SensorEventListener {

    private val MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 90

    private lateinit var viewBinding: ActivitySensorsBinding

    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val linearAccelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var xTot = 0.0
    private var yTot = 0.0
    private var startTime = Timestamp(0)
    private var curTime = Timestamp(0)
    private var timeDiff = 0.0
    private var hyp = 0.0
    private var v0 = 0.0
    private var angle = 0.0

    private var v0x = 0.0
    private var v0y = 0.0
    private var count = 0

    private var step_count = 0
    private var accel_iter = 0
    private var init_x = 0f
    private var init_y = 0f
    private var init_z = 0f

    // time, angle, steps
    private var step_cur_time = Timestamp(0)
    private var step_start_time = Timestamp(0)
    private var step_mid_time = Timestamp(0)
    val step_hist = MutableList(0) { listOf<Any>() }
    private var prev_steps = 0
    private val avg_step_size = 0.76 // meters
    private var step_x = 0.0
    private var step_y = 0.0

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensors)

        viewBinding = ActivitySensorsBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        checkPermissions()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
        // You must implement this callback in your code.
    }

    override fun onResume() {
        super.onResume()

        // Get updates from the accelerometer and magnetometer at a constant rate.
        // To make batch operations more efficient and reduce power consumption,
        // provide support for delaying updates to the application.
        //
        // In this example, the sensor reporting delay is small enough such that
        // the application receives an update before the system checks the sensor
        // readings again.
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also { pedometer ->
            sensorManager.registerListener(
                this,
                pedometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        step_handler()
    }

    override fun onPause() {
        super.onPause()

        // Don't receive any more updates from either sensor.
        sensorManager.unregisterListener(this)
    }

    // Get readings from accelerometer and magnetometer. To simplify calculations,
    // consider storing these readings as unit vectors.
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            System.arraycopy(event.values, 0, linearAccelerometerReading, 0, linearAccelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            step_count = event.values[0].toInt()
            Log.d("STEPS", step_count.toString())
        }
        step_handler()
    }

    // Compute the three orientation angles based on the most recent readings from
    // the device's accelerometer and magnetometer.
    private fun updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // "orientationAngles" now has up-to-date information.
    }

    private fun getAzimuth(): Int {
        updateOrientationAngles()

        // looking for Azimuth
        var azimuth = (this.orientationAngles[0] * 180 / 3.14).toInt()
        if (azimuth < 0) {
            azimuth += 360
        }

        return azimuth
    }

    private fun step_handler() {
        // set up first ever run
        if(step_start_time == Timestamp(0)) {
            step_start_time = Timestamp(System.currentTimeMillis())
            step_cur_time = step_start_time
            step_mid_time = step_cur_time
            prev_steps = step_count
        }
        else {
            step_cur_time = Timestamp(System.currentTimeMillis())
        }

        // inside 60 sec interval
        timeDiff = ((step_cur_time.time - step_start_time.time) / 1000.0)

        val mag = (step_count - prev_steps) * avg_step_size / ((step_cur_time.time - step_mid_time.time) / 1000.0)
        prev_steps = step_count
        step_mid_time = step_cur_time
        val angle = getAzimuth()
        val coord = get_coord(mag, angle.toDouble())
        Log.d("STEP_HIST_MGC", listOf(mag, angle, coord).toString())
        this.step_x += coord[0].toInt()
        this.step_y += coord[1].toInt()

        Log.d("STEP_HIST_XY", step_x.toString() + "," + step_y.toString())

        if(timeDiff >= 5) {
            step_start_time = step_cur_time
            val comp = get_comp(step_x, step_y)
            val ret = listOf<Any>(step_cur_time, comp[0] / timeDiff, comp[1])
            step_hist.add(ret)
            step_x = 0.0
            step_y = 0.0
            prev_steps = step_count

            Log.d("STEP_HIST_LOG", step_hist.toString())

//            val json = JSONObject()
            val data = MovementData(comp[1].toFloat(), comp[0].toFloat())
//            json.put(Timestamp(System.currentTimeMillis()).toString(), data)
//            Log.d("json", json.toString())
            saveJson(data.toString())
        }

        step_mid_time = step_cur_time
        prev_steps = step_count

        Log.d("STEP_HIST_XY", step_x.toString() + "," + step_y.toString())

    }

    private fun readJson(fileName: String) {
        val fileName = "${Helper().getLocalUserName(applicationContext)}.json"
        val fileInputStream = openFileInput(fileName)
        val jsonString = fileInputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        Log.d("json read", jsonObject.toString())
    }

    private fun saveJson(jsonString: String) {
        val userNumber: String = Helper().getLocalUserName(applicationContext)
        val fileName = "${userNumber}.json"
        val file = File(filesDir, fileName)
        val jsonObject: JSONObject
        if (file.exists()) {
            val fileInputStream = openFileInput(fileName)
            val jsonFileString = fileInputStream.bufferedReader().use { it.readText() }
            jsonObject = JSONObject(jsonFileString)
            fileInputStream.close()
        }
        else {
            jsonObject = JSONObject()
        }

        Log.d("json file", jsonObject.toString())
        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
        jsonObject.put(Timestamp(System.currentTimeMillis()).toString(), jsonString)
        val jsonOutString = jsonObject.toString()
        fileOutputStream.write(jsonOutString.toByteArray())
        fileOutputStream.close()

        updateTimestampFile(userNumber.toInt())
        Log.d("json", file.toString())
        readJson(fileName)
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

//    fun getLocalUserName(): String {
//        val db : AppDatabase = Room.databaseBuilder(
//            applicationContext,
//            AppDatabase::class.java, "database-name"
//        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
//
//        val user = db.userDao().findActive()
//
//        if (user != null) {
//            return user.username.toString()
//        }
//
//        return "x"
//    }

    fun get_coord(magnitude: Double, degrees: Double): List<Double> {
        val angle = Math.toRadians(degrees)
        val x = magnitude * Math.sin(angle)
        val y = magnitude * Math.cos(angle)
        return listOf(x,y)
    }

    fun get_comp(x: Double, y: Double): List<Double> {
        var adj = 0
        if(x > 0.0 && y < 0.0)
            adj = 90
        else if(x < 0.0 && y < 0.0)
            adj = 180
        else if(x < 0.0 && y > 0.0)
            adj = 270

        val mag = (x.pow(2) + y.pow(2)).pow(0.5)
        val angle = Math.toDegrees(Math.acos(y / mag))

        return listOf(mag, angle)
    }
}
