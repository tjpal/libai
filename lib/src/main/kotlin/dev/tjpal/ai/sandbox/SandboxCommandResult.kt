package dev.tjpal.ai.sandbox

data class SandboxCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val output: String
        get() = buildString {
            append(stdout)
            append(stderr)
        }
}
