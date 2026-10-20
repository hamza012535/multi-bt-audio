package com.hamza.multibtaudio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var audioManager: AudioManager
    private lateinit var outputList: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        buildUi()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        } else refreshOutputs()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) refreshOutputs()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(22))
            setBackgroundColor(0xFFF7F8FA.toInt())
        }
        root.addView(TextView(this).apply {
            text = "Multi BT Audio"; textSize = 28f
            setTextColor(0xFF172033.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Bluetooth output checker"; textSize = 15f
            setTextColor(0xFF526078.toInt()); setPadding(0, dp(4), 0, dp(20))
        })
        statusText = TextView(this).apply {
            textSize = 16f; setTextColor(0xFF172033.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(0xFFE7ECF4.toInt())
        }
        root.addView(statusText, matchWrap())
        root.addView(TextView(this).apply {
            text = "Detected audio outputs"; textSize = 19f
            setTextColor(0xFF172033.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(8))
        })
        outputList = TextView(this).apply {
            textSize = 15f; setTextColor(0xFF273247.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(0xFFFFFFFF.toInt())
        }
        root.addView(outputList, matchWrap())
        root.addView(Button(this).apply {
            text = "Refresh audio outputs"; setOnClickListener { refreshOutputs() }
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "Open Bluetooth settings"
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "Important limitation\nThis app can inspect available audio outputs and open your phone's Bluetooth settings. Android does not let an ordinary app force all phone audio to multiple standard Bluetooth speakers. Simultaneous playback depends on your phone's system audio-routing support and compatible speakers. This first build does not duplicate system audio."
            textSize = 14f; setTextColor(0xFF5B4636.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(0xFFFFF0DD.toInt())
        }, matchWrap())
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun refreshOutputs() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val relevant = devices.filter { isRelevant(it.type) }
        statusText.text = if (relevant.any { isBluetooth(it.type) })
            "Bluetooth output detected. Check your phone's native audio-sharing features for multi-device playback."
        else "No Bluetooth audio output is currently exposed to apps. Connect your speakers, then refresh."
        outputList.text = if (relevant.isEmpty()) "No audio outputs reported." else
            relevant.mapIndexed { index, device ->
                val label = try { device.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeName(device.type) }
                    catch (_: SecurityException) { typeName(device.type) }
                "${index + 1}. $label\n   ${typeName(device.type)}"
            }.joinToString("\n\n")
    }

    private fun isRelevant(type: Int) = isBluetooth(type) ||
        type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
        type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
        type == AudioDeviceInfo.TYPE_USB_DEVICE

    private fun isBluetooth(type: Int) =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
        (Build.VERSION.SDK_INT >= 33 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER)

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth hands-free"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        else -> if (Build.VERSION.SDK_INT >= 33 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER) "Bluetooth LE speaker" else "Audio device type $type"
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
