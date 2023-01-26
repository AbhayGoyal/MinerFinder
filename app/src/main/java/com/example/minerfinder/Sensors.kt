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
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.minerfinder.databinding.ActivitySensorsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.sql.Timestamp
import kotlin.math.abs
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

        GlobalScope.launch(Dispatchers.IO) {
            step_handler()
        }
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
//        step_handler()
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
//        step_handler()
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

    private fun randomPillar(pillar: String, chance: Float): List<Any> {
        val random = java.util.Random()
        val randOdd = random.nextFloat()
        if (chance > randOdd) {
            val randomLetter = 'A' + random.nextInt(('P' - 'A') + 1)
            return listOf(true, randomLetter.toString())
        }
        return listOf(false, pillar)
    }

    private suspend fun step_handler() {
        var lastSteps: Int = step_count
        var currentSteps: Int
        var angle: Int = 0
        var distance: Double = 0.0
        var pillar: String = "A"
        val INTERVAL: Long = 120 // seconds
        while (true) {
            var x: Double = 0.0
            var y: Double = 0.0
            val startTime = System.currentTimeMillis()
            for (i in 0 until INTERVAL) {
                delay(1000)
                Log.d("read 99", step_count.toString())
                currentSteps = step_count - lastSteps
                lastSteps = step_count
                distance = currentSteps * avg_step_size
                angle = getAzimuth()
                val coords = get_coord(distance, angle.toDouble())
                x += coords[0]
                y += coords[1]
                val res = randomPillar(pillar, 0.3f)
                if (abs(coords[0]) > 0 && res[0] as Boolean) {
                    pillar = res[1] as String
                    break
                }
            }
            val comp = get_comp(x, y)
            x = 0.0
            y = 0.0
            // velo in m/s
            val timeDiff = (System.currentTimeMillis() - startTime) / 1000
//            val data = MovementData(comp[1].toFloat(), (comp[0] / timeDiff).toFloat(), pillar)
            val data = comp[1].toFloat().toString() + "," + (comp[0] / timeDiff).toFloat() + "," + pillar
            Log.d("movdata", data.toString())
            saveJson(data.toString())
        }
    }

