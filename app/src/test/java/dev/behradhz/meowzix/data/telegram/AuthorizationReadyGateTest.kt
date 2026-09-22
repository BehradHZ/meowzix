package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationReadyGateTest {
    @Test
    fun `duplicate ready state is suppressed within one authenticated session`() {
        val gate = AuthorizationReadyGate()
        assertTrue(gate.shouldForward(true))
        assertFalse(gate.shouldForward(true))
        assertFalse(gate.shouldForward(true))
    }

    @Test
    fun `leaving ready resets the gate for the next authenticated session`() {
        val gate = AuthorizationReadyGate()
        assertTrue(gate.shouldForward(true))
        assertTrue(gate.shouldForward(false))
        assertTrue(gate.shouldForward(true))
        assertFalse(gate.shouldForward(true))
    }
}
