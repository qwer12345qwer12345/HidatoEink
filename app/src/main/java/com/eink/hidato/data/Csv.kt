package com.eink.hidato.data

/** 轻量 CSV 编解码，用于 Room 中存取整数序列（路径、线索集合等） */
object Csv {

    fun encode(values: Collection<Int>): String = values.joinToString(",")

    fun decode(text: String): List<Int> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        return t.split(",").map { it.trim().toInt() }
    }
}
