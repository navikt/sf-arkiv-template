package no.nav.sf.arkiv.diagnostics.model

data class DatabaseSourceDiagnostic(
    val source: String,
    val rowsLast30Days: Long,
    val rowDataBytesLast30Days: Long,
    val estimatedRowsPerYear: Double,
    val estimatedRowDataBytesPerYear: Double,
)
