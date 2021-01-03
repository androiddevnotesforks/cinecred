package com.loadingbyte.cinecred.ui

import com.loadingbyte.cinecred.Severity
import com.loadingbyte.cinecred.drawer.getSystemFont
import com.loadingbyte.cinecred.project.FontSpec
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.font.TextAttribute
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter


open class Form : JPanel(MigLayout("hidemode 3", "[align right][grow]")) {

    enum class FileType { FILE, DIRECTORY }
    class VerifyResult(val severity: Severity, msg: String) : Exception(msg)

    private class FormRow(val isVisibleFunc: (() -> Boolean)?) {
        val components = mutableListOf<JComponent>()
        var doVerify: (() -> Unit)? = null

        // We keep track of the form rows which are visible and have a verification error (not a warning).
        // One can use this information to determine whether the form is error-free.
        var isVisible = true
            set(value) {
                field = value
                for (comp in components)
                    comp.isVisible = value
            }
        var isErroneous = false
    }

    private val formRows = mutableListOf<FormRow>()
    private val componentToFormRow = mutableMapOf<Component, FormRow>()
    private var submitButton: JButton? = null
    private val changeListeners = mutableListOf<(Component) -> Unit>()

    private fun JTextField.addChangeListener(listener: () -> Unit) {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = listener()
            override fun removeUpdate(e: DocumentEvent) = listener()
            override fun changedUpdate(e: DocumentEvent) = listener()
        })
    }

    fun addTextField(
        label: String,
        grow: Boolean = true,
        isVisible: (() -> Boolean)? = null,
        verify: ((String) -> Unit)? = null
    ): JTextField {
        val field = JTextField().apply { setMinWidth() }
        val constraints = listOf(if (grow) "width 50%" else "")
        addFormRow(label, listOf(field), constraints, isVisible, verify?.let { { it(field.text) } })
        field.addChangeListener { onChange(field) }
        return field
    }

    fun addFilenameField(
        label: String,
        isVisible: (() -> Boolean)? = null,
        verify: ((String) -> Unit)? = null
    ): TextFieldWithFileExts {
        val field = TextFieldWithFileExts()
        addFormRow(label, listOf(field), listOf("width 50%"), isVisible, verify?.let { { it(field.text) } })
        field.addChangeListener { onChange(field) }
        return field
    }

    fun addFileField(
        label: String,
        fileType: FileType,
        isVisible: (() -> Boolean)? = null,
        verify: ((Path) -> Unit)? = null
    ): TextFieldWithFileExts {
        val field = TextFieldWithFileExts()

        val browse = JButton("Browse", FOLDER_ICON)
        browse.addActionListener {
            val fc = JFileChooser()
            fc.fileSelectionMode = when (fileType) {
                FileType.FILE -> JFileChooser.FILES_ONLY
                FileType.DIRECTORY -> JFileChooser.DIRECTORIES_ONLY
            }
            fc.selectedFile = File(field.text)

            if (field.fileExts.isNotEmpty()) {
                fc.isAcceptAllFileFilterUsed = false
                for (fileExt in field.fileExts)
                    fc.addChoosableFileFilter(FileNameExtensionFilter("*.$fileExt", fileExt))
            }

            if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                field.text = fc.selectedFile.absolutePath
                if (field.fileExts.isNotEmpty()) {
                    val selectedFileExts = (fc.fileFilter as FileNameExtensionFilter).extensions
                    field.text = field.text.ensureEndsWith(selectedFileExts.map { ".$it" })
                }
            }
        }

        val extendedVerify = {
            val fileStr = field.text.trim()
            if (fileStr.isEmpty())
                throw VerifyResult(Severity.ERROR, "Path is empty.")
            val file = Path.of(fileStr)
            if (fileType == FileType.FILE && Files.isDirectory(file))
                throw VerifyResult(Severity.ERROR, "Path points to a directory.")
            if (fileType == FileType.DIRECTORY && Files.isRegularFile(file))
                throw VerifyResult(Severity.ERROR, "Path points to a non-directory file.")
            if (verify != null)
                verify(file)
            if (fileType == FileType.FILE && Files.exists(file))
                throw VerifyResult(Severity.WARN, "File already exists and will be overwritten.")
        }

        addFormRow(label, listOf(field, browse), listOf("split, width 50%", ""), isVisible, extendedVerify)
        field.addChangeListener { onChange(field) }

        return field
    }

    fun addSpinner(
        label: String,
        model: SpinnerModel,
        isVisible: (() -> Boolean)? = null,
        verify: ((Any) -> Unit)? = null
    ): JSpinner {
        val field = JSpinner(model).apply { setMinWidth() }
        addFormRow(label, listOf(field), listOf(""), isVisible, verify?.let { { it(field.value) } })
        field.addChangeListener { onChange(field) }
        return field
    }

    fun addCheckBox(
        label: String,
        isVisible: (() -> Boolean)? = null,
        verify: ((Boolean) -> Unit)? = null
    ): JCheckBox {
        val field = JCheckBox()
        addFormRow(label, listOf(field), listOf(""), isVisible, verify?.let { { it(field.isSelected) } })
        field.addActionListener { onChange(field) }
        return field
    }

    @Suppress("UNCHECKED_CAST")
    private class CustomToStringListCellRenderer<E>(val toString: (E?) -> String) : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
            text = toString(value as E?)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <E> addComboBox(
        label: String,
        items: Array<E>,
        toString: (E) -> String = { it.toString() },
        isVisible: (() -> Boolean)? = null,
        verify: ((E?) -> Unit)? = null
    ): JComboBox<E> {
        val field = JComboBox(DefaultComboBoxModel(items)).apply {
            setMinWidth()
            renderer = CustomToStringListCellRenderer<E> { it?.let(toString) ?: "" }
        }
        addFormRow(label, listOf(field), listOf(""), isVisible, verify?.let { { it(field.selectedItem as E?) } })
        field.addActionListener { onChange(field) }
        return field
    }

    fun <E> addComboBoxList(
        label: String,
        items: Array<E>,
        toString: (E) -> String = { it.toString() },
        isVisible: (() -> Boolean)? = null,
        verify: ((List<E?>) -> Unit)? = null
    ): ComboBoxList<E> {
        val addButton = JButton(ADD_ICON)
        val removeButton = JButton(REMOVE_ICON).apply { isEnabled = false }
        val field = ComboBoxList(items, toString, changeListener = { field ->
            removeButton.isEnabled = field.selectedItems.size != 1
            onChange(field)
        })
        addButton.addActionListener { field.selectedItems += items[0] }
        removeButton.addActionListener { field.selectedItems = field.selectedItems.dropLast(1) }
        // Note: We have to manually specify "aligny top" for the ComboBoxList for some reason. If we don't and a
        // multiline verification message occurs, the ComboBoxList does for some reason align itself in the middle.
        addFormRow(
            label, listOf(field, addButton, removeButton), listOf("split, aligny top", "", ""), isVisible,
            verify?.let { { it(field.selectedItems) } }
        )
        return field
    }

    fun addColorChooserButton(
        label: String,
        isVisible: (() -> Boolean)? = null,
        verify: ((Color?) -> Unit)? = null
    ): ColorChooserButton {
        val field = ColorChooserButton(changeListener = ::onChange).apply { setMinWidth() }
        addFormRow(label, listOf(field), listOf(""), isVisible, verify?.let { { it(field.selectedColor) } })
        return field
    }

    fun addFontSpecChooser(
        label: String,
        isVisible: (() -> Boolean)? = null,
        verify: ((FontSpec?) -> Unit)? = null
    ): FontSpecChooserComponents {
        val field = FontSpecChooserComponents(changeListener = ::onChange)

        val extendedVerify = {
            val fontSpec = field.selectedFontSpec
            if (field.projectFamilies.getFamily(fontSpec.name) == null &&
                BUNDLED_FAMILIES.getFamily(fontSpec.name) == null &&
                SYSTEM_FAMILIES.getFamily(fontSpec.name) == null
            ) {
                val substituteFontName = getSystemFont(fontSpec.name).getFontName(Locale.US)
                val msg = "The font \"${fontSpec.name}\" is not available and will be substituted " +
                        "by \"$substituteFontName\"."
                throw VerifyResult(Severity.WARN, msg)
            }
            if (verify != null)
                verify(fontSpec)
        }

        addFormRow(
            label,
            listOf(
                field.familyComboBox, field.fontComboBox,
                JLabel("Color"), field.colorChooserButton,
                JLabel("Height (Px)"), field.heightPxSpinner
            ),
            listOf("", "newline, split", "newline, split", "", "", ""),
            isVisible, extendedVerify
        )

        return field
    }

    private fun addFormRow(
        label: String,
        fields: List<JComponent>,
        constraints: List<String>,
        isVisible: (() -> Boolean)? = null,
        verify: (() -> Unit)? = null
    ) {
        require(fields.size == constraints.size)

        val formRow = FormRow(isVisible)

        val jLabel = JLabel(label)
        formRow.components.add(jLabel)
        add(jLabel, "newline")

        formRow.components.addAll(fields)
        val endlineGroupId = "g" + System.identityHashCode(jLabel)
        val endlineFieldIds = mutableListOf<String>()
        for ((fieldIdx, field) in fields.withIndex()) {
            val fieldConstraints = mutableListOf(constraints[fieldIdx])
            // If the field ends a line, assign it a unique ID. For this, we just use its location in memory. Also add
            // it to the "endlineGroup". These IDs will be used later when positioning the verification components.
            if (fieldIdx == fields.lastIndex || "newline" in constraints.getOrElse(fieldIdx + 1) { "" }) {
                val id = "f" + System.identityHashCode(field).toString()
                fieldConstraints.add("id $endlineGroupId.$id")
                endlineFieldIds.add(id)
            }
            // If this field starts a new line, add a skip constraint to skip the label column.
            if ("newline" in fieldConstraints[0])
                fieldConstraints.add("skip 1")
            add(field, fieldConstraints.joinToString())
        }

        if (verify != null) {
            val verifyIconLabel = JLabel()
            val verifyMsgArea = newLabelTextArea()
            formRow.components.addAll(arrayOf(verifyIconLabel, verifyMsgArea))

            // Position the verification components using coordinates relative to the fields that are at the line ends.
            val iconLabelId = "c${System.identityHashCode(verifyIconLabel)}"
            val startYExpr = "${endlineFieldIds[0]}.y"
            add(verifyIconLabel, "id $iconLabelId, pos ($endlineGroupId.x2 + 3*rel) ($startYExpr + 3)")
            add(verifyMsgArea, "pos ($iconLabelId.x2 + rel) $startYExpr visual.x2 null")

            formRow.doVerify = {
                formRow.isErroneous = false
                try {
                    verify()
                    verifyIconLabel.icon = null
                    verifyMsgArea.text = null
                    for (comp in formRow.components)
                        comp.putClientProperty("JComponent.outline", null)
                } catch (e: VerifyResult) {
                    verifyIconLabel.icon = SEVERITY_ICON[e.severity]
                    verifyMsgArea.text = e.message
                    if (e.severity == Severity.WARN || e.severity == Severity.ERROR) {
                        val outline = if (e.severity == Severity.WARN) "warning" else "error"
                        for (comp in formRow.components)
                            comp.putClientProperty("JComponent.outline", outline)
                    }
                    if (e.severity == Severity.ERROR)
                        formRow.isErroneous = true
                }
            }
        }

        formRows.add(formRow)
        for (comp in formRow.components)
            componentToFormRow[comp] = formRow

        // Verify the initial field contents.
        formRow.doVerify?.invoke()
        // If the form row should initially be invisible, hide it.
        if (formRow.isVisibleFunc?.invoke() == false)
            formRow.isVisible = false
    }

    fun addSubmitButton(label: String) = JButton(label).also { button ->
        submitButton = button
        add(button, "newline, skip 1, span, align left")
    }

    private fun <C : Component> C.setMinWidth() {
        minimumSize = Dimension(120, minimumSize.height)
    }

    fun addChangeListener(changeListener: (Component) -> Unit) {
        changeListeners.add(changeListener)
    }

    fun clearChangeListeners() {
        changeListeners.clear()
    }

    fun onChange(component: Component) {
        componentToFormRow[component]?.doVerify?.invoke()

        for (formRow in formRows)
            formRow.isVisible = formRow.isVisibleFunc?.invoke() ?: true

        // The submit button (if there is one) should only be clickable if there are no verification errors in the form.
        submitButton?.isEnabled = isErrorFree

        // Notify all change listeners.
        for (changeListener in changeListeners)
            changeListener(component)
    }

    val isErrorFree: Boolean
        get() = formRows.all { !it.isVisible || !it.isErroneous }


    class TextFieldWithFileExts : JTextField() {

        private var _fileExts = mutableListOf<String>()

        var fileExts: List<String>
            get() = _fileExts
            set(newFileExts) {
                // When the list of admissible file extensions is changed and the field text doesn't end with an
                // admissible file extension anymore, remove the previous file extension (if there was any) and add
                // the default new one.
                if (!newFileExts.any { text.endsWith(".$it") }) {
                    text = text.ensureDoesntEndWith(fileExts.map { ".$it" })
                    text = text.ensureEndsWith(newFileExts.map { ".$it" })
                }
                _fileExts.clear()
                _fileExts.addAll(newFileExts)
            }

        init {
            // When the user leaves the text field, ensure that it ends with an admissible file extension.
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) {
                    text = text.ensureEndsWith(fileExts.map { ".$it" })
                }
            })
        }

    }


    class ComboBoxList<E>(
        private val items: Array<E>,
        private val toString: (E) -> String,
        private val changeListener: (ComboBoxList<E>) -> Unit
    ) : JPanel(MigLayout("insets 0")) {

        private val comboBoxes = mutableListOf<JComboBox<E>>()

        var selectedItems: List<E?>
            @Suppress("UNCHECKED_CAST")
            get() = comboBoxes.map { it.selectedItem as E? }
            set(sel) {
                while (comboBoxes.size < sel.size) {
                    val comboBox = JComboBox(DefaultComboBoxModel(items)).apply {
                        renderer = CustomToStringListCellRenderer<E> { it?.let(toString) ?: "" }
                        addActionListener { changeListener(this@ComboBoxList) }
                    }
                    comboBoxes.add(comboBox)
                    add(comboBox)
                }
                while (comboBoxes.size > sel.size)
                    remove(comboBoxes.removeLast())
                for ((comboBox, item) in comboBoxes.zip(sel))
                    comboBox.selectedItem = item
                revalidate()
                changeListener(this)
            }

    }


    class ColorChooserButton(private val changeListener: (ColorChooserButton) -> Unit) : JButton(" ") {

        var selectedColor: Color
            get() = background
            set(value) {
                background = value
            }

        init {
            toolTipText = "Click to choose color"

            // Default color
            selectedColor = Color.WHITE

            addActionListener {
                val newColor = JColorChooser.showDialog(null, "Choose Color", selectedColor)
                if (newColor != null) {
                    selectedColor = newColor
                    changeListener(this)
                }
            }
        }

    }


    class FontSpecChooserComponents(changeListener: (Component) -> Unit) {

        var selectedFontSpec: FontSpec
            get() {
                val selectedFont = fontComboBox.selectedItem
                return FontSpec(
                    if (selectedFont is Font) selectedFont.getFontName(Locale.US) else selectedFont as String? ?: "",
                    heightPxSpinner.value as Int, colorChooserButton.selectedColor
                )
            }
            set(value) {
                val family = projectFamilies.getFamily(value.name)
                    ?: BUNDLED_FAMILIES.getFamily(value.name)
                    ?: SYSTEM_FAMILIES.getFamily(value.name)
                if (family != null) {
                    familyComboBox.selectedItem = family
                    fontComboBox.isEditable = false
                    fontComboBox.selectedItem = family.getFont(value.name)
                } else {
                    familyComboBox.selectedItem = null
                    fontComboBox.isEditable = true
                    fontComboBox.selectedItem = value.name
                }
                heightPxSpinner.value = value.heightPx
                colorChooserButton.selectedColor = value.color
            }

        var projectFamilies: FontFamilies = FontFamilies(emptyList())
            set(value) {
                field = value
                populateFamilyComboBox()
            }

        val familyComboBox = JComboBox<FontFamily>().apply {
            maximumRowCount = 20
            // Equip the family combo box with a custom renderer that shows headings.
            renderer = FamilyListCellRenderer()
        }
        val fontComboBox = JComboBox<Any>(emptyArray()).apply {
            maximumRowCount = 20
            renderer = CustomToStringListCellRenderer<Any> { elem ->
                if (elem is Font) elem.getFontName(Locale.US) else elem as String? ?: ""
            }
        }
        val colorChooserButton = ColorChooserButton(changeListener)
        val heightPxSpinner = JSpinner(SpinnerNumberModel(1, 1, null, 1))
            .apply { minimumSize = Dimension(50, minimumSize.height) }

        private var disableFamilyListener = false

        init {
            familyComboBox.addActionListener {
                if (!disableFamilyListener) {
                    val fonts = (familyComboBox.selectedItem as FontFamily?)?.fonts ?: emptyList()
                    fontComboBox.model = DefaultComboBoxModel(fonts.toTypedArray())
                    fontComboBox.isEditable = false
                    changeListener(familyComboBox)
                }
            }
            fontComboBox.addActionListener { changeListener(fontComboBox) }
            heightPxSpinner.addChangeListener { changeListener(heightPxSpinner) }

            populateFamilyComboBox()
        }

        private fun populateFamilyComboBox() {
            // Note: We temporarily disable the family change listener because otherwise,
            // it would trigger multiple times and, even worse, with intermediate states.
            disableFamilyListener = true
            try {
                val selected = selectedFontSpec
                val families = projectFamilies.list + BUNDLED_FAMILIES.list + SYSTEM_FAMILIES.list
                familyComboBox.model = DefaultComboBoxModel(families.toTypedArray())
                selectedFontSpec = selected
            } finally {
                disableFamilyListener = false
            }
        }

        private inner class FamilyListCellRenderer : DefaultListCellRenderer() {

            private val panel = JPanel(MigLayout("insets 0"))
            private val projectHeaderLabel = createHeaderLabel("\u2605 Project Families \u2605")
            private val bundledHeaderLabel = createHeaderLabel("\u2605 Bundled Families \u2605")
            private val systemHeaderLabel = createHeaderLabel("System Families")
            private val noProjectFamiliesLabel = JLabel("(No font files in project dir)").apply {
                foreground = Color.GRAY
            }

            private fun createHeaderLabel(text: String) = JLabel(text).apply {
                foreground = Color.GRAY
                font = font
                    .deriveFont(font.size * 1.25f)
                    .deriveFont(mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON))
            }

            override fun getListCellRendererComponent(
                list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val projectHeaderIdx = 0
                val bundledHeaderIdx = projectHeaderIdx + projectFamilies.list.size
                val systemHeaderIdx = bundledHeaderIdx + BUNDLED_FAMILIES.list.size
                val familyName = (value as FontFamily?)?.familyName ?: ""
                val cell = super.getListCellRendererComponent(list, familyName, index, isSelected, cellHasFocus)
                return if (index == projectHeaderIdx || index == bundledHeaderIdx || index == systemHeaderIdx) {
                    panel.removeAll()
                    if (index == projectHeaderIdx) {
                        panel.add(projectHeaderLabel, "newline 8")
                        if (projectHeaderIdx == bundledHeaderIdx)
                            panel.add(noProjectFamiliesLabel, "newline 2")
                    }
                    if (index == bundledHeaderIdx)
                        panel.add(bundledHeaderLabel, "newline 8")
                    if (index == systemHeaderIdx)
                        panel.add(systemHeaderLabel, "newline 8")
                    panel.add(cell, "newline 2, growx, pushx")
                    panel
                } else
                    cell
            }

        }

    }

}
