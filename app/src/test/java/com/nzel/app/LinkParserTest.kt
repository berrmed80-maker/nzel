package com.nzel.app

import com.nzel.app.dl.LinkParser
import com.nzel.app.dl.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    @Test fun extractsUrlFromSharedText() {
        val t = "Check this out! https://www.youtube.com/watch?v=jNQXAC9IVRw it is great"
        assertEquals("https://www.youtube.com/watch?v=jNQXAC9IVRw", LinkParser.extract(t))
    }

    @Test fun stripsTrailingPunctuation() {
        assertEquals("https://www.tiktok.com/@a/video/123", LinkParser.extract("https://www.tiktok.com/@a/video/123)."))
    }

    @Test fun handlesShorteners() {
        assertEquals("https://youtu.be/jNQXAC9IVRw", LinkParser.extract("https://youtu.be/jNQXAC9IVRw"))
        assertEquals("https://vt.tiktok.com/ZSabc123/", LinkParser.extract("watch https://vt.tiktok.com/ZSabc123/"))
        assertEquals("https://fb.watch/abc123/", LinkParser.extract("https://fb.watch/abc123/"))
    }

    @Test fun returnsNullOnNoUrl() {
        assertNull(LinkParser.extract("no link here"))
        assertNull(LinkParser.extract(null))
        assertNull(LinkParser.extract(""))
    }

    @Test fun validation() {
        assertTrue(LinkParser.isValid("https://www.instagram.com/reel/abc/"))
        assertFalse(LinkParser.isValid("not a url"))
        assertFalse(LinkParser.isValid("https://x"))
    }

    @Test fun platformDetection() {
        assertEquals(Platform.YOUTUBE, LinkParser.platform("https://www.youtube.com/watch?v=1"))
        assertEquals(Platform.YOUTUBE, LinkParser.platform("https://youtu.be/1"))
        assertEquals(Platform.INSTAGRAM, LinkParser.platform("https://www.instagram.com/reel/1/"))
        assertEquals(Platform.TIKTOK, LinkParser.platform("https://www.tiktok.com/@u/video/1"))
        assertEquals(Platform.TIKTOK, LinkParser.platform("https://vm.tiktok.com/abc"))
        assertEquals(Platform.FACEBOOK, LinkParser.platform("https://www.facebook.com/reel/1"))
        assertEquals(Platform.FACEBOOK, LinkParser.platform("https://fb.watch/1/"))
        assertEquals(Platform.X, LinkParser.platform("https://x.com/u/status/1"))
        assertEquals(Platform.X, LinkParser.platform("https://twitter.com/u/status/1"))
        assertEquals(Platform.OTHER, LinkParser.platform("https://vimeo.com/123"))
    }
}
