package com.loadingbyte.cinecred.ui.styling

import com.loadingbyte.cinecred.common.Severity
import com.loadingbyte.cinecred.common.l10n
import com.loadingbyte.cinecred.drawer.DrawnProject
import com.loadingbyte.cinecred.drawer.DrawnStageInfo
import com.loadingbyte.cinecred.project.*
import com.loadingbyte.cinecred.ui.ProjectController
import com.loadingbyte.cinecred.ui.helper.*
import kotlinx.collections.immutable.toPersistentList
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.*
import java.util.*
import javax.swing.*
import kotlin.math.max


class EditStylingPanel(private val ctrl: ProjectController) : JPanel() {

    // ========== ENCAPSULATION LEAKS ==========
    @Deprecated("ENCAPSULATION LEAK") val leakedSplitPane: JSplitPane
    @Deprecated("ENCAPSULATION LEAK") val leakedAddPageStyleButton: JButton
    @Deprecated("ENCAPSULATION LEAK") val leakedAddContentStyleButton: JButton
    @Deprecated("ENCAPSULATION LEAK") val leakedRemoveStyleButton: JButton
    @Deprecated("ENCAPSULATION LEAK") val leakedStylingTree get() = stylingTree
    @Deprecated("ENCAPSULATION LEAK") val leakedGlobalForm get() = globalForm
    @Deprecated("ENCAPSULATION LEAK") val leakedPageStyleForm get() = pageStyleForm
    @Deprecated("ENCAPSULATION LEAK") val leakedContentStyleForm get() = contentStyleForm
    @Deprecated("ENCAPSULATION LEAK") val leakedLetterStyleForm get() = letterStyleForm
    // =========================================

    private val keyListeners = mutableListOf<KeyListener>()

    fun onKeyEvent(event: KeyEvent): Boolean =
        keyListeners.any { it.onKeyEvent(event) }

    private val globalForm = StyleForm(Global::class.java)
    private val pageStyleForm = StyleForm(PageStyle::class.java)
    private val contentStyleForm = StyleForm(ContentStyle::class.java)
    private val letterStyleForm = StyleForm(LetterStyle::class.java)

    // Create a panel with the four style editing forms.
    private val rightPanelCards = CardLayout()
    private val rightPanel = JPanel(rightPanelCards).apply {
        add(JScrollPane() /* use a full-blown JScrollPane to match the look of the non-blank cards */, "Blank")
        add(JScrollPane(globalForm), "Global")
        add(JScrollPane(pageStyleForm), "PageStyle")
        add(JScrollPane(contentStyleForm), "ContentStyle")
        add(JScrollPane(letterStyleForm), "LetterStyle")
    }

    private val stylingTree = StylingTree()

    // Wire up an adjuster that we can call when various events happen, upon which it will update the StyleForms.
    private val formAdjuster = StyleFormAdjuster(
        listOf(globalForm, pageStyleForm, contentStyleForm, letterStyleForm),
        getCurrentStyling = ::styling,
        getCurrentStyleInActiveForm = { stylingTree.selected as Style? },
        notifyConstraintViolations = ::updateConstraintViolations
    )

    // Cache the Styling which is currently stored in the tree, so that we don't have to repeatedly regenerate it.
    private var styling: Styling? = null

    // We increase this counter each time a new form is opened. It is used to tell apart multiple edits
    // of the same widget but in different styles.
    private var openCounter = 0

    // Remember the runtime of the latest render of the project, as well as the runtime of the first occurrences of
    // individual scroll styles.
    private var mostRecentRuntime = 0
    private val mostRecentScrollRuntimes = HashMap<String, Int>()

