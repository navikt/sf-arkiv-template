package no.nav.sf.arkiv.database

import no.nav.sf.arkiv.diagnostics.model.DatabaseDiagnostic
import no.nav.sf.arkiv.diagnostics.model.DatabaseSourceDiagnostic
import org.jetbrains.exposed.sql.Transaction

private val table = ArkivTable.tableName

fun Transaction.tableDiagnostic(): DatabaseDiagnostic =
    checkNotNull(
        exec(
            """
            SELECT pg_size_pretty(pg_total_relation_size(oid)) AS total_size,
                   pg_total_relation_size(oid) AS total_size_bytes,
                   GREATEST(reltuples, 0)::bigint AS row_estimate
            FROM pg_class WHERE oid = '$table'::regclass
            """.trimIndent(),
        ) { result ->
            check(result.next()) { "No diagnostics found for $table" }
            val size = result.getLong("total_size_bytes")
            val rows = result.getLong("row_estimate")
            DatabaseDiagnostic(
                table = table,
                totalSize = result.getString("total_size"),
                totalSizeBytes = size,
                rowEstimate = rows,
                bytesPerRow = if (rows > 0) size.toDouble() / rows else 0.0,
            )
        },
    ) { "No result set for $table diagnostics" }

fun Transaction.sourceDiagnostics(): List<DatabaseSourceDiagnostic> =
    exec(
        """
        SELECT kilde, COUNT(*) AS rows_last_30_days,
               SUM(pg_column_size(a)) AS row_data_bytes
        FROM $table a WHERE dato >= CURRENT_TIMESTAMP - INTERVAL '30 days'
        GROUP BY kilde ORDER BY kilde
        """.trimIndent(),
    ) { result ->
        buildList {
            while (result.next()) {
                val rows = result.getLong("rows_last_30_days")
                val bytes = result.getLong("row_data_bytes")
                add(
                    DatabaseSourceDiagnostic(
                        source = result.getString("kilde"),
                        rowsLast30Days = rows,
                        rowDataBytesLast30Days = bytes,
                        estimatedRowsPerYear = rows * 365.0 / 30,
                        estimatedRowDataBytesPerYear = bytes * 365.0 / 30,
                    ),
                )
            }
        }
    }.orEmpty()
