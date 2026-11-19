package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.recommendation.OutputClass
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Only the coarse playback route class is exposed. No MAC address, device name, Bluetooth
 * identity, physical location, microphone data, or motion/speed telemetry is collected.
 */
@Singleton
class AndroidAudioOutputContext @Inject constructor(@ApplicationContext context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun currentClass(): OutputClass? {
        val manager = audioManager ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Unlike an inventory of connected devices, this describes expected media routing.
                val media = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                classifyRoute(manager.getAudioDevicesForAttributes(media).map { it.type })
            } else {
                // On older Android versions the precise route isn't public. Report only an
                // unambiguous connected external type, otherwise leave the context unknown.
                val connected = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
                val external = connected.filterNot {
                    it == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                        it == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                if (external.isNotEmpty()) classifyRoute(external)
                else if (AudioDeviceInfo.TYPE_BUILTIN_SPEAKER in connected) OutputClass.SPEAKER
                else null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    companion object {
        /** No guessing if multiple or unknown routes are present. */
        fun classifyRoute(types: List<Int>): OutputClass? {
            val distinct = types.distinct()
            if (distinct.size != 1) return null
            return when (distinct.singleOrNull()) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputClass.SPEAKER
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> OutputClass.WIRED
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_HEARING_AID,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> OutputClass.BLUETOOTH
                else -> null
            }
        }
    }
}