    init {
        stylingTree.onDeselect = ::openBlank
        stylingTree.addSingletonType(
            PRESET_GLOBAL, l10n("ui.styling.globalStyling"), GLOBE_ICON,
            onSelect = { openGlobal(it, globalForm, "Global") }
        )
        stylingTree.addListType(
            PageStyle::class.java, l10n("ui.styling.pageStyles"), FILMSTRIP_ICON,
            onSelect = { openListedStyle(it, pageStyleForm, "PageStyle") },
            objToString = PageStyle::name
        )
        stylingTree.addListType(
            ContentStyle::class.java, l10n("ui.styling.contentStyles"), LAYOUT_ICON,
            onSelect = { openListedStyle(it, contentStyleForm, "ContentStyle") },
            objToString = ContentStyle::name
        )
        stylingTree.addListType(
            LetterStyle::class.java, l10n("ui.styling.letterStyles"), LETTERS_ICON,
            onSelect = { openListedStyle(it, letterStyleForm, "LetterStyle") },
            objToString = LetterStyle::name
        )

        // Add buttons for adding and removing style nodes.
        val addPageStyleButton = newToolbarButtonWithKeyListener(
            SVGIcon.Dual(ADD_ICON, FILMSTRIP_ICON), l10n("ui.styling.addPageStyleTooltip"),
            VK_P, CTRL_DOWN_MASK or SHIFT_DOWN_MASK
        ) {
            addAndSelectStyle(PRESET_PAGE_STYLE.copy(name = l10n("ui.styling.newPageStyleName")))
        }
        val addContentStyleButton = newToolbarButtonWithKeyListener(
            SVGIcon.Dual(ADD_ICON, LAYOUT_ICON), l10n("ui.styling.addContentStyleTooltip"),
            VK_C, CTRL_DOWN_MASK or SHIFT_DOWN_MASK
        ) {
            addAndSelectStyle(PRESET_CONTENT_STYLE.copy(name = l10n("ui.styling.newContentStyleName")))
        }
        val addLetterStyleButton = newToolbarButtonWithKeyListener(
            SVGIcon.Dual(ADD_ICON, LETTERS_ICON), l10n("ui.styling.addLetterStyleTooltip"),
            VK_L, CTRL_DOWN_MASK or SHIFT_DOWN_MASK
        ) {
            addAndSelectStyle(PRESET_LETTER_STYLE.copy(name = l10n("ui.styling.newLetterStyleName")))
        }
        val duplicateStyleButton = newToolbarButton(
            DUPLICATE_ICON, l10n("ui.styling.duplicateStyleTooltip"), VK_D, CTRL_DOWN_MASK, ::duplicateStyle
        )
        val removeStyleButton = newToolbarButton(
            TRASH_ICON, l10n("ui.styling.removeStyleTooltip"), VK_DELETE, 0, ::removeStyle
        )

        // Add contextual keyboard shortcuts for the style duplication and removal buttons.
        stylingTree.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).apply {
            put(KeyStroke.getKeyStroke(VK_D, CTRL_DOWN_MASK), "duplicate")
            put(KeyStroke.getKeyStroke(VK_DELETE, 0), "remove")
        }
        stylingTree.actionMap.put("duplicate", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                duplicateStyle()
            }
        })
        stylingTree.actionMap.put("remove", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                removeStyle()
            }
        })

        // Layout the buttons.
        val buttonPanel = JPanel(WrappingToolbarLayout(gap = 6)).apply {
            add(addPageStyleButton)
            add(addContentStyleButton)
            add(addLetterStyleButton)
            add(duplicateStyleButton)
            add(removeStyleButton)
        }

        // Layout the tree and the button panel.
        val leftPanel = JPanel(BorderLayout(0, 6)).apply {
            add(buttonPanel, BorderLayout.NORTH)
            add(JScrollPane(stylingTree), BorderLayout.CENTER)
            minimumSize = Dimension(100, 0)
        }

        // Put everything together in a split pane.
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, leftPanel, rightPanel)
        // Slightly postpone moving the dividers so that the panes know their height when the dividers are moved.
        SwingUtilities.invokeLater {
            splitPane.setDividerLocation(0.25)
            SwingUtilities.invokeLater { splitPane.setDividerLocation(0.25) }
        }

        layout = MigLayout()
        add(splitPane, "grow, push")

        @Suppress("DEPRECATION")
        leakedSplitPane = splitPane
        @Suppress("DEPRECATION")
        leakedAddPageStyleButton = addPageStyleButton
        @Suppress("DEPRECATION")
        leakedAddContentStyleButton = addContentStyleButton
        @Suppress("DEPRECATION")
        leakedRemoveStyleButton = removeStyleButton
    }

    private fun newToolbarButtonWithKeyListener(
        icon: Icon,
        tooltip: String,
        shortcutKeyCode: Int,
        shortcutModifiers: Int,
        listener: () -> Unit
    ): JButton {
        keyListeners.add(KeyListener(shortcutKeyCode, shortcutModifiers, listener))
        return newToolbarButton(icon, tooltip, shortcutKeyCode, shortcutModifiers, listener)
    }

    private fun addAndSelectStyle(style: Style) {
        // Add the style.
        stylingTree.addListElement(style)
        // Rebuild the styling object and subsequently update the UI.
        styling = buildStyling()
        formAdjuster.onLoadStyling()
        // Select the new style in the tree. This will cause it to be opened.
        stylingTree.selected = style
        // Add an undo state.
        ctrl.stylingHistory.editedAndRedraw(styling!!, Pair(null, openCounter))
    }

    private fun duplicateStyle() {
        val style = stylingTree.selected
        if (style !is ListedStyle)
            return
        val newName = "${style.name} (${l10n("ui.styling.copiedStyleNameSuffix")})"
        var copiedStyle = style.copy(ListedStyle::name.st().notarize(newName))
        val updates = ensureConsistency(stylingTree.getList(style.javaClass) + copiedStyle)
        for ((oldStyle, newStyle) in updates)
            if (oldStyle === copiedStyle) copiedStyle = newStyle else stylingTree.updateListElement(oldStyle, newStyle)
        addAndSelectStyle(copiedStyle)
    }

    private fun removeStyle() {
        val style = stylingTree.selected
        if (style !is ListedStyle)
            return
        val selRow = stylingTree.selectedRow
        // Remove the style.
        stylingTree.removeListElement(style)
        // Update other styles to retain consistency.
        val updates = ensureConsistencyAfterRemoval(stylingTree.getList(style.javaClass), style)
        updates.forEach(stylingTree::updateListElement)
        // Rebuild the styling object and subsequently update the UI.
        styling = buildStyling()
        formAdjuster.onLoadStyling()
        // Select the neighboring row in the tree. This will cause another style to be opened.
        stylingTree.selectedRow = selRow.coerceAtMost(stylingTree.rowCount - 1)
        // Add an undo state.
        ctrl.stylingHistory.editedAndRedraw(styling!!, Pair(null, openCounter))
    }

    private fun onChange(widget: Form.Widget<*>? = null, styling: Styling = buildStyling()) {
        // Rebuild the styling object and subsequently update the UI.
        this.styling = styling
        formAdjuster.onChangeInActiveForm()
        // Add an undo state.
        ctrl.stylingHistory.editedAndRedraw(styling, Pair(widget, openCounter))
    }

    private fun openBlank() {
        formAdjuster.activeForm = null
        rightPanelCards.show(rightPanel, "Blank")
    }

    private fun openGlobal(style: Global, form: StyleForm<Global>, cardName: String) {
        // Strictly speaking, we wouldn't need to recreate the change listener each time the form is opened, but we do
        // it here nevertheless for symmetry with the openListedStyle() method.
        form.changeListeners.clear()
        form.changeListeners.add { widget ->
            var newStyle = form.save()
            // If the global runtime setting is 0 and has now been activated, initialize the corresponding timecode
            // spinner with the project's current runtime. This is to provide a good starting value for the user.
            if (newStyle.runtimeFrames.run { isActive && value == 0 }) {
                form.openSingleSetting(Global::runtimeFrames.st(), Opt(true, mostRecentRuntime))
                newStyle = form.save()
            }
            stylingTree.setSingleton(newStyle)
            onChange(widget)
        }
        openStyle(style, form, cardName)
    }

    private fun <S : ListedStyle> openListedStyle(style: S, form: StyleForm<S>, cardName: String) {
        val consistencyRetainer = StylingConsistencyRetainer(styling!!, style)
        form.changeListeners.clear()
        form.changeListeners.add { widget ->
            var newStyle = form.save()
            // If the scroll page runtime setting is 0 and has now been activated, initialize the corresponding timecode
            // spinner with the scroll page's current runtime. This is to provide a good starting value for the user.
            if (newStyle is PageStyle && newStyle.scrollRuntimeFrames.run { isActive && value == 0 }) {
                val newValue = Opt(true, mostRecentScrollRuntimes.getOrDefault(newStyle.name, 1))
                form.castToStyle(PageStyle::class.java).openSingleSetting(PageStyle::scrollRuntimeFrames.st(), newValue)
                newStyle = form.save()
            }
            stylingTree.updateListElement(style.javaClass.cast(stylingTree.selected), newStyle)
            val newStyling = buildStyling()
            val updates = consistencyRetainer.ensureConsistencyAfterEdit(newStyling, newStyle)
            if (updates.isEmpty()) {
                // Take a shortcut to avoid generating the Styling object a second time.
                onChange(widget, newStyling)
                return@add
            }
            updates.forEach(stylingTree::updateListElement)
            updates[newStyle]?.let { updatedNewStyle -> form.open(newStyle.javaClass.cast(updatedNewStyle)) }
            onChange(widget)
        }
        openStyle(style, form, cardName)
    }

    private fun <S : Style> openStyle(style: S, form: StyleForm<S>, cardName: String) {
        form.open(style)
        openCounter++
        formAdjuster.activeForm = form
        formAdjuster.onLoadStyleIntoActiveForm()
        rightPanelCards.show(rightPanel, cardName)
    }

    private fun buildStyling() = Styling(
        stylingTree.getSingleton(Global::class.java),
        stylingTree.getList(PageStyle::class.java).toPersistentList(),
        stylingTree.getList(ContentStyle::class.java).toPersistentList(),
        stylingTree.getList(LetterStyle::class.java).toPersistentList(),
    )

    fun setStyling(styling: Styling) {
        this.styling = styling

        stylingTree.setSingleton(styling.global)
        stylingTree.replaceAllListElements(ListedStyle.CLASSES.flatMap { styling.getListedStyles(it) })
        formAdjuster.onLoadStyling()

        // Simulate the user selecting the node which is already selected currently. This will cause the selected style
        // to be reopened.
        stylingTree.triggerOnSelectOrOnDeselect()
    }

    fun updateProjectFontFamilies(projectFamilies: FontFamilies) {
        formAdjuster.updateProjectFontFamilies(projectFamilies)
    }

    fun updateProject(drawnProject: DrawnProject) {
        // Only update the grayed-out status of styles if the DrawnProject we received stems from the current Styling.
        // If it doesn't, the Styling has changed again in the meantime, and we can't compare with the outdated usage
        // information because the identity of the styles has changed since then. If we didn't have this check, rapidly
        // changing a style would cause it to blink gray and white in the styling tree.
        if (drawnProject.project.styling === styling) {
            val usedStyles = findUsedStyles(drawnProject.project)
            stylingTree.adjustAppearance(isGrayedOut = { it is ListedStyle && it !in usedStyles })
        }
        drawnProject.drawnCredits.firstOrNull()?.let { mostRecentRuntime = it.video.numFrames }
        for (drawnCredits in drawnProject.drawnCredits)
            for (drawnPage in drawnCredits.drawnPages)
                for ((stage, stageInfo) in drawnPage.page.stages.zip(drawnPage.stageInfo))
                    if (stageInfo is DrawnStageInfo.Scroll)
                        mostRecentScrollRuntimes.putIfAbsent(stage.style.name, stageInfo.frames)
    }

    private fun updateConstraintViolations(constraintViolations: List<ConstraintViolation>) {
        val severityPerStyle = IdentityHashMap<Style, Severity>()
        for (violation in constraintViolations)
            severityPerStyle[violation.rootStyle] =
                maxOf(violation.severity, severityPerStyle.getOrDefault(violation.rootStyle, Severity.entries[0]))

        stylingTree.adjustAppearance(getExtraIcons = { style ->
            val severity = severityPerStyle[style]
            if (severity == null || severity == Severity.INFO) emptyList() else listOf(severity.icon)
        })
    }


    /**
     * A wrapping LayoutManager similar to FlowLayout. In contrast to FlowLayout, this implementation increases the
     * minimum and preferred height of the container when wrapping occurs.
     */
    private class WrappingToolbarLayout(private val gap: Int) : LayoutManager {

        override fun addLayoutComponent(name: String, comp: Component) {}
        override fun removeLayoutComponent(comp: Component) {}
        override fun minimumLayoutSize(parent: Container) = preferredLayoutSize(parent)

        override fun preferredLayoutSize(parent: Container): Dimension {
            val rows = flowIntoRows(parent)
            val height = rows.sumOf(Row::height) + gap * (rows.size - 1)
            val insets = parent.insets
            return Dimension(parent.width + insets.left + insets.right, height + insets.top + insets.bottom)
        }

        override fun layoutContainer(parent: Container) {
            val insets = parent.insets
            var y = insets.top
            for (row in flowIntoRows(parent)) {
                var x = insets.left + (parent.width - row.width) / 2
                for (comp in row.comps) {
                    val pref = comp.preferredSize
                    comp.setBounds(x, y, pref.width, pref.height)
                    x += pref.width + gap
                }
                y += row.height + gap
            }
        }

        private fun flowIntoRows(parent: Container): List<Row> {
            val rows = mutableListOf<Row>()

            var rowComps = mutableListOf<Component>()
            var rowWidth = 0
            var rowHeight = 0
            for (idx in 0..<parent.componentCount) {
                val comp = parent.getComponent(idx)
                val pref = comp.preferredSize
                if (rowWidth == 0 || rowWidth + gap + pref.width <= parent.width) {
                    rowComps.add(comp)
                    if (rowWidth != 0) rowWidth += gap
                    rowWidth += pref.width
                    rowHeight = max(rowHeight, pref.height)
                } else {
                    rows.add(Row(rowComps, rowWidth, rowHeight))
                    rowComps = mutableListOf(comp)
                    rowWidth = pref.width
                    rowHeight = pref.height
                }
            }
            if (rowComps.isNotEmpty())
                rows.add(Row(rowComps, rowWidth, rowHeight))

            return rows
        }

        private class Row(val comps: List<Component>, val width: Int, val height: Int)

    }

}