//    private fun step_handler2() {
//        // set up first ever run
//        if(step_start_time == Timestamp(0)) {
//            step_start_time = Timestamp(System.currentTimeMillis())
//            step_cur_time = step_start_time
//            step_mid_time = step_cur_time
//            prev_steps = step_count
//        }
//        else {
//            step_cur_time = Timestamp(System.currentTimeMillis())
//        }
//
//        // inside 60 sec interval
//        timeDiff = ((step_cur_time.time - step_start_time.time) / 1000.0)
//
//        val mag = (step_count - prev_steps) * avg_step_size / ((step_cur_time.time - step_mid_time.time) / 1000.0)
//        prev_steps = step_count
//        step_mid_time = step_cur_time
//        val angle = getAzimuth()
//        val coord = get_coord(mag, angle.toDouble())
//        Log.d("STEP_HIST_MGC", listOf(mag, angle, coord).toString())
//        this.step_x += coord[0].toInt()
//        this.step_y += coord[1].toInt()
//
//        Log.d("STEP_HIST_XY", step_x.toString() + "," + step_y.toString())
//
//        if(timeDiff >= 5) {
//            step_start_time = step_cur_time
//            val comp = get_comp(step_x, step_y)
//            val ret = listOf<Any>(step_cur_time, comp[0] / timeDiff, comp[1])
//            step_hist.add(ret)
//            step_x = 0.0
//            step_y = 0.0
//            prev_steps = step_count
//
//            Log.d("STEP_HIST_LOG", step_hist.toString())
//
////            val json = JSONObject()
//            val data = MovementData(comp[1].toFloat(), comp[0].toFloat(), "A")
////            json.put(Timestamp(System.currentTimeMillis()).toString(), data)
////            Log.d("json", json.toString())
//            saveJson(data.toString())
//        }
//
//        step_mid_time = step_cur_time
//        prev_steps = step_count
//
//        Log.d("STEP_HIST_XY", step_x.toString() + "," + step_y.toString())
//
//    }

    private fun readJson(fileName: String) {
        val fileName = "${Helper().getLocalUserName(applicationContext)}.json"
        val fileInputStream = openFileInput(fileName)
        val jsonString = fileInputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        Log.d("json read", jsonObject.toString())
//        val pillarRatios30 = ratios(jsonObject, 30*60)
//        val pillarRatios60 = ratios(jsonObject, 60*60)
//        val pillarRatios180 = ratios(jsonObject, 180*60)
//        regionRatios(pillarRatios30, 30*60)
//        regionRatios(pillarRatios60, 60*60)
//        regionRatios(pillarRatios180, 180*60)
        regionHandler(jsonObject)
    }

    private fun saveJson(jsonString: String) {
        val STORAGE_TIME = 3 * 3600 // in seconds: x hours * seconds
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

        // limit ot 40 items in json object for testing
        while (jsonObject.length() > 40) {
            val firstKey = jsonObject.keys().next()
            jsonObject.remove(firstKey)
        }
        while (jsonObject.length() > 0) {
            val firstKey = jsonObject.keys().next()
            if ((Timestamp(System.currentTimeMillis()).time - Timestamp.valueOf(firstKey).time) / 1000 > STORAGE_TIME)
                jsonObject.remove(firstKey)
            else
                break
        }

        val jsonOutString = jsonObject.toString()
        fileOutputStream.write(jsonOutString.toByteArray())
        fileOutputStream.close()


        runOnUiThread {
            displayJson(jsonOutString)
        }

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

    private fun displayJson(m: String) {
        val minerDisplay: TextView = findViewById<TextView>(R.id.miner_data)
        minerDisplay.movementMethod = ScrollingMovementMethod()
        minerDisplay.text = "Miner Data:\n$m"
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
        val x = magnitude * Math.cos(angle)
        val y = magnitude * Math.sin(angle)
        return listOf(x,y)
    }

    fun get_comp(x: Double, y: Double): List<Double> {
//        var adj = 0
//        if(x > 0.0 && y < 0.0)
//            adj = 90
//        else if(x < 0.0 && y < 0.0)
//            adj = 180
//        else if(x < 0.0 && y > 0.0)
//            adj = 270

        val mag = (x.pow(2) + y.pow(2)).pow(0.5)
        var angle = Math.toDegrees(Math.atan2(y, x))
//        val angle = Math.toDegrees(Math.acos(y / mag))
        if (angle < 0)
            angle += 360

        return listOf(mag, angle)
    }

    // timespan in seconds
//    private fun ratios(jsonObject: JSONObject, timespan: Int = 10800) {
//        val pillarCounts = mutableMapOf<String, Int>()
//        var isFirst = true
//        var oldTime = Timestamp(0)
//        var timeDiff = 0
//        for (key in jsonObject.keys()) {
//            val values = jsonObject.get(key).toString().split(",")
//            val pillar = values[2]
//            val time = Timestamp.valueOf(key)
//
//            if (!isFirst) {
//                timeDiff = ((time.time - oldTime.time) / 1000).toInt() // in seconds
//            }
//            else {
//                isFirst = false
//                timeDiff = 120
//            }
//            oldTime = time
//
//            // check if were within timespan
//            if ((System.currentTimeMillis() - time.time) / 1000 > timespan) {
//                continue
//            }
//
//            if (pillarCounts.containsKey(pillar)) {
//                pillarCounts[pillar] = pillarCounts[pillar]!! + timeDiff
//            } else {
//                pillarCounts[pillar] = timeDiff
//            }
//        }
//
//        Log.d("ratios", pillarCounts.toString())
//
//        val total = pillarCounts.values.sum()
//        val letterRatios = pillarCounts.mapValues { (_, count) -> count.toFloat() / total }
//        Log.d("ratios", letterRatios.toString())
//    }

    private fun ratios(jsonObject: JSONObject, timespan: Int = 10800): Map<String, Float> {
        val pillarCounts = mutableMapOf<String, Int>()
        var isFirst = true
        var lastPiller = "none"
        var timeDiff = 0
        for (key in jsonObject.keys()) {
            val values = jsonObject.get(key).toString().split(",")
            val pillar = values[2]
            val time = Timestamp.valueOf(key)

            // check if were within timespan
            if ((System.currentTimeMillis() - time.time) / 1000 > timespan) {
                continue
            }

            // only add if changing pillars
            if (lastPiller == pillar) {
                continue
            }
            lastPiller = pillar

            if (pillarCounts.containsKey(pillar)) {
                pillarCounts[pillar] = pillarCounts[pillar]!! + 1
            } else {
                pillarCounts[pillar] = 1
            }
        }

        Log.d("ratios", pillarCounts.toString())

        val total = pillarCounts.values.sum()
        val letterRatios = pillarCounts.mapValues { (_, count) -> count.toFloat() / total }
        Log.d("ratios", letterRatios.toString())

        return letterRatios
    }

    fun regionRatios(pillarRatios: Map<String, Float>): MutableMap<String, Float> {
        val regions = mutableMapOf("ABCD" to 0f, "EFGH" to 0f, "IJKL" to 0f, "MNOP" to 0f)

        for ((key, value) in pillarRatios) {
            for ((rKey, rValue) in regions) {
                if (rKey.contains(key)) {
                    regions[rKey] = regions[rKey]!! + value
                }
            }
        }

//        runOnUiThread {
//            val regionsDisplay: TextView = findViewById<TextView>(R.id.region_data_30)
//            regionsDisplay.text = "Region Data: " + (timespan / 60).toString() + "\n$regions\n"
//        }

        Log.d("regionsratios", regions.toString())
        return regions
    }

    fun regionHandler(jsonObject: JSONObject) {
        val pillar30 = ratios(jsonObject, 30*60)
        val pillar60 = ratios(jsonObject, 60*60)
        val pillar90 = ratios(jsonObject, 90*60)
        val pillar120 = ratios(jsonObject, 120*60)
        val region30 = regionRatios(pillar30)
        val region60 = regionRatios(pillar60)
        val region90 = regionRatios(pillar90)
        val region120 = regionRatios(pillar120)

        runOnUiThread {
            val regions30Display: TextView = findViewById<TextView>(R.id.region_data30)
            regions30Display.text = "Region Data (30 min):\n${String.format("%.2f",region30)}\n"
            val regions60Display: TextView = findViewById<TextView>(R.id.region_data60)
            regions60Display.text = "Region Data (60 min):\n$region60\n"
//            val regions90Display: TextView = findViewById<TextView>(R.id.region_data90)
//            regions90Display.text = "Region Data (90 min):\n$region90\n"
            val regions120Display: TextView = findViewById<TextView>(R.id.region_data120)
            regions120Display.text = "Region Data (120 min):\n$region120\n"
        }
    }
}
