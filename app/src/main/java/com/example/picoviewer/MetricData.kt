package com.example.picoviewer

/**
 * Models and parsing for RETINA aggregate metrics text files.
 *
 * **Input shape:** Lines like `Some Label: 42` inside an optional
 * `=== Aggregate Metrics ===` … `=== End Aggregate Metrics ===` block.
 *
 * **Pipeline:** Raw label → [mapToInternalKey] (stable id) → [classify] (UI section, sort order,
 * optional [Metric.displayLabel] so saccadic and manual share the same tile titles). The Compose UI
 * ([MainActivity]) then orders rows by section using fixed key lists, not file order.
 */

/** Which block of the results screen this metric belongs to. */
enum class MetricSection {
    COGNITIVE,
    SACCADIC,
    MANUAL,
    MISC,
}

/**
 * One key/value from the file after parsing.
 *
 * @param key Internal id from [MetricsParser.mapToInternalKey]; drives grouping with [section].
 * @param label Text before the colon in the file (e.g. `"Saccadic SD"`).
 * @param value Text after the colon.
 * @param category Best-effort section name from `=== ... ===` headers (mostly legacy; primary grouping is [section]).
 * @param section Where [MainActivity] shows this metric.
 * @param sectionOrder Sort key within [section]; lower appears first. Unknown/misc extras use large values.
 * @param displayLabel If set, [Metric.tileLabel] uses this instead of [label] (parallel names for saccadic/manual).
 */
data class Metric(
    val key: String,
    val label: String,
    val value: String,
    val category: String = "General",
    val section: MetricSection = MetricSection.MISC,
    val sectionOrder: Int = 999,
    val displayLabel: String? = null,
) {
    /** What the tile shows as the metric name. */
    val tileLabel: String get() = displayLabel ?: label
}

object MetricsParser {
    /**
     * Reads all `Label: value` lines from the aggregate block (or the whole string if markers are missing).
     */
    fun parse(rawData: String): List<Metric> {
        val body = extractAggregateBlock(rawData)
        val lines = body.lines()
        val metrics = mutableListOf<Metric>()

        // Updated when we see a non-end `=== Heading ===` line; retained on [Metric] for debugging.
        var currentCategory = "General"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("===")) {
                // Skip delimiter lines; treat inner headings as weak metadata only.
                if (trimmed.startsWith("===") && !trimmed.contains("End", ignoreCase = true)) {
                    currentCategory = trimmed.replace("===", "").trim()
                }
                continue
            }

