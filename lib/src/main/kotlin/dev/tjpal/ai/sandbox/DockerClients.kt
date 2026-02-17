package dev.tjpal.ai.sandbox

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient

object DockerClients {
    fun createDefaultConfig(): DefaultDockerClientConfig {
        return DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    }

    fun createClient(config: DefaultDockerClientConfig = createDefaultConfig()): DockerClient {
        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .build()

        return DockerClientImpl.getInstance(config, httpClient)
    }
}
