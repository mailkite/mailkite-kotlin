// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Minimal zero-dependency JSON parser/serializer. Parses into Map<String, Any?>,
// List<Any?>, String, Long/Double, Boolean and null; serializes the same shapes
// back out. Sufficient for the MailKite JSON API — not a general validator. Pure
// Kotlin/JVM (no Android, no kotlinx.serialization), so it runs in unit tests.

package dev.mailkite.client

internal object Json {

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(value.toString())
            is Double, is Float -> {
                val d = (value as Number).toDouble()
                if (d == Math.rint(d) && !d.isInfinite()) sb.append(d.toLong().toString())
                else sb.append(d.toString())
            }
            is Number -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (e in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, e)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, value.asList())
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun parse(s: String): Any? {
        val p = P(s)
        p.ws()
        val v = p.value()
        p.ws()
        return v
    }

    private class P(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun cur(): Char = s[i]

        fun value(): Any? = when (s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> num()
        }

        fun obj(): MutableMap<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            ws()
            if (cur() == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws()
                i++ // :
                ws()
                m[k] = value()
                ws()
                val c = s[i++]
                if (c == '}') break
            }
            return m
        }

        fun arr(): MutableList<Any?> {
            val a = ArrayList<Any?>()
            i++ // [
            ws()
            if (cur() == ']') { i++; return a }
            while (true) {
                ws()
                a.add(value())
                ws()
                val c = s[i++]
                if (c == ']') break
            }
            return a
        }

        fun str(): String {
            val sb = StringBuilder()
            i++ // opening "
            while (true) {
                val c = s[i++]
                if (c == '"') break
                if (c == '\\') {
                    when (val e = s[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        fun num(): Any {
            val start = i
            while (i < s.length && "+-0123456789.eE".indexOf(s[i]) >= 0) i++
            val n = s.substring(start, i)
            return if (n.contains('.') || n.contains('e') || n.contains('E')) n.toDouble() else n.toLong()
        }
    }
}
