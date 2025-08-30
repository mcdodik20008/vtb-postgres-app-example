package com.mcdodik.postgresplananalyzer.core.impl

import com.mcdodik.postgresplananalyzer.core.impl.rules.AutovacuumRule
import com.mcdodik.postgresplananalyzer.core.impl.rules.BloatRule
import com.mcdodik.postgresplananalyzer.core.impl.rules.MissingIndexRule
import com.mcdodik.postgresplananalyzer.core.impl.rules.NPlusOneRule
import com.mcdodik.postgresplananalyzer.core.impl.rules.OrderByRule
import com.mcdodik.postgresplananalyzer.core.impl.rules.PartitioningRule
import com.mcdodik.postgresplananalyzer.core.impl.rules.QueryRewriteRule
import com.mcdodik.postgresplananalyzer.core.impl.rules.WorkMemRule

object DefaultRuleset {
    fun build() =
        listOf(
            MissingIndexRule(),
            OrderByRule(),
            QueryRewriteRule(),
            NPlusOneRule(),
            WorkMemRule(),
            AutovacuumRule(),
            PartitioningRule(),
            BloatRule(),
        )
}
