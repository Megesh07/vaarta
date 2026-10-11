package ai.vaarta.tools.eval

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionTest {
    @Test
    fun `eval version is a non-blank semver-ish string`() {
        assertTrue(EVAL_VERSION.isNotBlank())
        assertTrue(EVAL_VERSION.contains('.'))
    }
}
