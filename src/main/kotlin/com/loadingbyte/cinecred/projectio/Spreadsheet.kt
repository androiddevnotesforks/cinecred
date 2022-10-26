package com.loadingbyte.cinecred.projectio

import com.github.miachm.sods.Borders
import com.loadingbyte.cinecred.common.Severity.ERROR
import com.loadingbyte.cinecred.common.Severity.WARN
import com.loadingbyte.cinecred.common.l10n
import org.apache.commons.csv.CSVFormat
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.IndexedColors
import java.io.StringReader
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.readText


class Spreadsheet(matrix: List<List<String>>) : Iterable<Spreadsheet.Record> {

    private val records: List<Record> = matrix.mapIndexed(::Record)

    val numRecords: Int get() = records.size
    val numColumns: Int get() = records.maxOf { it.cells.size }

    operator fun get(recordNo: Int): Record = records[recordNo]
    operator fun get(recordNo: Int, columnNo: Int): String = records[recordNo].cells[columnNo]

    fun map(transform: (String) -> String): Spreadsheet = Spreadsheet(records.map { it.cells.map(transform) })

    override fun iterator(): Iterator<Record> = records.iterator()

    class Record(val recordNo: Int, val cells: List<String>) {
        fun isNotEmpty() = cells.any { it.isNotEmpty() }
    }

}


// The formats are ordered according to decreasing preference.
val SPREADSHEET_FORMATS = listOf(ExcelFormat("xlsx"), ExcelFormat("xls"), OdsFormat, CsvFormat)


interface SpreadsheetFormat {

    val fileExt: String

    fun read(file: Path): Pair<Spreadsheet, List<ParserMsg>>

    /**
     * @param colWidths Width of some of the columns, in characters.
     */
    fun write(file: Path, spreadsheet: Spreadsheet, rowLooks: Map<Int, RowLook>, colWidths: List<Int>)

    class RowLook(
        val height: Int = -1,
        val fontSize: Int = -1,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val wrap: Boolean = false,
        val borderBottom: Boolean = false
    )

}


class ExcelFormat(override val fileExt: String) : SpreadsheetFormat {

    override fun read(file: Path) = readOfficeDocument(
        file,
        open = { org.apache.poi.ss.usermodel.WorkbookFactory.create(file.toFile(), null, true) },
        getNumSheets = { workbook -> workbook.numberOfSheets },
        read = { workbook ->
            val sheet = workbook.getSheetAt(0)
            val numRows = sheet.lastRowNum + 1
            val numCols = sheet.maxOf { row -> row.lastCellNum } + 1

            val matrix = MutableList(numRows) { MutableList(numCols) { "" } }
            for (row in sheet)
                for (cell in row) {
                    var cellType = cell.cellType!!
                    if (cellType == CellType.FORMULA)
                        cellType = cell.cachedFormulaResultType
                    matrix[row.rowNum][cell.columnIndex] = when (cellType) {
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        CellType.STRING -> cell.stringCellValue
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        CellType.BLANK -> ""
                        CellType.ERROR -> FormulaError.forInt(cell.errorCellValue).string
                        CellType.FORMULA, CellType._NONE -> throw IllegalStateException()
                    }
                }
            Spreadsheet(matrix)
        },
        close = { workbook -> workbook.close() }
    )

    override fun write(
        file: Path, spreadsheet: Spreadsheet, rowLooks: Map<Int, SpreadsheetFormat.RowLook>, colWidths: List<Int>
    ) {
        val xlsx = fileExt == "xlsx"
        val workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(xlsx)
        val sheet = workbook.createSheet(file.nameWithoutExtension)
        val rowStyles = createDeduplicatedRowStyles(workbook, rowLooks)

        for (record in spreadsheet) {
            val rowIdx = record.recordNo
            val row = sheet.createRow(rowIdx)
            val rowStyle = rowStyles[rowIdx]

            for ((colIdx, cellValue) in record.cells.withIndex()) {
                val cell = row.createCell(colIdx)
                try {
                    cell.setCellValue(cellValue.toDouble())
                } catch (_: NumberFormatException) {
                    cell.setCellValue(cellValue)
                }
                rowStyle?.let(cell::setCellStyle)
            }

            rowLooks[rowIdx]?.let { look ->
                if (look.height != -1)
                    row.height = (look.height * 56).toShort()
            }
        }

        for ((col, width) in colWidths.withIndex())
            sheet.setColumnWidth(col, width * 138)

        file.outputStream().use(workbook::write)
    }

