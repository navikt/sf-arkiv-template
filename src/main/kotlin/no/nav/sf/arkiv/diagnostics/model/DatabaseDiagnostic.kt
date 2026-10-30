package no.nav.sf.arkiv.diagnostics.model

data class DatabaseDiagnostic(
    val table: String,
    val totalSize: String,
    val totalSizeBytes: Long,
    val rowEstimate: Long,
    val bytesPerRow: Double,
)
