package dev.tjpal.ai.sandbox

data class ContainerSandboxConfig(
    val imageRepository: String = "busybox",
    val imageTag: String = "latest",
    val pullImageOnCreate: Boolean = true
) {
    val imageName: String
        get() = "$imageRepository:$imageTag"
}
