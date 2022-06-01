package com.IP.SMARTPON

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.widget.Button
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.IP.SMARTPON.models.loginResult
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch


class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val callback: OnBackPressedCallback =
            object : OnBackPressedCallback(true)
            {
                override fun handleOnBackPressed() {
                    finishAffinity()
                }
            }
        this.onBackPressedDispatcher.addCallback(
            this,
            callback
        )
        val sharedPreference =  getSharedPreferences("DATA", Context.MODE_PRIVATE)
        val token = sharedPreference.getString("token",null)
        val intent = Intent(this, Connect::class.java)
        if(token!=null) {
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT > 9) {
            val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
        }
        // get reference to all views
        var et_user_name = findViewById(R.id.et_user_name) as EditText
        var et_password = findViewById(R.id.et_password) as EditText
        var btn_reset = findViewById(R.id.btn_reset) as Button
        var btn_submit = findViewById(R.id.btn_submit) as Button

        btn_reset.setOnClickListener {
            et_user_name.setText("")
            et_password.setText("")
        }

        btn_submit.setOnClickListener {
            val user_name = et_user_name.text;
            val password = et_password.text;

            sharedPreference.edit().putString("token",null).commit()

            run("http://api.vhealth.me/Auth/login", user_name.toString(),password.toString())

            val intent = Intent(this, Connect::class.java)

            val sharedPreference =  getSharedPreferences("DATA", Context.MODE_PRIVATE)
            val token = sharedPreference.getString("token",null)
            if(token  != null) {
                startActivity(intent)
            }else{
                val dlgAlert: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(this)
                dlgAlert.setMessage("Login details are invalid!")
                dlgAlert.setTitle("vHealth")
                dlgAlert.setPositiveButton(
                    "Ok"
                ) { dialog, which -> }
                dlgAlert.setCancelable(true)
                dlgAlert.create().show()
            }
        }

    }
    fun run(url: String, username: String, password: String) {
        val countDownLatch = CountDownLatch(1)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val jsonObject = JSONObject()
        jsonObject.put("username",username)
        jsonObject.put("password", password)

        val body = jsonObject.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                countDownLatch.countDown()
            }

            override fun onResponse(call: Call, response: Response) {
                if(response.code == 200) {
                    val sharedPreference = getSharedPreferences("DATA", Context.MODE_PRIVATE)
                    var editor = sharedPreference.edit()
                    val gson = GsonBuilder().create()
                    editor.putString(
                        "token",
                        gson.fromJson(response.body?.string(), loginResult::class.java).token
                    )
                    editor.commit()
                    countDownLatch.countDown()
                }else{
                    getSharedPreferences("DATA", Context.MODE_PRIVATE).edit().putString("token",null).commit()
                    countDownLatch.countDown()
                }
            }
        })
        countDownLatch.await();
    }

}

