package com.mcdodik.postgresplananalyzer.impl

import com.mcdodik.postgresplananalyzer.impl.rules.AutovacuumRule
import com.mcdodik.postgresplananalyzer.impl.rules.BloatRule
import com.mcdodik.postgresplananalyzer.impl.rules.MissingIndexRule
import com.mcdodik.postgresplananalyzer.impl.rules.NPlusOneRule
import com.mcdodik.postgresplananalyzer.impl.rules.OrderByRule
import com.mcdodik.postgresplananalyzer.impl.rules.PartitioningRule
import com.mcdodik.postgresplananalyzer.impl.rules.QueryRewriteRule
import com.mcdodik.postgresplananalyzer.impl.rules.WorkMemRule

object DefaultRuleset {
    fun build() = listOf(
        MissingIndexRule(),
        OrderByRule(),
        QueryRewriteRule(),
        NPlusOneRule(),
        WorkMemRule(),
        AutovacuumRule(),
        PartitioningRule(),
        BloatRule()
    )
}
