package no.nav.sf.arkiv.api.model

data class ArkivResponse(
    val id: Long,
    val dato: String,
    val opprettetAv: String,
    val kilde: String,
    val dokumentasjonId: String,
    val dokumentasjonSummarized: String,
    val dokumentdato: String,
    val konfidentiellt: Boolean,
)

fun summarize(dokumentasjon: String): String = "${dokumentasjon.length} characters"
