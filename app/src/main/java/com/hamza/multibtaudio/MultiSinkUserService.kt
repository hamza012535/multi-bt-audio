package com.hamza.multibtaudio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build
import android.util.Log

class MultiSinkUserService(private val suppliedContext: Context? = null) : IMultiSinkControl.Stub() {
    private fun audioManager(): AudioManager? {
        val context = suppliedContext ?: try {
            val thread = Class.forName("android.app.ActivityThread")
            thread.getMethod("currentSystemContext").invoke(null) as? Context
        } catch (_: Throwable) { null }
        return context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun mediaStrategy(strategyClass: Class<*>): Any? {
        val strategies = strategyClass.getMethod("getAudioProductStrategies").invoke(null) as? List<*> ?: return null
        return strategies.firstOrNull { strategy ->
            if (strategy == null) false else try {
                val attrs = strategyClass.getMethod("getAudioAttributes").invoke(strategy)
                val list = when (attrs) {
                    is List<*> -> attrs
                    else -> listOf(attrs)
                }
                list.any { attribute ->
                    attribute != null && attribute.javaClass.getMethod("getUsage").invoke(attribute) == AudioAttributes.USAGE_MEDIA
                }
            } catch (_: Throwable) { false }
        }
    }

    override fun applyPreferredDevices(addresses: Array<out String>?): String {
        if (Build.VERSION.SDK_INT < 31) return "BLOCKED_OS: Android 12+ required."
        if (addresses == null || addresses.size < 2) return "SELECT_TWO: select two speakers."
        return try {
            val manager = audioManager() ?: return "BLOCKED_CONTEXT: system audio service unavailable in Shizuku process."
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val chosen = outputs.filter { device ->
                (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                 device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                 (Build.VERSION.SDK_INT >= 33 && device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) &&
                    addresses.any { it.equals(device.address, true) }
            }
            if (chosen.size < 2) return "WAITING_OUTPUTS: fewer than two selected outputs are connected."
            val strategyClass = Class.forName("android.media.audiopolicy.AudioProductStrategy")
            val strategy = mediaStrategy(strategyClass) ?: return "BLOCKED_API: media audio strategy unavailable on this vendor build."
            val attributesClass = Class.forName("android.media.AudioDeviceAttributes")
            val constructor = attributesClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            val requested = chosen.map { constructor.newInstance(AudioDeviceInfo.ROLE_OUTPUT, it.type, it.address) }
            val method = AudioManager::class.java.getMethod("setPreferredDevicesForStrategy", strategyClass, List::class.java)
            val accepted = method.invoke(manager, strategy, requested) as? Boolean ?: false
            if (accepted) "ROUTING_REQUEST_ACCEPTED: requested ${chosen.size} outputs; verify audible playback on both."
            else "BLOCKED_ROUTING_DENIED: AudioService rejected the request."
        } catch (error: Throwable) {
            val cause = error.cause ?: error
            Log.e("MultiBtAudio", "Audio strategy route failed", error)
            "BLOCKED_VENDOR_API: ${cause.javaClass.simpleName}: ${cause.message ?: "no details"}. HiOS may restrict this API."
        }
    }

    override fun clearPreferredDevices() {
        try {
            if (Build.VERSION.SDK_INT < 31) return
            val manager = audioManager() ?: return
            val strategyClass = Class.forName("android.media.audiopolicy.AudioProductStrategy")
            val strategy = mediaStrategy(strategyClass) ?: return
            AudioManager::class.java.getMethod("removePreferredDeviceForStrategy", strategyClass).invoke(manager, strategy)
        } catch (error: Throwable) { Log.w("MultiBtAudio", "Unable to clear route", error) }
    }
}