    private fun createDeduplicatedRowStyles(
        workbook: org.apache.poi.ss.usermodel.Workbook,
        rowLooks: Map<Int, SpreadsheetFormat.RowLook>
    ): Map<Int, org.apache.poi.ss.usermodel.CellStyle> {
        data class FontKey(val size: Int, val bold: Boolean, val italic: Boolean)
        data class StyleKey(val font: FontKey, val wrap: Boolean, val borderBottom: Boolean)

        val fontsByKey = HashMap<FontKey, org.apache.poi.ss.usermodel.Font>()
        val stylesByKey = HashMap<StyleKey, org.apache.poi.ss.usermodel.CellStyle>()
        val stylesByRowIdx = HashMap<Int, org.apache.poi.ss.usermodel.CellStyle>()
        for ((rowIdx, look) in rowLooks) {
            val fontKey = FontKey(look.fontSize, look.bold, look.italic)
            val styleKey = StyleKey(fontKey, look.wrap, look.borderBottom)
            val font = fontsByKey.computeIfAbsent(fontKey) {
                workbook.createFont().apply {
                    if (fontKey.size != -1)
                        fontHeightInPoints = fontKey.size.toShort()
                    if (fontKey.bold)
                        bold = true
                    if (fontKey.italic)
                        italic = true
                }
            }
            val style = stylesByKey.computeIfAbsent(styleKey) {
                workbook.createCellStyle().apply {
                    setFont(font)
                    if (styleKey.wrap)
                        wrapText = true
                    if (styleKey.borderBottom) {
                        borderBottom = BorderStyle.THIN
                        bottomBorderColor = IndexedColors.BLACK.index
                    }
                }
            }
            stylesByRowIdx[rowIdx] = style
        }

        return stylesByRowIdx
    }

}


object OdsFormat : SpreadsheetFormat {

    override val fileExt = "ods"

    override fun read(file: Path) = readOfficeDocument(
        file,
        open = { com.github.miachm.sods.SpreadSheet(file.toFile()) },
        getNumSheets = { workbook -> workbook.numSheets },
        read = { workbook ->
            val sheet = workbook.getSheet(0)
            Spreadsheet(sheet.dataRange.values.map { cells -> cells.map { it?.toString() ?: "" } })
        },
        close = { }
    )

    override fun write(
        file: Path, spreadsheet: Spreadsheet, rowLooks: Map<Int, SpreadsheetFormat.RowLook>, colWidths: List<Int>
    ) {
        val numRows = spreadsheet.numRecords
        val numCols = spreadsheet.numColumns

        val sheet = com.github.miachm.sods.Sheet(file.nameWithoutExtension, numRows, numCols)
        val cellMatrix = Array(numRows) { row -> Array(numCols) { col -> spreadsheet[row, col].tryToDouble() } }
        sheet.dataRange.values = cellMatrix

        for (record in spreadsheet) {
            val row = record.recordNo
            val style = com.github.miachm.sods.Style()
            rowLooks[row]?.let { look ->
                if (look.fontSize != -1)
                    style.fontSize = look.fontSize
                style.isBold = look.bold
                style.isItalic = look.italic
                style.isWrap = look.wrap
                if (look.borderBottom)
                    style.borders = Borders(false, null, true, "0.75pt solid #000000", false, null, false, null)
            }
            for (col in record.cells.indices)
                sheet.getRange(row, col).style = style
        }

        for ((row, look) in rowLooks)
            if (look.height != -1)
                sheet.setRowHeight(row, look.height.toDouble())
        for ((col, width) in colWidths.withIndex())
            sheet.setColumnWidth(col, width.toDouble())

        val workbook = com.github.miachm.sods.SpreadSheet()
        workbook.appendSheet(sheet)
        workbook.save(file.toFile())
    }

    private fun String.tryToDouble(): Any =
        try {
            toDouble()
        } catch (_: NumberFormatException) {
            this
        }

}


object CsvFormat : SpreadsheetFormat {

    override val fileExt = "csv"

    override fun read(file: Path): Pair<Spreadsheet, List<ParserMsg>> {
        return Pair(read(file.readText()), emptyList())
    }

    fun read(text: String): Spreadsheet {
        // We trim the unicode character "ZERO WIDTH NO-BREAK SPACE" which is added by Excel for some reason.
        val trimmed = text.trimStart(0xFEFF.toChar())

        // Parse the CSV file into a list of CSV records.
        val csvRecords = CSVFormat.DEFAULT.parse(StringReader(trimmed)).records

        // Convert the CSV records to a string matrix and then create a spreadsheet.
        return Spreadsheet(csvRecords.map { rec -> ArrayList<String>(rec.size()).apply { addAll(rec) } })
    }

    override fun write(
        file: Path, spreadsheet: Spreadsheet, rowLooks: Map<Int, SpreadsheetFormat.RowLook>, colWidths: List<Int>
    ) {
        CSVFormat.DEFAULT.print(file, Charsets.UTF_8).use { printer ->
            for (record in spreadsheet)
                printer.printRecord(record.cells)
        }
    }

}


// Helper function to avoid duplicate code.
private inline fun <W> readOfficeDocument(
    file: Path,
    open: () -> W,
    getNumSheets: (W) -> Int,
    read: (W) -> Spreadsheet,
    close: (W) -> Unit
): Pair<Spreadsheet, List<ParserMsg>> {
    val log = mutableListOf<ParserMsg>()

    val workbook = open()
    try {
        val numSheets = getNumSheets(workbook)

        if (numSheets == 0)
            log.add(ParserMsg(null, null, null, ERROR, l10n("projectIO.spreadsheet.noSheet", file)))
        else if (numSheets > 1)
            log.add(ParserMsg(null, null, null, WARN, l10n("projectIO.spreadsheet.multipleSheets", file)))

        return Pair(if (numSheets == 0) Spreadsheet(emptyList()) else read(workbook), log)
    } finally {
        close(workbook)
    }
}
