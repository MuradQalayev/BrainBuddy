package com.muradgalayev.brainbuddy.ui.together

import com.muradgalayev.brainbuddy.ui.utils.resolve
import com.muradgalayev.brainbuddy.testing.TestStrings
import com.muradgalayev.brainbuddy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// error copy for Myndora Together. every one of these cases cost real debugging time. the
// original mapper collapsed anything it didn't recognise into 'Couldn't send the request',
// which is how a missing pgcrypto search_path looked identical to being offline. the contract:
// setup failures name the fix, our own DB guard clauses pass through verbatim, and only
// genuinely opaque driver noise falls back
class TogetherErrorMessageTest {

    private val FALLBACK = TestStrings.en(R.string.together_check_failed)

    private fun message(raw: String) =
        Exception(raw).friendlyMessage(R.string.together_check_failed).resolve(TestStrings.en)

    @Test
    fun `guard clause from our own RPC is shown to the user as written`() {
        val raw = """{"code":"P0001","message":"You are already connected"}"""

        assertEquals("You are already connected", message(raw))
    }

    @Test
    fun `crossed requests explain what to do instead`() {
        val raw = """{"message":"They already sent you a request — answer that one instead"}"""

        assertEquals("They already sent you a request — answer that one instead", message(raw))
    }

    @Test
    fun `withdrawing an already-closed request says so`() {
        // the withdraw path now raises rather than silently deleting nothing
        val raw = """{"code":"P0001","message":"That request is no longer open"}"""

        assertEquals("That request is no longer open", message(raw))
    }

    @Test
    fun `missing migration points at the migration, not at a generic failure`() {
        val raw = """{"code":"PGRST202","message":"Could not find the function """ +
            """public.send_connection_request(p_addressee, p_relation) in the schema cache"}"""

        val result = message(raw)

        assertTrue(result, result.contains("SQL migration"))
        assertNotEquals(FALLBACK, result)
    }

    @Test
    fun `missing pgcrypto names the search path, which is the actual fix`() {
        val raw = """{"code":"42883","message":"function gen_salt(unknown) does not exist"}"""

        val result = message(raw)

        assertTrue(result, result.contains("pgcrypto"))
    }

    @Test
    fun `crypt failure is recognised as the same setup problem`() {
        val raw = """{"message":"function crypt(text, text) does not exist"}"""

        assertTrue(message(raw).contains("pgcrypto"))
    }

    @Test
    fun `a message containing escaped quotes is still extracted`() {
        // Postgres quotes identifiers in its errors, and a naive negated character class stopped at
        // the first escaped quote and dumped the whole body into the fallback, which is how 'column
        // reference is ambiguous' surfaced as 'Couldn't create an invite link'
        val raw = """{"code":"42702","message":"column reference \"expires_at\" is ambiguous"}"""

        val result = message(raw)

        assertNotEquals(FALLBACK, result)
        assertTrue(result, result.contains("expires_at"))
        // unescaped on the way out, the user shouldn't see backslashes
        assertTrue(result, !result.contains("\\\""))
    }

    @Test
    fun `an ambiguous column is reported as a migration problem, not user error`() {
        val raw = """{"code":"42702","message":"column reference \"expires_at\" is ambiguous"}"""

        assertTrue(message(raw).contains("re-run the latest SQL migration"))
    }

    @Test
    fun `a missing function is reported as a migration problem`() {
        val raw = """{"message":"function make_interval(hours => integer) does not exist"}"""

        assertTrue(message(raw).contains("re-run the latest SQL migration"))
    }

    @Test
    fun `pgcrypto still wins over the generic migration message`() {
        // gen_salt errors also say 'does not exist', so the more specific clause has to come first or
        // the actionable pgcrypto hint would be lost
        val raw = """{"message":"function gen_salt(unknown) does not exist"}"""

        assertTrue(message(raw).contains("pgcrypto"))
    }

    @Test
    fun `offline is reported as offline rather than as a server error`() {
        val raw = "java.net.UnknownHostException: Unable to resolve host \"xyz.supabase.co\""

        val result = message(raw)

        assertTrue(result, result.contains("offline"))
    }

    @Test
    fun `opaque driver noise falls back to the caller's wording`() {
        val raw = "SQLSTATE(08006) connection reset by peer while reading from socket"

        assertEquals(FALLBACK, message(raw))
    }

    @Test
    fun `a wall of json falls back rather than being dumped on screen`() {
        val raw = "{" + "\"detail\":\"" + "x".repeat(400) + "\"}"

        assertEquals(FALLBACK, message(raw))
    }

    @Test
    fun `transport details and bearer token never reach the UI`() {
        val raw = "POST https://project.supabase.co/rest/v1/rpc Authorization: Bearer secret-token"

        assertEquals(FALLBACK, message(raw))
    }

    @Test
    fun `an exception with no message falls back`() {
        assertEquals(FALLBACK, Exception().friendlyMessage(R.string.together_check_failed).resolve(TestStrings.en))
    }
}
