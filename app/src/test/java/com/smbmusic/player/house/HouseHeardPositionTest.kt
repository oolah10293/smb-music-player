package com.smbmusic.player.house

import org.junit.Assert.*
import org.junit.Test

class HouseHeardPositionTest {
    private val first = HouseTrack(1, "Rap/first.mp3", "Same title", "", "", 100_000, 0)
    private val second = HouseTrack(9, "Rap/shuffled-next.mp3", "Same title", "", "", 200_000, 0)
    private fun state(track: HouseTrack = first, position: Long = 50_000, transport: String = "play") =
        HouseState(listOf(first, second), songId = track.id, positionMs = position, transport = transport, ready = true)

    @Test fun advancesFromObservationAndSubtractsActualPlayoutDelayWithCorrectOffsetSign() {
        val heard = HouseHeardPosition()
        heard.observe(state(), 100_000)
        assertEquals(HouseHeardTrack(first.file, 48_525), heard.snapshot(101_000, HouseAudioTiming(3000, 25), 500))
        assertEquals(HouseHeardTrack(first.file, 47_525), heard.snapshot(101_000, HouseAudioTiming(3000, 25), -500))
    }

    @Test fun retainsPreviousIdentityWhenMpdIsAlreadyOnTheNextShuffledSong() {
        val heard = HouseHeardPosition()
        heard.observe(state(position = 99_000), 100_000)
        heard.observe(state(second, 1_000), 102_000)
        assertEquals(HouseHeardTrack(first.file, 98_000), heard.snapshot(102_000, HouseAudioTiming(3000), 0))
        assertEquals(HouseHeardTrack(second.file, 0), heard.snapshot(104_000, HouseAudioTiming(3000), 0))
    }

    @Test fun neverGuessesNextTrackFromQueueOrderWhenNoPollObservedIt() {
        val heard = HouseHeardPosition()
        heard.observe(state(position = 99_000), 100_000)
        assertNull(heard.snapshot(105_000, HouseAudioTiming(3000), 0))
    }

    @Test fun unknownBufferStalePollAndMissingCurrentIdentityDoNotInventACheckpoint() {
        val heard = HouseHeardPosition()
        heard.observe(state(), 100_000)
        assertNull(heard.snapshot(100_000, HouseAudioTiming(), 0))
        assertNull(heard.snapshot(110_001, HouseAudioTiming(3000), 0))
        assertNull(heard.snapshot(99_999, HouseAudioTiming(3000), 0))
        heard.observe(state().copy(songId = 88), 102_000)
        assertNull(heard.snapshot(102_000, HouseAudioTiming(3000), 0))
    }

    @Test fun pausedAndStoppedPositionsNeverAdvanceWithTime() {
        for (transport in listOf("pause", "stop")) {
            val heard = HouseHeardPosition()
            heard.observe(state(position = 25_000, transport = transport), 100_000)
            assertEquals(HouseHeardTrack(first.file, 25_000), heard.snapshot(105_000, HouseAudioTiming(), 0))
        }
    }

    @Test fun seekLeavesUnknownIntervalRatherThanBackProjectingTheNewPositionAcrossIt() {
        val heard = HouseHeardPosition()
        heard.observe(state(position = 20_000), 100_000)
        heard.observe(state(position = 70_000), 102_000)
        heard.observe(state(position = 72_000), 104_000)
        assertNull(heard.snapshot(104_000, HouseAudioTiming(3000), 0))
        assertEquals(HouseHeardTrack(first.file, 70_000), heard.snapshot(105_000, HouseAudioTiming(3000), 0))
    }

    @Test fun manualNextAtBeginningCanRetainTheOldTrackBeforeItsNaturalEnd() {
        val heard = HouseHeardPosition()
        heard.observe(state(position = 20_000), 100_000)
        heard.observe(state(second, 1_000), 102_000)
        assertEquals(HouseHeardTrack(first.file, 19_000), heard.snapshot(102_000, HouseAudioTiming(3000), 0))
    }

    @Test fun repeatOfSameSongRetainsThePreviousPassAcrossBoundary() {
        val heard = HouseHeardPosition()
        heard.observe(state(position = 99_000), 100_000)
        heard.observe(state(position = 1_000), 102_000)
        assertEquals(HouseHeardTrack(first.file, 98_000), heard.snapshot(102_000, HouseAudioTiming(3000), 0))
        assertEquals(HouseHeardTrack(first.file, 0), heard.snapshot(104_000, HouseAudioTiming(3000), 0))
    }

    @Test fun lateOldObservationCannotRollBackCheckpoint() {
        val heard = HouseHeardPosition()
        heard.observe(state(position = 50_000), 100_000)
        heard.observe(state(position = 10_000), 99_000)
        assertEquals(HouseHeardTrack(first.file, 47_000), heard.snapshot(100_000, HouseAudioTiming(3000), 0))
    }
}
