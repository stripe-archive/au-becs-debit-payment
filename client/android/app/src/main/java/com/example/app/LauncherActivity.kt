package com.example.app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.app.databinding.LauncherActivityBinding

class LauncherActivity : AppCompatActivity() {

    private val viewBinding: LauncherActivityBinding by lazy {
        LauncherActivityBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        viewBinding.launchCheckoutKotlin.setOnClickListener {
            launchActivity(CheckoutActivityKotlin::class.java)
        }
        viewBinding.launchCheckoutJava.setOnClickListener {
            launchActivity(CheckoutActivityJava::class.java)
        }
    }

    private fun launchActivity(activityClass: Class<out AppCompatActivity>) {
        startActivity(Intent(this, activityClass))
    }
}
