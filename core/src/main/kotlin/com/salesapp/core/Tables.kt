package com.salesapp.core

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Reads CSV text and Excel (.xlsx) files into rows of cells. */
object Tables {

    /** Reads a file as rows. Excel files start with "PK" (they are zip files); anything else is CSV. */
    fun read(bytes: ByteArray): List<List<String>> =
        if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) readXlsx(bytes)
        else readCsv(String(bytes, Charsets.UTF_8).removePrefix("﻿"))

    /** CSV with quotes. Works with commas, semicolons (German Excel) or tabs. */
    fun readCsv(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val sep = listOf(';', '\t', ',').maxBy { c -> firstLine.count { it == c } }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                sep -> { row.add(cell.toString().trim()); cell.clear() }
                '\r' -> {}
                '\n' -> {
                    row.add(cell.toString().trim()); cell.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        row.add(cell.toString().trim())
        if (row.any { it.isNotEmpty() }) rows.add(row)
        return rows
    }

    fun writeCsv(rows: List<List<String>>): String = rows.joinToString("\n", postfix = "\n") { r ->
        r.joinToString(",") { v ->
            if (v.any { it == ',' || it == '"' || it == '\n' || it == ';' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        }
    }

    /** First sheet of an .xlsx file. Numbers come back as plain text ("12", "1.19"). */
    fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet")) files[e.name] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { xml ->
            val items = parseXml(xml).getElementsByTagNameNS("*", "si")
            (0 until items.length).map { textOf(items.item(it) as Element) }
        } ?: emptyList()
        val sheetName = files.keys.filter { it.startsWith("xl/worksheets/sheet") }
            .minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE } ?: return emptyList()
        val rowEls = parseXml(files.getValue(sheetName)).getElementsByTagNameNS("*", "row")
        val rows = mutableListOf<List<String>>()
        for (r in 0 until rowEls.length) {
            val cells = (rowEls.item(r) as Element).getElementsByTagNameNS("*", "c")
            val row = mutableListOf<String>()
            for (k in 0 until cells.length) {
                val c = cells.item(k) as Element
                val col = columnIndex(c.getAttribute("r")) ?: row.size
                while (row.size < col) row.add("")
                val v = c.getElementsByTagNameNS("*", "v").item(0)?.textContent ?: ""
                row.add(
                    when (c.getAttribute("t")) {
                        "s" -> shared.getOrElse(v.trim().toIntOrNull() ?: -1) { "" }
                        "inlineStr" -> textOf(c)
                        else -> v
                    }.trim()
                )
            }
            if (row.any { it.isNotEmpty() }) rows.add(row)
        }
        return rows
    }

    private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun textOf(el: Element): String {
        val ts = el.getElementsByTagNameNS("*", "t")
        return (0 until ts.length).joinToString("") { ts.item(it).textContent }
    }

    /** "C7" -> 2 */
    private fun columnIndex(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }
}
