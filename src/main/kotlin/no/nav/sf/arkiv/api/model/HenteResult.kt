package no.nav.sf.arkiv.api.model

data class HenteResult(
    val entries: List<HenteResponse>,
    val skippedKonfidentiellt: Long,
)
