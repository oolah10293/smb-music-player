package com.housemusic.player

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.view.ContextThemeWrapper
import com.housemusic.player.house.HouseRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QueueActionTest {
    @Test fun resumeBusyCompletionAndDisconnectFollowConfirmedState() {
        val button = Button(ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic))
        val extras = Bundle().apply {
            putBoolean(HouseRuntime.EXTRA_CONNECTED, true)
            putBoolean(HouseRuntime.EXTRA_RADIO, true)
            putBoolean(HouseRuntime.EXTRA_RESUME_AVAILABLE, true)
        }
        button.bindQueueAction(extras, "A–Z", false)
        assertEquals("Resume Playlist", button.text.toString())
        assertTrue(button.isEnabled)
        button.bindQueueAction(extras, "A–Z", true)
        assertFalse(button.isEnabled)
        assertEquals("Resuming…", button.text.toString())
        extras.putBoolean(HouseRuntime.EXTRA_RADIO, false)
        button.bindQueueAction(extras, "Z–A", false)
        assertEquals("Z–A", button.text.toString())
        assertTrue(button.isEnabled)
        extras.putBoolean(HouseRuntime.EXTRA_CONNECTED, false)
        button.bindQueueAction(extras, "Z–A", false)
        assertFalse(button.isEnabled)
    }

    @Test fun missingSnapshotAndUncertainRestoreDoNotOfferAnInvalidAction() {
        val button = Button(ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_HouseMusic))
        val extras = Bundle().apply {
            putBoolean(HouseRuntime.EXTRA_CONNECTED, true)
            putBoolean(HouseRuntime.EXTRA_RADIO, true)
        }
        button.bindQueueAction(extras, "A–Z", false)
        assertEquals(View.INVISIBLE, button.visibility)
        assertFalse(button.isEnabled)
        extras.putBoolean(HouseRuntime.EXTRA_RADIO, false)
        extras.putString(HouseRuntime.EXTRA_RESUME_STATUS, "uncertain")
        button.bindQueueAction(extras, "A–Z", false)
        assertEquals(View.INVISIBLE, button.visibility)
        assertFalse(button.isEnabled)
    }
}
