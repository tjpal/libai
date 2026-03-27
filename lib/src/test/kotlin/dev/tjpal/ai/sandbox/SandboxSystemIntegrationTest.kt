package dev.tjpal.ai.sandbox

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class SandboxSystemIntegrationTest {
    @Test
    fun `Sandbox System Integration Test`() {
        val dockerHost = System.getenv("DOCKER_HOST")
        assumeTrue(
            !dockerHost.isNullOrBlank(),
            "Skipping sandbox integration test because DOCKER_HOST is not configured."
        )

        val dockerClient = DockerClients.createClient()
        val sandbox = Sandbox(dockerClient)
        try {
            val environment = sandbox.createContainerEnvironment()
            val result = environment.runCommand("echo hello from sandbox")

            assertEquals(0, result.exitCode, "Expected echo command to succeed.")
            assertEquals("hello from sandbox", result.stdout.trim(), "Unexpected sandbox command output.")
        } finally {
            sandbox.close()
        }
    }
}
