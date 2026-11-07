package com.qiubo.optimaltv.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleBackExitPolicyTest {

    @Test
    fun `first back prompts`() {
        val policy = DoubleBackExitPolicy()

        assertEquals(DoubleBackDecision.PROMPT, policy.onBack(1_000L))
    }

    @Test
    fun `second back inside window exits`() {
        val policy = DoubleBackExitPolicy()
        policy.onBack(1_000L)

        assertEquals(
            DoubleBackDecision.EXIT,
            policy.onBack(1_000L + DOUBLE_BACK_EXIT_WINDOW_MS),
        )
    }

    @Test
    fun `expired second back only prompts again`() {
        val policy = DoubleBackExitPolicy()
        policy.onBack(1_000L)

        assertEquals(
            DoubleBackDecision.PROMPT,
            policy.onBack(1_000L + DOUBLE_BACK_EXIT_WINDOW_MS + 1L),
        )
    }

    @Test
    fun `reset breaks pending double-back sequence`() {
        val policy = DoubleBackExitPolicy()
        policy.onBack(1_000L)
        policy.reset()

        assertEquals(DoubleBackDecision.PROMPT, policy.onBack(1_100L))
    }

    @Test
    fun `non-monotonic clock restarts the prompt window`() {
        val policy = DoubleBackExitPolicy()
        policy.onBack(5_000L)

        assertEquals(DoubleBackDecision.PROMPT, policy.onBack(4_000L))
    }
}
