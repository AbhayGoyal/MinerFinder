package com.example.minerfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 90

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // bind this activity whit a layout file
        setContentView(R.layout.activity_main)

        // text display on the home screen
        val homeText: TextView = findViewById<TextView>(R.id.home_text)
        homeText.text = "Welcome to Miner Finer!\nMiner #${Helper().getLocalUserName(applicationContext)}"

        // make sure user has necessary permissions to run the app
        checkPermissions()

        // this deletes the miner info files and timestamp file (this is used for TESTING and should
        // be removed or accounted for in production code
        val files = filesDir.listFiles()
        for (file in files) {
            Log.d("filetree", file.toString())
            if (file.toString() == "/data/user/0/com.example.minerfinder/files/1.json" ||
                file.toString() == "/data/user/0/com.example.minerfinder/files/2.json" ||
                file.toString() == "/data/user/0/com.example.minerfinder/files/3.json" ||
                file.toString() == "/data/user/0/com.example.minerfinder/files/4.json" ||
                file.toString() == "/data/user/0/com.example.minerfinder/files/5.json" ||
                file.toString() == "/data/user/0/com.example.minerfinder/files/timestamp.csv") {
                Log.d("filetree", file.readText())

                file.delete()
            }
        }

        // starts the service step counter (tracks the users steps) which runs in the background
        startService(Intent(this, StepCounter::class.java))
    }

    // is called automatically when the app is terminated
    override fun onDestroy() {
        // ends the background service step counter
        stopService(Intent(this, StepCounter::class.java))
        super.onDestroy()
    }

    // checks to see if we have activity recognition permission
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission denied, ask user for permission
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION)
        }
    }

    // each of these view function are called from the layout file whenever a specific button is pressed
    // will start the new intent/page corresponding to that button
    // FUTURE WORK: may be better practice to not call these from the layout file and add an onClickListener
    // instead

    fun connectionView(view: View?) {
        val intent = Intent(this, Connection::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    fun sensorsView(view: View?) {
        val intent = Intent(this, DataDisplay::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    fun accountView(view: View?) {
        val intent = Intent(this, Account::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    fun cameraView(view: View?) {
        val intent = Intent(this, Camera::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    fun photosView(view: View?) {
        val intent = Intent(this, Photos::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    fun sendPhotoView(view: View?) {
        val intent = Intent(this, PhotoConnection::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }
}