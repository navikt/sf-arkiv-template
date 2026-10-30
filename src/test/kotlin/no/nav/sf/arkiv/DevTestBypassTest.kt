package no.nav.sf.arkiv

import org.junit.Assert.assertEquals
import org.junit.Test

class DevTestBypassTest {
    @Test
    fun `bypass requires dev cluster and explicit true`() {
        assertEquals(System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp", isDev)
        assertEquals(
            isDev && System.getenv("DEV_TEST_BYPASS_ALLOWED") == "true",
            isDevTestBypassAllowed,
        )
    }
}
