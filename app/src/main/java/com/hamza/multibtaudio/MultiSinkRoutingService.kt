package com.hamza.multibtaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

class MultiSinkRoutingService : Service() {
    private lateinit var am: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var remote: IMultiSinkControl? = null
    private var bound = false
    private val prefs by lazy { getSharedPreferences("multi_bt_audio", MODE_PRIVATE) }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IMultiSinkControl.Stub.asInterface(service)
            applyRoute()
        }
        override fun onServiceDisconnected(name: ComponentName?) { remote = null }
    }
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { handler.postDelayed({ applyRoute() }, 900) }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { handler.postDelayed({ applyRoute() }, 1200) }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { handler.postDelayed({ applyRoute() }, 1500) }
    }
    private val tick = object : Runnable {
        override fun run() { applyRoute(); handler.postDelayed(this, 10_000) }
    }

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(AUDIO_SERVICE) as AudioManager
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("multi_bt_route", "Multi-speaker routing", NotificationManager.IMPORTANCE_LOW))
        startForeground(21, notification("Starting Shizuku audio routing"))
        am.registerAudioDeviceCallback(callback, handler)
        try { registerReceiver(receiver, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) } catch (_: Exception) {}
        handler.postDelayed(tick, 2500)
    }

    private fun bindUserService() {
        if (bound) return
        try {
            val args = Shizuku.UserServiceArgs(ComponentName(this, MultiSinkUserService::class.java))
                .tag("multi-bt-audio-routing").version(1).daemon(false)
            Shizuku.bindUserService(args, conn)
            bound = true
        } catch (_: Throwable) { saveStatus("Shizuku user-service binding failed. Check Shizuku status and authorization.") }
    }

    private fun applyRoute() {
        if (!Shizuku.pingBinder()) { saveStatus("Shizuku is not running. Start it through Wireless debugging."); return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { saveStatus("Shizuku permission missing. Authorize Multi BT Audio in Shizuku."); return }
        if (remote == null) { bindUserService(); return }
        val addresses = prefs.getStringSet("selected_addresses", emptySet())?.toTypedArray() ?: emptyArray()
        val result = try { remote?.applyPreferredDevices(addresses) ?: "Shizuku control service unavailable." }
            catch (t: Throwable) { "Routing IPC failed: ${t.message ?: t.javaClass.simpleName}" }
        saveStatus(result ?: "No routing result.")
    }

    private fun saveStatus(value: String) {
        getSharedPreferences("multi_bt_audio_status", MODE_PRIVATE).edit().putString("status", value).apply()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(21, notification(value))
    }

    private fun notification(value: String): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "multi_bt_route") else Notification.Builder(this)
        return b.setContentTitle("Multi BT Audio").setContentText(value.take(120))
            .setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            try { remote?.clearPreferredDevices() } catch (_: Throwable) {}
            stopSelf()
            return START_NOT_STICKY
        }
        applyRoute()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try { am.unregisterAudioDeviceCallback(callback) } catch (_: Exception) {}
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        if (bound) try {
            val args = Shizuku.UserServiceArgs(ComponentName(this, MultiSinkUserService::class.java))
                .tag("multi-bt-audio-routing").version(1).daemon(false)
            Shizuku.unbindUserService(args, conn, true)
        } catch (_: Throwable) {}
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val ACTION_STOP = "com.hamza.multibtaudio.STOP_ROUTING" }
}
