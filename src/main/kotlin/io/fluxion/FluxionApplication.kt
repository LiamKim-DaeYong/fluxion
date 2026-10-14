package io.fluxion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FluxionApplication

fun main(args: Array<String>) {
    runApplication<FluxionApplication>(*args)
}
