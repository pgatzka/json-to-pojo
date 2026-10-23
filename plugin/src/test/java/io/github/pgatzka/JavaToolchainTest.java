package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the unit tests run on the declared Java 25 toolchain.
 */
class JavaToolchainTest {

    @Test
    @DisplayName("1: the JVM running the unit tests is Java 25")
    void runtime_isJava25() {
        assertThat(Runtime.version().feature()).isEqualTo(25);
    }
}
