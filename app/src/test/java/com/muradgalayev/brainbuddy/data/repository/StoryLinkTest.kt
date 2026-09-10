package com.muradgalayev.brainbuddy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryLinkTest {

    @Test
    fun `https and http story links are accepted`() {
        assertEquals("https://myndora.app/help", safeStoryLink(" https://myndora.app/help "))
        assertEquals("http://example.com/path?q=1", safeStoryLink("http://example.com/path?q=1"))
    }

    @Test
    fun `unsafe non-web schemes are rejected`() {
        assertNull(safeStoryLink("javascript:alert(1)"))
        assertNull(safeStoryLink("file:///data/user/0/private"))
        assertNull(safeStoryLink("intent://open#Intent;scheme=bank;end"))
    }

    @Test
    fun `links need a host and cannot contain embedded credentials`() {
        assertNull(safeStoryLink("https:///missing-host"))
        assertNull(safeStoryLink("https://user:password@example.com/private"))
    }
}
