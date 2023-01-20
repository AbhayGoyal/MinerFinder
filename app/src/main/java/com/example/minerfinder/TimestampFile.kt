package com.example.minerfinder

//import com.example.minecomms.databinding.ActivityConnectionBinding
import android.Manifest
import android.app.PendingIntent.getActivity
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
import androidx.camera.core.impl.utils.ContextUtil.getApplicationContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.minerfinder.databinding.ActivitySensorsBinding
import com.example.minerfinder.db.AppDatabase
import org.json.JSONObject
import java.io.*
import java.sql.Timestamp
import kotlin.math.pow


class TimestampFile : AppCompatActivity(){
//    private fun saveJson(jsonString: String) {
//        val fileName = "${Helper().getLocalUserName(applicationContext)}.json"
//        val file = File(filesDir, fileName)
//        val jsonObject: JSONObject
//        if (file.exists()) {
//            val fileInputStream = openFileInput(fileName)
//            val jsonFileString = fileInputStream.bufferedReader().use { it.readText() }
//            jsonObject = JSONObject(jsonFileString)
//            fileInputStream.close()
//        }
//        else {
//            jsonObject = JSONObject()
//        }
//
//        Log.d("json file", jsonObject.toString())
//        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
//        jsonObject.put(Timestamp(System.currentTimeMillis()).toString(), jsonString)
//        val jsonOutString = jsonObject.toString()
//        fileOutputStream.write(jsonOutString.toByteArray())
//        fileOutputStream.close()
//
//        readJson(fileName)
//    }
}