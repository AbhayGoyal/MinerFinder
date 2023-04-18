// https://towardsdatascience.com/deep-learning-on-your-phone-pytorch-lite-interpreter-for-mobile-platforms-ae73d0b17eaa

package com.example.minerfinder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
//import org.pytorch.IValue
//import org.pytorch.Module


class Model : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model)
    }

//    fun runModel() {
//        val module: java.lang.Module = java.lang.Module.load(assetFilePath(this, "model.ptl"))
//    }
}