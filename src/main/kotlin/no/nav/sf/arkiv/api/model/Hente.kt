package no.nav.sf.arkiv.api.model

data class Hente(
    val id: String = "",
    val kilde: String = "",
    val dokumentasjonId: String = "",
    val dokumentdato: String = "",
    val aktoerid: String = "",
    val fnr: String = "",
    val orgnr: String = "",
    val tema: String = "",
)

fun Hente.isEmpty(): Boolean = listOf(id, kilde, dokumentasjonId, dokumentdato, aktoerid, fnr, orgnr, tema).all { it.isEmpty() }

fun Hente.hasValidDokumentDato(): Boolean = dokumentdato.isEmpty() || isValidDay(dokumentdato)
