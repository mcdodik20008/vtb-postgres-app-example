package com.mcdodik.postgresplananalyzer.instastructure

import com.mcdodik.postgresplananalyzer.model.PlanNode

object PlanUtil {
    fun walk(root: PlanNode, fn: (PlanNode) -> Unit) {
        fn(root); root.plans.forEach { walk(it, fn) }
    }
    fun anyNode(root: PlanNode, types: Set<String>): PlanNode? {
        var res: PlanNode? = null
        walk(root) { if (it.nodeType in types && res == null) res = it }
        return res
    }
}

