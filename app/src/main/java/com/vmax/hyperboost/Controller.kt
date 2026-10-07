package com.vmax.hyperboost

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Kind { PHONE, BLUETOOTH, WIRED, USB, HDMI, HEARING }

data class DeviceUi(
    val key: String,
    val name: String,
    val typeLabel: String,
    val kind: Kind,
    val isActive: Boolean,
    val boost: Int
)

data class UiState(
    val enabled: Boolean = false,
    val devices: List<DeviceUi> = emptyList(),
    val paired: List<String> = emptyList(),
    val error: String? = null
)

object Controller {
    /** 100% в интерфейсе = +15 дБ */
    const val MAX_MB = 1500

    private lateinit var ctx: Context
    private lateinit var am: AudioManager
    private lateinit var prefs: SharedPreferences
    private var enhancer: LoudnessEnhancer? = null
    private var inited = false

    private val handler = Handler(Looper.getMainLooper())
    private var pollUsers = 0
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000)
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    fun init(c: Context) {
        if (inited) return
        inited = true
        ctx = c.applicationContext
        am = ctx.getSystemService(AudioManager::class.java)
        prefs = ctx.getSharedPreferences("boost", Context.MODE_PRIVATE)
        _state.value = _state.value.copy(enabled = prefs.getBoolean("enabled", false))
        am.registerAudioDeviceCallback(callback, handler)
        refresh()
    }

    fun acquirePolling() {
        if (pollUsers++ == 0) handler.post(poll)
    }

    fun releasePolling() {
        pollUsers--
        if (pollUsers <= 0) {
            pollUsers = 0
            handler.removeCallbacks(poll)
        }
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        _state.value = _state.value.copy(enabled = on)
        applyGain()
    }

    fun setBoost(key: String, percent: Int) {
        prefs.edit().putInt("b_$key", percent.coerceIn(0, 100)).apply()
        refresh()
    }

    private fun kindOf(d: AudioDeviceInfo): Kind? = when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Kind.PHONE
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> Kind.BLUETOOTH
        AudioDeviceInfo.TYPE_HEARING_AID -> Kind.HEARING
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> Kind.WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> Kind.USB
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> Kind.HDMI
        else -> null
    }

    private fun typeLabel(d: AudioDeviceInfo, k: Kind) = when (k) {
        Kind.PHONE -> "Встроенный динамик"
        Kind.BLUETOOTH -> if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) "Bluetooth" else "Bluetooth LE"
        Kind.WIRED -> "Проводное аудио"
        Kind.USB -> "USB-аудио"
        Kind.HDMI -> "HDMI"
        Kind.HEARING -> "Слуховой аппарат"
    }

    private fun priority(k: Kind) = when (k) {
        Kind.BLUETOOTH, Kind.HEARING -> 0
        Kind.USB, Kind.HDMI -> 1
        Kind.WIRED -> 2
        Kind.PHONE -> 3
    }

    fun refresh() {
        if (!inited) return
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { kindOf(it) != null }

        var routedIds: Set<Int> = emptySet()
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                routedIds = am.getAudioDevicesForAttributes(attrs).map { it.id }.toSet()
            } catch (_: Throwable) {
            }
        }
        val activeId: Int? = outs.firstOrNull { it.id in routedIds }?.id
            ?: outs.minByOrNull { priority(kindOf(it)!!) }?.id

        val list = outs.map { d ->
            val k = kindOf(d)!!
            val key = "${d.type}:${d.address}"
            val name = if (k == Kind.PHONE) "Динамики телефона"
            else d.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeLabel(d, k)
            DeviceUi(key, name, typeLabel(d, k), k, d.id == activeId, prefs.getInt("b_$key", 0))
        }.distinctBy { it.key }
            .sortedWith(compareByDescending<DeviceUi> { it.isActive }.thenBy { it.name })

        _state.value = _state.value.copy(devices = list, paired = pairedNames(list.map { it.name }))
        applyGain()
    }

    private fun pairedNames(connected: List<String>): List<String> {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        if (!ok) return emptyList()
        return try {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
                ?.mapNotNull { it.name }
                ?.filter { it !in connected }
                ?.sorted() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun applyGain() {
        val s = _state.value
        val boost = if (s.enabled) s.devices.firstOrNull { it.isActive }?.boost ?: 0 else 0
        try {
            if (boost <= 0 && enhancer == null) return
            val e = enhancer ?: LoudnessEnhancer(0).also { enhancer = it }
            e.setTargetGain(boost * MAX_MB / 100)
            e.enabled = boost > 0
            if (s.error != null) _state.value = _state.value.copy(error = null)
        } catch (t: Throwable) {
            try { enhancer?.release() } catch (_: Throwable) {}
            enhancer = null
            _state.value = _state.value.copy(
                error = "Система не разрешила глобальный эффект усиления. Попробуйте перезапустить плеер."
            )
        }
    }
}
