package dev.behradhz.meowzix.data.recommendation

import android.media.AudioDeviceInfo
import dev.behradhz.meowzix.domain.recommendation.OutputClass
import org.junit.Assert.*
import org.junit.Test

class AndroidAudioOutputContextTest {
    @Test fun routesAreClassifiedOnlyWhenUnambiguous() {
        val classify = AndroidAudioOutputContext.Companion::classifyRoute
        assertEquals(OutputClass.SPEAKER, classify(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertEquals(OutputClass.WIRED, classify(listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)))
        assertEquals(OutputClass.BLUETOOTH, classify(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        assertNull(classify(emptyList()))
        assertNull(classify(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        assertNull(classify(listOf(AudioDeviceInfo.TYPE_HDMI)))
    }
}
