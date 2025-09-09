package com.mcdodik.postgresplananalyzer.instastructure

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlanNode

object PgExplainParser {
    private val mapper = jacksonObjectMapper()

    fun parseExplainJson(json: String): Plan {
        val arr = mapper.readTree(json)
        val rootObj =
            when {
                arr.isArray && arr.size() > 0 -> arr[0]
                arr.has("Plan") -> arr
                else -> error("Unexpected EXPLAIN JSON structure")
            }
        val planNode = rootObj["Plan"] ?: error("No 'Plan' field in EXPLAIN JSON")
        val root = toNode(planNode)
        val totalCost = planNode.optDouble("Total Cost") ?: 0.0
        return Plan(root = root, totalCost = totalCost)
    }

    private fun toNode(n: JsonNode): PlanNode {
        val children = mutableListOf<PlanNode>()
        n["Plans"]?.takeIf { it.isArray }?.forEach { child -> children += toNode(child) }

        return PlanNode(
            nodeType = n.optText("Node Type") ?: "Unknown",
            relationName = n.optText("Relation Name"),
            schema = n.optText("Schema"),
            alias = n.optText("Alias"),
            filter = n.optText("Filter"),
            indexCond = n.optText("Index Cond"),
            plans = children,
            estimatedRows = n.optDouble("Plan Rows") ?: 0.0,
            planWidth = n.optInt("Plan Width") ?: 0,
            totalCost = n.optDouble("Total Cost") ?: 0.0,
        )
    }

    private fun JsonNode.optText(field: String): String? = this.get(field)?.takeIf { !it.isNull }?.asText()

    private fun JsonNode.optDouble(field: String): Double? = this.get(field)?.takeIf { !it.isNull }?.asDouble()

    private fun JsonNode.optInt(field: String): Int? = this.get(field)?.takeIf { !it.isNull }?.asInt()
}
