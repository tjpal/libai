package dev.tjpal.ai.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SandboxSystemIntegrationTest {
    @Test
    fun `Sandbox System Integration Test`() {
        val dockerHost = System.getenv("DOCKER_HOST")
            ?: fail(
                "Missing environment variable DOCKER_HOST. " +
                    "Set it to the Podman socket (for example: unix:///path/to/podman.sock)."
            )

        if (dockerHost.isBlank()) {
            fail(
                "Environment variable DOCKER_HOST is blank. " +
                    "Set it to the Podman socket (for example: unix:///path/to/podman.sock)."
            )
        }

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
