package com.example.powerai.domain.eval

import java.util.Locale

internal object EvalTestSummaryFormatter {
    fun containsAllLabel(report: String, label: String): Boolean {
        return report.contains("all:\nlabel=$label")
    }

    fun containsCase(reportOrSummary: String, query: String): Boolean {
        return reportOrSummary.contains("$query -> hitAt3=") ||
            reportOrSummary.contains("[OK] $query -> hitAt3=") ||
            reportOrSummary.contains("[MISS] $query -> hitAt3=")
    }

    fun containsCaseStatus(reportOrSummary: String, query: String, hitAt3: Boolean): Boolean {
        val statusPrefix = if (hitAt3) "[OK] " else "[MISS] "
        return reportOrSummary.contains("$statusPrefix$query -> hitAt3=$hitAt3") ||
            reportOrSummary.contains("$query -> hitAt3=$hitAt3")
    }

    fun containsRetrievedList(reportOrSummary: String): Boolean {
        return reportOrSummary.contains("retrieved=")
    }

    fun containsUnlabeledGroup(reportOrSummary: String): Boolean {
        return reportOrSummary.contains("label=(unlabeled)")
    }

    fun containsMissesSection(report: String): Boolean {
        return report.contains("\n\nmisses:\n")
    }

    fun hasNoMisses(report: String): Boolean {
        return report.contains("\n\nmisses:\n(no misses)")
    }

    fun isNoMissesSummary(summary: String): Boolean {
        return summary == "(no misses)"
    }

    fun hasNoCases(report: String): Boolean {
        return report.contains("all:\n(no cases)")
    }

    fun formatEdgeCaseReport(
        detailed: DetailedEvalResult,
        groupByLabel: Boolean = false,
        highlightMisses: Boolean = false
    ): String = buildString {
        append("all:\n")
        append(
            formatEdgeCaseSummary(
                detailed = detailed,
                groupByLabel = groupByLabel,
                highlightMisses = highlightMisses
            )
        )
        append("\n\nmisses:\n")
        append(
            formatEdgeCaseSummary(
                detailed = detailed,
                groupByLabel = groupByLabel,
                highlightMisses = highlightMisses,
                missesOnly = true
            )
        )
    }

    fun formatEdgeCaseSummary(
        detailed: DetailedEvalResult,
        groupByLabel: Boolean = false,
        highlightMisses: Boolean = false,
        missesOnly: Boolean = false
    ): String {
        val filteredCases = if (missesOnly) {
            detailed.cases.filterNot { it.hitAt3 }
        } else {
            detailed.cases
        }
        val cases = if (groupByLabel) {
            filteredCases.sortedWith(compareBy({ it.label.orEmpty() }, { it.query }))
        } else {
            filteredCases
        }
        if (cases.isEmpty()) {
            return if (missesOnly) "(no misses)" else "(no cases)"
        }
        return if (groupByLabel) {
            cases.groupBy { it.label.orEmpty().ifBlank { "(unlabeled)" } }
                .entries
                .joinToString(separator = "\n") { (label, group) ->
                    buildString {
                        append("label=")
                        append(label)
                        append("\n")
                        append(group.joinToString(separator = "\n") { formatCase(it, highlightMisses) })
                    }
                }
        } else {
            cases.joinToString(separator = "\n") { formatCase(it, highlightMisses) }
        }
    }

    private fun formatCase(case: EvalCaseResult, highlightMisses: Boolean): String {
        val statusPrefix = when {
            !highlightMisses -> ""
            case.hitAt3 -> "[OK] "
            else -> "[MISS] "
        }
        return buildString {
            append(statusPrefix)
            append(case.query)
            append(" -> hitAt3=")
            append(case.hitAt3)
            append(", rr=")
            append(String.format(Locale.US, "%.2f", case.reciprocalRank))
            append(", retrieved=")
            append(case.retrievedIds.joinToString(prefix = "[", postfix = "]"))
        }
    }
}