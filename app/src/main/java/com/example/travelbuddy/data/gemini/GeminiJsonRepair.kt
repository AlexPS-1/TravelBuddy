package com.example.travelbuddy.data.gemini

object GeminiJsonRepair {

    fun normalize(raw: String): String {
        var s = raw.trim()

        // Remove common wrappers
        s = s.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        // Normalize smart quotes
        s = s
            .replace('“', '"')
            .replace('”', '"')
            .replace('’', '\'')
            .replace('‘', '\'')

        // Extract first JSON object if there is extra text
        extractFirstJsonObject(s)?.let { s = it }

        // 🔥 Fix the exact bug you’ve seen: "key":\n"value" -> "key":"value"
        s = s.replace(Regex("\"\\s*:\\s*\\n\\s*"), "\": ")

        // 🔥 Make output single-line to prevent illegal newlines inside quoted strings.
        // This is the simplest reliable fix for multi-line pitch strings.
        s = s.replace("\r\n", "\n").replace("\n", " ")

        return s.trim()
    }

    fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var quote: Char? = null
        var escape = false

        for (i in start until text.length) {
            val ch = text[i]

            if (escape) {
                escape = false
                continue
            }

            if (inString) {
                if (ch == '\\') escape = true
                else if (ch == quote) {
                    inString = false
                    quote = null
                }
                continue
            } else {
                if (ch == '"' || ch == '\'') {
                    inString = true
                    quote = ch
                    continue
                }
            }

            when (ch) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}