            if (trimmed.contains(":")) {
                val parts = trimmed.split(":", limit = 2)
                val rawLabel = parts[0].trim()
                val value = parts[1].trim()
                val key = mapToInternalKey(rawLabel)
                metrics += buildMetric(key, rawLabel, value, currentCategory)
            }
        }
        return metrics
    }

    /**
     * Prefer the bounded region so headers/footers outside the block are never parsed as metrics.
     */
    private fun extractAggregateBlock(rawData: String): String {
        val startMarker = "=== Aggregate Metrics ==="
        val endMarker = "=== End Aggregate Metrics ==="
        val start = rawData.indexOf(startMarker)
        val end = rawData.indexOf(endMarker)
        return if (start >= 0 && end > start) {
            rawData.substring(start + startMarker.length, end)
        } else {
            rawData
        }
    }

    private fun buildMetric(
        key: String,
        rawLabel: String,
        value: String,
        category: String,
    ): Metric {
        val (section, order, displayLabel) = classify(key)
        return Metric(
            key = key,
            label = rawLabel,
            value = value,
            category = category,
            section = section,
            sectionOrder = order,
            displayLabel = displayLabel,
        )
    }

    /**
     * Maps [key] to UI section, order within that section, and optional short [displayLabel].
     * Saccadic and manual rows intentionally use identical [displayLabel] strings for matching metrics.
     */
    private fun classify(key: String): Triple<MetricSection, Int, String?> {
        return when (key) {
            "cog_readiness" -> Triple(MetricSection.COGNITIVE, 0, "Cognitive Readiness")
            "cog_control" -> Triple(MetricSection.COGNITIVE, 1, "Cognitive Control")
            "cog_speed" -> Triple(MetricSection.COGNITIVE, 2, "Cognitive Speed")

            "omission_saccadic" -> Triple(MetricSection.SACCADIC, 0, "Omission errors (%)")
            "commission_saccadic" -> Triple(MetricSection.SACCADIC, 1, "No-go errors (%)")
            "anticipation_saccadic" -> Triple(MetricSection.SACCADIC, 2, "Anticipation errors (%)")
            "sd_saccadic" -> Triple(MetricSection.SACCADIC, 3, "SD")
            "iqr_saccadic" -> Triple(MetricSection.SACCADIC, 4, "IQR")
            "rt_saccadic" -> Triple(MetricSection.SACCADIC, 5, "RT")
            "valid_saccadic" -> Triple(MetricSection.SACCADIC, 6, "Valid RTs (%)")

            "omission_manual" -> Triple(MetricSection.MANUAL, 0, "Omission errors (%)")
            "commission_manual" -> Triple(MetricSection.MANUAL, 1, "No-go errors (%)")
            "anticipation_manual" -> Triple(MetricSection.MANUAL, 2, "Anticipation errors (%)")
            "sd_manual" -> Triple(MetricSection.MANUAL, 3, "SD")
            "iqr_manual" -> Triple(MetricSection.MANUAL, 4, "IQR")
            "rt_manual" -> Triple(MetricSection.MANUAL, 5, "RT")
            "valid_manual" -> Triple(MetricSection.MANUAL, 6, "Valid RTs (%)")

            "commission_fixation" -> Triple(MetricSection.MISC, 0, null)
            "trials_total" -> Triple(MetricSection.MISC, 1, null)
            "trials_valid" -> Triple(MetricSection.MISC, 2, null)
            "gaze_off" -> Triple(MetricSection.MISC, 3, null)
            "fixation_loss" -> Triple(MetricSection.MISC, 4, null)

            // Any unrecognized key: misc bucket, sort after known misc, tie-break by label in UI.
            else -> Triple(MetricSection.MISC, 1000, null)
        }
    }

    /**
     * Normalizes exporter-specific wording (and some legacy no-space variants) to the keys [classify] understands.
     * Unknown labels become a slug of the raw text so they still show up under Miscellaneous.
     */
    private fun mapToInternalKey(label: String): String {
        val l = label.lowercase().replace(" ", "")
        val relaxed = label.lowercase()
        return when {
            relaxed.contains("cognitive readiness") || l == "cognitivereadiness" -> "cog_readiness"
            relaxed.contains("cognitive control") || l == "cognitivecontrol" -> "cog_control"
            relaxed.contains("cognitive speed") || l == "cognitivespeed" -> "cog_speed"

            relaxed.contains("saccadic omission") -> "omission_saccadic"
            relaxed.contains("manual omission") -> "omission_manual"

            relaxed.contains("fixation no-go") || l.contains("fixationcommission") -> "commission_fixation"
            relaxed.contains("saccadic no-go") || l.contains("saccadiccommission") -> "commission_saccadic"
            relaxed.contains("manual no-go") || l.contains("manualcommission") -> "commission_manual"

            relaxed.contains("saccadic anticipation") -> "anticipation_saccadic"
            relaxed.contains("manual anticipation") -> "anticipation_manual"

            relaxed.contains("manual sd") || l.contains("standarddeviationmanual") -> "sd_manual"
            relaxed.contains("saccadic sd") || l.contains("standarddeviationsaccadic") -> "sd_saccadic"

            relaxed.contains("manual iqr") || l.contains("interquartilerangemanual") -> "iqr_manual"
            relaxed.contains("saccadic iqr") || l.contains("interquartilerangesaccadic") -> "iqr_saccadic"

            relaxed.contains("manual rt") || l.contains("medianmanual") -> "rt_manual"
            relaxed.contains("saccadic rt") || l.contains("mediansaccadic") -> "rt_saccadic"

            relaxed.contains("valid manual rts") || l.contains("validmanualrtpercentage") -> "valid_manual"
            relaxed.contains("valid saccadic rts") || l.contains("validsaccadicrtpercentage") -> "valid_saccadic"

            relaxed.contains("total trials") || l == "totaltrials" -> "trials_total"
            relaxed.contains("valid trials") || l == "validtrials" -> "trials_valid"

            relaxed.contains("gaze off center") -> "gaze_off"
            relaxed.contains("fixation loss") -> "fixation_loss"

            else -> label.replace(" ", "_").lowercase()
        }
    }
}
