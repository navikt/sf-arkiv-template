package no.nav.sf.arkiv.api.model

data class Arkiv(
    var opprettetAv: String = "",
    var kilde: String = "",
    var dokumentasjonId: String = "",
    var dokumentasjon: String = "",
    var dokumentdato: String = "",
    var aktoerid: String = "",
    var fnr: String = "",
    var orgnr: String = "",
    var tema: String = "",
    var konfidentiellt: Boolean = false,
)

fun Arkiv.hasValidDokumentDato(): Boolean = isValidDay(dokumentdato)
