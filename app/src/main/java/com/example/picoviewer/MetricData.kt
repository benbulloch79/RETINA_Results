package com.example.picoviewer

/**
 * Represents a single metric item.
 */
data class Metric(
    val key: String,    // Internal key for grouping (e.g., "omission_saccadic")
    val label: String,  // Display label (e.g., "Saccadic Omission Errors (%)")
    val value: String,
    val category: String = "General"
)

/**
 * Parser for the "Aggregate Metrics" format.
 */
object MetricsParser {
    fun parse(rawData: String): List<Metric> {
        val lines = rawData.lines()
        val metrics = mutableListOf<Metric>()
        
        var currentCategory = "General"
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("===")) {
                if (trimmed.startsWith("===") && !trimmed.contains("End")) {
                    currentCategory = trimmed.replace("===", "").trim()
                }
                continue
            }
            
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":", limit = 2)
                val rawLabel = parts[0].trim()
                val value = parts[1].trim()
                
                // Map the raw label to a standard internal key for grouping
                val internalKey = mapToInternalKey(rawLabel)
                metrics.add(Metric(internalKey, rawLabel, value, currentCategory))
            }
        }
        return metrics
    }

    /**
     * Maps various file labels to a consistent internal key for the UI grouping logic.
     */
    private fun mapToInternalKey(label: String): String {
        val l = label.lowercase()
        return when {
            // Cognitive Top Trio
            l.contains("cognitive speed") || l == "cognitivespeed" -> "cog_speed"
            l.contains("cognitive control") || l == "cognitivecontrol" -> "cog_control"
            l.contains("cognitive readiness") || l == "cognitivereadiness" -> "cog_readiness"
            
            // Omissions
            l.contains("saccadic omission") -> "omission_saccadic"
            l.contains("manual omission") -> "omission_manual"
            
            // Commissions / No-Go
            l.contains("fixation no-go") || l.contains("fixationcommission") -> "commission_fixation"
            l.contains("saccadic no-go") || l.contains("saccadiccommission") -> "commission_saccadic"
            l.contains("manual no-go") || l.contains("manualcommission") -> "commission_manual"
            
            // Anticipation
            l.contains("saccadic anticipation") -> "anticipation_saccadic"
            l.contains("manual anticipation") -> "anticipation_manual"
            
            // SD
            l.contains("manual sd") || l.contains("standarddeviationmanual") -> "sd_manual"
            l.contains("saccadic sd") || l.contains("standarddeviationsaccadic") -> "sd_saccadic"
            
            // IQR
            l.contains("manual iqr") || l.contains("interquartilerangemanual") -> "iqr_manual"
            l.contains("saccadic iqr") || l.contains("interquartilerangesaccadic") -> "iqr_saccadic"
            
            // RT / Median
            l.contains("manual rt") || l.contains("medianmanual") -> "rt_manual"
            l.contains("saccadic rt") || l.contains("mediansaccadic") -> "rt_saccadic"
            
            // Valid %
            l.contains("valid manual rts") || l.contains("validmanualrtpercentage") -> "valid_manual"
            l.contains("valid saccadic rts") || l.contains("validsaccadicrtpercentage") -> "valid_saccadic"
            
            // Trials
            l.contains("total trials") || l == "totaltrials" -> "trials_total"
            l.contains("valid trials") || l == "validtrials" -> "trials_valid"
            
            // Gaze / Fixation
            l.contains("gaze off center") -> "gaze_off"
            l.contains("fixation loss") -> "fixation_loss"
            
            else -> label.replace(" ", "_")
        }
    }
}
