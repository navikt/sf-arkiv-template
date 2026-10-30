package no.nav.sf.arkiv

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.hotspot.DefaultExports

object Metrics {
    val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val requestArkiv = registerLabelCounter("request_arkiv")
    val requestHente = registerLabelCounter("request_hente")
    val insertedEntries = registerLabelCounter("inserted_entries")
    val issues = registerLabelCounter("issues")

    val arkivItem = registerLabelCounter("arkiv_item", "opprettet_av", "kilde", "tema", "konfidentiellt")

    fun registerLabelCounter(
        name: String,
        vararg labels: String,
    ) = Counter
        .build()
        .name(name)
        .help(name)
        .labelNames(*labels)
        .register()

    init {
        DefaultExports.initialize()
    }
}
