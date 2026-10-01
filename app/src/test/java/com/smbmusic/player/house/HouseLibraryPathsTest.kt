package com.smbmusic.player.house

import org.junit.Assert.*
import org.junit.Test

class HouseLibraryPathsTest {
    private val root = HouseLibraryPaths.normalizeRoot("nas/John/Shared Music")

    @Test fun `one configured root preserves queue order and special filenames in both directions`() {
        val tracks = listOf("CDs/Album/01 Intro.mp3", "Rap/A+B & C.flac", "Oldies/Crème #1.m4a")
        val urls = tracks.map { HouseLibraryPaths.smb(root, it)!! }
        assertEquals(tracks, urls.map { HouseLibraryPaths.relative(root, it) })
        assertEquals("smb://nas/John/Shared%20Music/Rap/A%2BB%20%26%20C.flac", urls[1])
        assertEquals("CDs/Album/01 Intro.mp3", HouseLibraryPaths.relative(root,
            "smb://NAS/John/Shared Music/CDs/Album/01 Intro.mp3"))
    }

    @Test fun `mapping never guesses a share root or matches a folder name substring`() {
        assertNull(HouseLibraryPaths.relative("", "smb://nas/John/Shared%20Music/Rap/song.mp3"))
        assertNull(HouseLibraryPaths.relative(root, "smb://nas/John/Shared%20Music%20copy/Rap/song.mp3"))
        assertNull(HouseLibraryPaths.relative(root, "smb://other/John/Shared%20Music/Rap/song.mp3"))
        assertNull(HouseLibraryPaths.relative(root, "smb://nas/Elsewhere/Shared%20Music/Rap/song.mp3"))
        assertNull(HouseLibraryPaths.relative(root, root))
    }

    @Test fun `traversal encoded delimiters credentials and foreign resources cannot be transferred`() {
        listOf("../private.mp3", "/Rap/song.mp3", "Rap/../private.mp3", "Rap\\song.mp3", "Rap//song.mp3")
            .forEach { assertNull(it, HouseLibraryPaths.smb(root, it)) }
        listOf("smb://nas/John/Shared%20Music/%2e%2e/private.mp3",
            "smb://nas/John/Shared%20Music/Rap%2Fsong.mp3",
            "smb://user:password@nas/John/Shared%20Music/Rap/song.mp3",
            "https://nas/John/Shared%20Music/Rap/song.mp3")
            .forEach { assertNull(it, HouseLibraryPaths.relative(root, it)) }
    }

    @Test fun `server percent sign is a filename not another URL decoding step`() {
        val relative = "Rap/100%25 sound.mp3"
        val url = HouseLibraryPaths.smb(root, relative)!!
        assertTrue(url.contains("100%2525"))
        assertEquals(relative, HouseLibraryPaths.relative(root, url))
    }
}
