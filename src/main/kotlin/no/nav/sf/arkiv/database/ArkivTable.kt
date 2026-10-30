package no.nav.sf.arkiv.database

import no.nav.sf.arkiv.api.model.Arkiv
import no.nav.sf.arkiv.api.model.ArkivResponse
import no.nav.sf.arkiv.api.model.Hente
import no.nav.sf.arkiv.api.model.HenteResponse
import no.nav.sf.arkiv.api.model.fmt
import no.nav.sf.arkiv.api.model.fmt_onlyDay
import no.nav.sf.arkiv.api.model.summarize
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.joda.time.DateTime

/**
 * Object that reflects the table Arkiv. It resides in Gcp (and backup locally motivates it being compliant to arkivloven)
 * There is identical models ArkivV3 and ArkivV4 that contains older records (only in fss). If you change the name of this
 * object you can access those (given connected to correct db instance in fss).
 * In production:
 * Arkiv - up to date 2026-10-20 found data in fss Arkiv, later found in gcp
 * ArkivV3 - 9219513 records created 2021-12-07T09:37 - 2023-04-21T12:34
 * ArkivV4 - 3148235 records created 2023-04-21T12:34 - 2024-10-09T09:19
 * Any newer records is found in current Arkiv
 */
object ArkivTable : Table("arkiv") {
    val id = long("id").autoIncrement().uniqueIndex()
    val dato = datetime("dato")
    val opprettetAv = varchar("opprettet_av", 50)
    val kilde = varchar("kilde", 43)
    val dokumentasjonId = varchar("dokumentasjonId", 20).index()
    val dokumentasjon = text("dokumentasjon")
    val dokumentdato = datetime("dokumentdato")
    val aktoerid = varchar("aktoerid", 14).index()
    val fnr = varchar("fnr", 13).index()
    val orgnr = varchar("orgnr", 13).index()
    val tema = varchar("tema", 50)
    val konfidentiellt = bool("konfidentiellt")
}

private fun Column<String>.maxLength() = (columnType as VarCharColumnType).colLength

fun Arkiv.tooLongFields(): List<String> =
    listOf(
        Triple("opprettetAv", opprettetAv, ArkivTable.opprettetAv),
        Triple("kilde", kilde, ArkivTable.kilde),
        Triple("dokumentasjonId", dokumentasjonId, ArkivTable.dokumentasjonId),
        Triple("aktoerid", aktoerid, ArkivTable.aktoerid),
        Triple("fnr", fnr, ArkivTable.fnr),
        Triple("orgnr", orgnr, ArkivTable.orgnr),
        Triple("tema", tema, ArkivTable.tema),
    ).filter { (_, value, column) -> value.length > column.maxLength() }
        .map { (name, _, column) -> "$name (max ${column.maxLength()} characters)" }

fun ArkivTable.insert(item: Arkiv): ArkivResponse {
    val createdAt = DateTime.now()
    val documentDate = DateTime.parse(item.dokumentdato, fmt_onlyDay)
    val inserted =
        insert {
            it[dato] = createdAt
            it[opprettetAv] = item.opprettetAv
            it[kilde] = item.kilde
            it[dokumentasjonId] = item.dokumentasjonId
            it[dokumentasjon] = item.dokumentasjon
            it[dokumentdato] = documentDate
            it[aktoerid] = item.aktoerid
            it[fnr] = item.fnr
            it[orgnr] = item.orgnr
            it[tema] = item.tema
            it[konfidentiellt] = item.konfidentiellt
        }
    return ArkivResponse(
        id = inserted[id],
        dato = fmt.print(createdAt),
        opprettetAv = item.opprettetAv,
        kilde = item.kilde,
        dokumentasjonId = item.dokumentasjonId,
        dokumentasjonSummarized = summarize(item.dokumentasjon),
        dokumentdato = fmt_onlyDay.print(documentDate),
        konfidentiellt = item.konfidentiellt,
    )
}

fun ArkivTable.select(filter: Hente): Query =
    selectAll().apply {
        if (filter.id.isNotEmpty()) andWhere { id eq filter.id.toLong() }
        if (filter.kilde.isNotEmpty()) andWhere { kilde eq filter.kilde }
        if (filter.dokumentasjonId.isNotEmpty()) andWhere { dokumentasjonId eq filter.dokumentasjonId }
        if (filter.dokumentdato.isNotEmpty()) andWhere { dokumentdato eq DateTime.parse(filter.dokumentdato, fmt_onlyDay) }
        if (filter.aktoerid.isNotEmpty()) andWhere { aktoerid eq filter.aktoerid }
        if (filter.fnr.isNotEmpty()) andWhere { fnr eq filter.fnr }
        if (filter.orgnr.isNotEmpty()) andWhere { orgnr eq filter.orgnr }
        if (filter.tema.isNotEmpty()) andWhere { tema eq filter.tema }
    }

fun ResultRow.toHenteResponse(): HenteResponse =
    HenteResponse(
        id = this[ArkivTable.id],
        dato = fmt.print(this[ArkivTable.dato]),
        opprettetAv = this[ArkivTable.opprettetAv],
        kilde = this[ArkivTable.kilde],
        dokumentasjonId = this[ArkivTable.dokumentasjonId],
        dokumentasjon = this[ArkivTable.dokumentasjon],
        dokumentdato = fmt_onlyDay.print(this[ArkivTable.dokumentdato]),
        aktoerid = this[ArkivTable.aktoerid],
        fnr = this[ArkivTable.fnr],
        orgnr = this[ArkivTable.orgnr],
        tema = this[ArkivTable.tema],
    )
