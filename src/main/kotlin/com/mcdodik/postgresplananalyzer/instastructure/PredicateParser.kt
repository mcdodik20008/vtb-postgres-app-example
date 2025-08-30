package com.mcdodik.postgresplananalyzer.instastructure

object PredicateParser {
    // Очень простая эвристика: вытаскиваем колонки из фильтра равенства и сравнения
    private val eq = Regex("""(?i)\b([a-zA-Z_][\w.]*)\s*=\s*[$?]?\d+|\b([a-zA-Z_][\w.]*)\s*=\s*'[^']*'""")
    private val rng = Regex("""(?i)\b([a-zA-Z_][\w.]*)\s*(>|<|>=|<=)\s*[$?]?\d+""")
    private val like = Regex("""(?i)\b([a-zA-Z_][\w.]*)\s+LIKE\s+'([^']+)'""")
    fun equalityColumns(filter: String?): List<String> =
        eq.findAll(filter.orEmpty()).map { it.groups[1]?.value ?: it.groups[2]?.value!! }.toList()

    fun rangeColumns(filter: String?): List<String> =
        rng.findAll(filter.orEmpty()).map { it.groupValues[1] }.toList()

    fun likePatterns(filter: String?): List<Pair<String, String>> =
        like.findAll(filter.orEmpty()).map { it.groupValues[1] to it.groupValues[2] }.toList()
}