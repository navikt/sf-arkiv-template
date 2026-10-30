package no.nav.sf.arkiv.api.model

data class HenteResponse(
    val id: Long,
    val dato: String,
    val opprettetAv: String,
    val kilde: String,
    val dokumentasjonId: String,
    val dokumentasjon: String,
    val dokumentdato: String,
    val aktoerid: String,
    val fnr: String,
    val orgnr: String,
    val tema: String,
)
