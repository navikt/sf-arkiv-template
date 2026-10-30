@file:Suppress("ktlint:standard:property-naming")

package no.nav.sf.arkiv.api.model

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

val fmt: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
val fmt_onlyDay: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

private val dayPattern = Regex("""\d{4}-\d{2}-\d{2}""")

fun isValidDay(value: String?): Boolean =
    value != null && dayPattern.matches(value) && runCatching { DateTime.parse(value, fmt_onlyDay) }.isSuccess
