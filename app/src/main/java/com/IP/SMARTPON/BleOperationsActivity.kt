package com.IP.SMARTPON

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.IP.SMARTPON.BLE.ConnectionEventListener
import com.IP.SMARTPON.BLE.ConnectionManager
import com.IP.SMARTPON.BLE.isNotifiable
import kotlinx.android.synthetic.main.activity_ble_operations.log_scroll_view
import kotlinx.android.synthetic.main.activity_ble_operations.log_text_view
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jetbrains.anko.alert
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch

class BleOperationsActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var device: BluetoothDevice
    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        }?.filter { u -> u.uuid.toString() == "0000ffe1-0000-1000-8000-00805f9b34fb"} ?: listOf()
    }
    private val characteristicProperties by lazy {
        characteristics.map { characteristic ->
            characteristic to mutableListOf<CharacteristicProperty>().apply {

                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)

            }.toList()
        }.toMap()
    }

    private var notifyingCharacteristics = mutableListOf<UUID>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ConnectionManager.registerListener(connectionEventListener)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_operations)
        var btn_stop = findViewById(R.id.btn_STOP) as Button

        btn_stop.setOnClickListener {
            ConnectionManager.teardownConnection(device)
            val intent = Intent(this, Connect::class.java)
            startActivity(intent)
        }
        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")


        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.ble_playground)
        }
        //setupRecyclerView()
        val characteristic = ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        }?.filter { u -> u.uuid.toString() == "0000ffe1-0000-1000-8000-00805f9b34fb"} ?: listOf()
        ConnectionManager.enableNotifications(device, characteristic[0])
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        val formattedMessage = String.format("%s: %s", dateFormatter.format(Date()), message)
        runOnUiThread {
            val currentLogText = if (log_text_view.text.isEmpty()) {
                "Beginning of log."
            } else {
                log_text_view.text
            }
            log_text_view.text = "$currentLogText\n$formattedMessage"
            log_scroll_view.post { log_scroll_view.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    alert {
                        title = "Disconnected"
                        message = "Disconnected from device."
                        positiveButton("OK") { onBackPressed() }
                    }.show()
                }
            }

            onCharacteristicChanged = { _, characteristic ->
                val date = String(characteristic.value)
                val typeD = date.substring(0,1)
                val value = date.substring(2)
                if(typeD == "t") {
                    run("http://api.vhealth.me/temp",value.toFloat().toInt())
                    log("Temperatura: ${value.toFloat().toInt()}°C")
                }
                if(typeD == "u"){
                    run("http://api.vhealth.me/umiditate",value.toFloat().toInt())
                    log("Umiditate: ${value.toFloat().toInt()}%")
                }
                if(typeD == "b"){
                    run("http://api.vhealth.me/puls",value.toInt())
                    log("BPM: ${value}" )
                }
            }
        }
    }

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Read"
                Writable -> "Write"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun EditText.showKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requestFocus()
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.toUpperCase(Locale.US).toInt(16).toByte() }.toByteArray()

    fun run(url: String, valoare: Int) {
        val countDownLatch = CountDownLatch(1)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val jsonObject = JSONObject()
        jsonObject.put("valoare",valoare)

        val sharedPreference =  getSharedPreferences("DATA", Context.MODE_PRIVATE)
        val token = sharedPreference.getString("token",null)
        val body = jsonObject.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .header("Authorization", "bearer "+token)
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                countDownLatch.countDown()
            }
            override fun onResponse(call: Call, response: Response) {
                countDownLatch.countDown()
            }
        })
        countDownLatch.await();
    }
}
