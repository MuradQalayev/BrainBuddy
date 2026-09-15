package com.muradgalayev.brainbuddy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// invite-link round-tripping. the link replaced the security-question challenge, since a
// question like 'what is my surname?' is knowledge-based authentication whose answers are
// usually public, which is why NIST SP 800-63B dropped KBA. a random 128-bit token is a real
// secret, so parsing on the receiving end has to be strict: anything that isn't unmistakably
// our link must resolve to null rather than be half-accepted
class InviteLinkTest {

    @Test
    fun `a minted link round-trips back to its token`() {
        val token = "aB3-_xYz9QwErTyUiOp1"

        val url = TogetherRepository.inviteUrl(token)

        assertEquals(token, TogetherRepository.tokenFromUrl(url))
    }

    @Test
    fun `the link uses the myndora scheme, not the auth callback scheme`() {
        // brainbuddy:// is registered with Supabase Auth and the Google OAuth client, so reusing it
        // for invites would risk breaking sign-in
        val url = TogetherRepository.inviteUrl("token123")

        assertTrue(url, url.startsWith("myndora://connect"))
    }

    @Test
    fun `base64url tokens survive the round trip intact`() {
        // the server emits base64url: '-' and '_' must not be mangled, and there's deliberately no '='
        // padding because it breaks naive link parsers
        val token = "-_aA09zZ-_aA09zZ-_aA"

        assertEquals(token, TogetherRepository.tokenFromUrl(TogetherRepository.inviteUrl(token)))
    }

    @Test
    fun `extra query parameters after the token are not swallowed`() {
        val parsed = TogetherRepository.tokenFromUrl("myndora://connect?token=abc123&ref=whatsapp")

        assertEquals("abc123", parsed)
    }

    @Test
    fun `the auth callback link is not mistaken for an invite`() {
        assertNull(TogetherRepository.tokenFromUrl("brainbuddy://auth-callback#access_token=xyz"))
    }

    @Test
    fun `a link from another app is rejected`() {
        assertNull(TogetherRepository.tokenFromUrl("https://example.com/connect?token=abc123"))
        assertNull(TogetherRepository.tokenFromUrl("otherapp://connect?token=abc123"))
    }

    @Test
    fun `our scheme with a different host is rejected`() {
        assertNull(TogetherRepository.tokenFromUrl("myndora://settings?token=abc123"))
    }

    @Test
    fun `a link with no token resolves to null rather than an empty token`() {
        assertNull(TogetherRepository.tokenFromUrl("myndora://connect"))
        assertNull(TogetherRepository.tokenFromUrl("myndora://connect?token="))
        assertNull(TogetherRepository.tokenFromUrl("myndora://connect?ref=whatsapp"))
    }

    @Test
    fun `null and blank input are handled without throwing`() {
        assertNull(TogetherRepository.tokenFromUrl(null))
        assertNull(TogetherRepository.tokenFromUrl(""))
        assertNull(TogetherRepository.tokenFromUrl("   "))
    }

    @Test
    fun `surrounding whitespace from a pasted link is tolerated`() {
        val parsed = TogetherRepository.tokenFromUrl("  myndora://connect?token=abc123  ")

        assertEquals("abc123", parsed)
    }

    @Test
    fun `a whole forwarded message yields the token inside it`() {
        val message = "Let's connect on Myndora! Tap this private invite link:\nmyndora://connect?token=aB3-_xYz9QwErTyUiOp1"

        assertEquals("aB3-_xYz9QwErTyUiOp1", TogetherRepository.tokenFromPastedText(message))
    }

    @Test
    fun `a bare token can be pasted on its own`() {
        assertEquals("aB3-_xYz9QwErTyUiOp1", TogetherRepository.tokenFromPastedText("  aB3-_xYz9QwErTyUiOp1 "))
    }

    @Test
    fun `pasted text with no invite in it is rejected`() {
        assertNull(TogetherRepository.tokenFromPastedText("hello"))
        assertNull(TogetherRepository.tokenFromPastedText("see https://example.com/connect?token=abc123"))
        assertNull(TogetherRepository.tokenFromPastedText("two words"))
        assertNull(TogetherRepository.tokenFromPastedText(""))
    }

}
