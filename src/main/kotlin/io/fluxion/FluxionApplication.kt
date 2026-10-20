package io.fluxion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class FluxionApplication

fun main(args: Array<String>) {
    runApplication<FluxionApplication>(*args)
}
