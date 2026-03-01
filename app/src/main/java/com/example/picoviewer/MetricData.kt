package com.example.picoviewer

/**
 * Represents a single metric item.
 */
data class Metric(
    val key: String,
    val label: String,
    val value: String,
    val category: String = "General"
)

/**
 * Simple parser for the "Aggregate Metrics" format.
 */
object MetricsParser {
    fun parse(rawData: String): List<Metric> {
        val lines = rawData.lines()
        val metrics = mutableListOf<Metric>()
        
        var currentCategory = "General"
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            // Handle category markers
            if (trimmed.startsWith("===")) {
                currentCategory = trimmed.replace("===", "").trim()
                continue
            }
            
            // Handle key-value pairs (key: value)
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":", limit = 2)
                val rawKey = parts[0].trim()
                val value = parts[1].trim()
                metrics.add(Metric(rawKey, formatKey(rawKey), value, currentCategory))
            }
        }
        return metrics
    }

    /**
     * Converts camelCase or PascalCase keys to readable labels.
     * e.g., "saccadicOmissionPercentage" -> "Saccadic Omission Percentage"
     */
    private fun formatKey(key: String): String {
        // Special case for the gaze/fixation keys which already have spaces
        if (key.contains(" ")) return key

        val result = StringBuilder()
        for (i in key.indices) {
            val c = key[i]
            if (i > 0 && c.isUpperCase() && !key[i-1].isUpperCase()) {
                result.append(" ")
            }
            result.append(if (i == 0) c.uppercaseChar() else c)
        }
        return result.toString()
    }
}
