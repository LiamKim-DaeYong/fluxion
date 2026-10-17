package io.fluxion

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTests {
    private val modules = ApplicationModules.of(FluxionApplication::class.java)

    @Test
    fun `application modules follow the declared boundaries`() {
        modules.verify()
    }

    @Test
    fun `prints the module structure`() {
        modules.forEach(::println)
    }
}
