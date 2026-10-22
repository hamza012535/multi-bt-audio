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
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var audioManager: AudioManager
    private lateinit var statusText: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var toggle: Button
    private val prefs by lazy { getSharedPreferences("multi_bt_audio", MODE_PRIVATE) }
    private val selected = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        selected.addAll(prefs.getStringSet("selected_addresses", emptySet()) ?: emptySet())
        buildUi()
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        } else refreshDevices()
    }
    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) updateStatus()
        if (::deviceList.isInitialized && (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) refreshDevices()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) refreshDevices()
    }
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(26), dp(20), dp(24)); setBackgroundColor(0xFFF6F7FB.toInt()) }
        root.addView(TextView(this).apply { text = "Multi BT Audio"; textSize = 28f; setTextColor(0xFF172033.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD })
        root.addView(TextView(this).apply { text = "Route media audio to multiple Bluetooth speakers"; textSize = 15f; setTextColor(0xFF526078.toInt()); setPadding(0, dp(5), 0, dp(18)) })
        statusText = TextView(this).apply { textSize = 14f; setTextColor(0xFF253149.toInt()); setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(0xFFE5EBF5.toInt()) }
        root.addView(statusText, matchWrap())
        root.addView(TextView(this).apply { text = "Shizuku setup"; textSize = 19f; setTextColor(0xFF172033.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(18), 0, dp(6)) })
        root.addView(TextView(this).apply { text = "Install Shizuku, start it with Wireless debugging, then authorize this app when prompted. Some vendor builds may reject system audio routing."; textSize = 14f; setTextColor(0xFF4B566C.toInt()) }, matchWrap())
        root.addView(Button(this).apply { text = "Get Shizuku"; setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))) } }, matchWrap())
        root.addView(Button(this).apply { text = "Open Bluetooth settings"; setOnClickListener { try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) } } }, matchWrap())
        root.addView(TextView(this).apply { text = "Paired speakers"; textSize = 19f; setTextColor(0xFF172033.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(18), 0, dp(6)) })
        root.addView(TextView(this).apply { text = "Select at least two paired speakers. Connect both and refresh to see active audio outputs."; textSize = 14f; setTextColor(0xFF4B566C.toInt()) }, matchWrap())
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(deviceList, matchWrap())
        root.addView(Button(this).apply { text = "Refresh devices"; setOnClickListener { refreshDevices() } }, matchWrap())
        toggle = Button(this).apply { text = "Enable Multi-Speaker"; setOnClickListener { toggleRouting() } }
        root.addView(toggle, matchWrap())
        root.addView(TextView(this).apply { text = "A routing request is not proof of audible playback. Test both speakers and reconnect behavior on your phone."; textSize = 13f; setTextColor(0xFF6B4D29.toInt()); setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(0xFFFFF0D9.toInt()) }, matchWrap())
        val scroll = ScrollView(this); scroll.addView(root); setContentView(scroll); updateStatus()
    }
    private fun refreshDevices() {
        deviceList.removeAllViews()
        val bonded = try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) emptyList()
            else adapter?.bondedDevices?.toList().orEmpty()
        } catch (_: SecurityException) { emptyList() }
        val outputs = try { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { isBluetoothOutput(it.type) } } catch (_: SecurityException) { emptyList() }
        bonded.forEach { device ->
            val mac = try { device.address ?: "" } catch (_: SecurityException) { "" }
            val name = try { device.name ?: "Bluetooth speaker" } catch (_: SecurityException) { "Bluetooth speaker" }
            val active = outputs.any { it.address.equals(mac, true) }
            val kind = outputs.firstOrNull { it.address.equals(mac, true) }?.let { typeName(it.type) } ?: "Not exposed as connected audio output"
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(0xFFFFFFFF.toInt()) }
            row.addView(CheckBox(this).apply {
                text = name; isChecked = selected.contains(mac)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(mac) else selected.remove(mac)
                    prefs.edit().putStringSet("selected_addresses", selected.toSet()).apply()
                }
            })
            row.addView(TextView(this).apply { text = "MAC: ${mac.ifBlank { "unavailable" }}\nType: $kind\nRouted status: ${if (active) "Connected output detected (not proof of strategy routing)" else "Not currently exposed as an output"}"; textSize = 12f; setTextColor(0xFF526078.toInt()) })
            deviceList.addView(row, matchWrap())
        }
        if (bonded.isEmpty()) deviceList.addView(TextView(this).apply { text = "No paired Bluetooth devices. Pair speakers in Bluetooth settings."; setPadding(dp(12), dp(12), dp(12), dp(12)) })
        if (outputs.isEmpty()) deviceList.addView(TextView(this).apply { text = "No connected Bluetooth audio outputs detected."; setPadding(dp(10), dp(8), dp(10), dp(8)); setTextColor(0xFF8A4B20.toInt()) })
        updateStatus()
    }
    private fun toggleRouting() {
        if (Build.VERSION.SDK_INT < 31) { statusText.text = "Missing prerequisite: Android 12 or newer is required for this multi-device media routing API."; return }
        if (selected.size < 2) { statusText.text = "Select at least two paired speakers."; return }
        if (!Shizuku.pingBinder()) { statusText.text = "Missing prerequisite: Shizuku is not running. Install it and start it using Wireless debugging."; return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { Shizuku.requestPermission(42); statusText.text = "Approve the Shizuku permission request, then tap Enable Multi-Speaker again."; return }
        startForegroundService(Intent(this, MultiSinkRoutingService::class.java))
        toggle.text = "Multi-Speaker Enabled (tap to stop)"
        toggle.setOnClickListener {
            startService(Intent(this, MultiSinkRoutingService::class.java).setAction(MultiSinkRoutingService.ACTION_STOP))
            toggle.text = "Enable Multi-Speaker"
            toggle.setOnClickListener { toggleRouting() }
            statusText.text = "Routing service stopped."
        }
        statusText.text = "Service started. Check its notification for diagnostics and verify sound on both speakers."
    }
    private fun updateStatus() {
        if (!::statusText.isInitialized) return
        statusText.text = when {
            Build.VERSION.SDK_INT < 31 -> "Unsupported OS path: Android 12+ required."
            !Shizuku.pingBinder() -> "Shizuku is not running. Start it using Wireless debugging."
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Shizuku is running but this app needs authorization."
            else -> getSharedPreferences("multi_bt_audio_status", MODE_PRIVATE).getString("status", "Shizuku authorized. Select two speakers and enable routing.") ?: "No routing status yet."
        }
    }
    private fun isBluetoothOutput(type: Int) = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET || (Build.VERSION.SDK_INT >= 33 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
    private fun typeName(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth hands-free"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "LE Audio headset"
        else -> if (Build.VERSION.SDK_INT >= 33 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER) "LE Audio speaker" else "Bluetooth type $type"
    }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
