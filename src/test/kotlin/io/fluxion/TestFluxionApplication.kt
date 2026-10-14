package io.fluxion

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<FluxionApplication>().with(TestcontainersConfiguration::class).run(*args)
}
