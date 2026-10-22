package com.hamza.multibtaudio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Binder
import android.util.Log

class MultiSinkUserService : IMultiSinkControl.Stub() {
    override fun applyPreferredDevices(addresses: Array<out String>?): String {
        if (Build.VERSION.SDK_INT < 31) return "BLOCKED_OS: Android 12+ required."
        if (addresses == null || addresses.size < 2) return "SELECT_TWO: select two speakers."
        return try {
            val thread = Class.forName("android.app.ActivityThread")
            val context = thread.getMethod("currentSystemContext").invoke(null)
                ?: return "BLOCKED_CONTEXT: system context unavailable."
            val manager = context.javaClass.getMethod("getSystemService", String::class.java)
                .invoke(context, "audio") as AudioManager
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val chosen = outputs.filter { device ->
                (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                 device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                 (Build.VERSION.SDK_INT >= 33 && device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) &&
                    addresses.any { it.equals(device.address, true) }
            }
            if (chosen.size < 2) return "WAITING_OUTPUTS: fewer than two selected outputs are connected."
            val strategyClass = Class.forName("android.media.audiopolicy.AudioProductStrategy")
            val strategies = strategyClass.getMethod("getAudioProductStrategies").invoke(null) as? List<*>
                ?: return "BLOCKED_API: no audio strategies exposed."
            val media = strategies.firstOrNull { strategy ->
                try {
                    val attributes = strategyClass.getMethod("getAudioAttributes").invoke(strategy)
                    attributes.javaClass.getMethod("getUsage").invoke(attributes) == AudioAttributes.USAGE_MEDIA
                } catch (_: Throwable) { false }
            } ?: return "BLOCKED_API: media strategy unavailable."
            val deviceAttributes = Class.forName("android.media.AudioDeviceAttributes")
            val ctor = deviceAttributes.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            val requested = chosen.map { ctor.newInstance(AudioDeviceInfo.ROLE_OUTPUT, it.type, it.address) }
            val method = AudioManager::class.java.getMethod("setPreferredDevicesForStrategy", strategyClass, List::class.java)
            val accepted = method.invoke(manager, media, requested) as? Boolean ?: false
            if (accepted) "ROUTING_REQUEST_ACCEPTED: requested ${chosen.size} outputs; verify sound on both."
            else "BLOCKED_ROUTING_DENIED: AudioService rejected the request."
        } catch (error: Throwable) {
            val cause = error.cause ?: error
            Log.e("MultiBtAudio", "Audio strategy route failed", error)
            "BLOCKED_VENDOR_API: ${cause.javaClass.simpleName}: ${cause.message ?: "no details"}. HiOS may restrict this API."
        }
    }

    override fun clearPreferredDevices() {
        try {
            val strategyClass = Class.forName("android.media.audiopolicy.AudioProductStrategy")
            val thread = Class.forName("android.app.ActivityThread")
            val context = thread.getMethod("currentSystemContext").invoke(null) ?: return
            val manager = context.javaClass.getMethod("getSystemService", String::class.java).invoke(context, "audio") as AudioManager
            val strategies = strategyClass.getMethod("getAudioProductStrategies").invoke(null) as? List<*> ?: return
            val media = strategies.firstOrNull { strategy ->
                try {
                    val attributes = strategyClass.getMethod("getAudioAttributes").invoke(strategy)
                    attributes.javaClass.getMethod("getUsage").invoke(attributes) == AudioAttributes.USAGE_MEDIA
                } catch (_: Throwable) { false }
            } ?: return
            AudioManager::class.java.getMethod("removePreferredDeviceForStrategy", strategyClass).invoke(manager, media)
        } catch (error: Throwable) { Log.w("MultiBtAudio", "Unable to clear route", error) }
    }
}
