package com.loadingbyte.cinecred.ui.styling

import com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE
import com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON
import com.loadingbyte.cinecred.common.Severity
import com.loadingbyte.cinecred.common.l10n
import com.loadingbyte.cinecred.project.*
import com.loadingbyte.cinecred.ui.ProjectController
import com.loadingbyte.cinecred.ui.helper.*
import kotlinx.collections.immutable.toImmutableList
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.*
import java.util.*
import javax.swing.*


class EditStylingPanel(private val ctrl: ProjectController) : JPanel() {

    // ========== HINT OWNERS ==========
    val stylingTreeHintOwner: Component
    // =================================

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

    // Cache the styling which is currently stored in the styling tree as well as its constraint violations and unused
    // styles, so that we don't have to repeatedly regenerate these three things.
    private var styling: Styling? = null
    private var constraintViolations: List<ConstraintViolation> = emptyList()
    private var unusedStyles: Set<Style> = emptySet()

    // Keep track of the form which is currently open.
    private var openedForm: StyleForm<*>? = null

    // We increase this counter each time a new form is opened. It is used to tell apart multiple edits
    // of the same widget but in different styles.
    private var openCounter = 0

    init {
        stylingTreeHintOwner = stylingTree
        stylingTree.onDeselect = ::openBlank
        stylingTree.addSingletonType(
            PRESET_GLOBAL, l10n("ui.styling.globalStyling"), GLOBE_ICON,
            onSelect = ::openGlobal
        )
        stylingTree.addListType(
            PageStyle::class.java, l10n("ui.styling.pageStyles"), FILMSTRIP_ICON,
            onSelect = ::openPageStyle,
            objToString = PageStyle::name
        )
        stylingTree.addListType(
            ContentStyle::class.java, l10n("ui.styling.contentStyles"), LAYOUT_ICON,
            onSelect = ::openContentStyle,
            objToString = ContentStyle::name
        )
        stylingTree.addListType(
            LetterStyle::class.java, l10n("ui.styling.letterStyles"), LETTERS_ICON,
            onSelect = ::openLetterStyle,
            objToString = LetterStyle::name
        )

        fun makeToolbarBtn(
            name: String, icon: Icon, shortcutKeyCode: Int = -1, shortcutModifiers: Int = 0, listener: () -> Unit
        ): JButton {
            var ttip = l10n("ui.styling.$name")
            if (shortcutKeyCode != -1) {
                ttip += " (${getModifiersExText(shortcutModifiers)}+${getKeyText(shortcutKeyCode)})"
                keyListeners.add(KeyListener(shortcutKeyCode, shortcutModifiers, listener))
            }
            return JButton(icon).apply {
                putClientProperty(BUTTON_TYPE, BUTTON_TYPE_TOOLBAR_BUTTON)
                isFocusable = false
                toolTipText = ttip
                addActionListener { listener() }
            }
        }

        // Add buttons for adding and removing style nodes.
        val addPageStyleButton = makeToolbarBtn(
            "addPageStyleTooltip", SVGIcon.Dual(ADD_ICON, FILMSTRIP_ICON), VK_P, CTRL_DOWN_MASK or SHIFT_DOWN_MASK
        ) {
            stylingTree.addListElement(PRESET_PAGE_STYLE.copy(name = l10n("ui.styling.newPageStyleName")), true)
            onChange()
        }
        val addContentStyleButton = makeToolbarBtn(
            "addContentStyleTooltip", SVGIcon.Dual(ADD_ICON, LAYOUT_ICON), VK_C, CTRL_DOWN_MASK or SHIFT_DOWN_MASK
        ) {
            stylingTree.addListElement(PRESET_CONTENT_STYLE.copy(name = l10n("ui.styling.newContentStyleName")), true)
            onChange()
        }
        val addLetterStyleButton = makeToolbarBtn(
            "addLetterStyleTooltip", SVGIcon.Dual(ADD_ICON, LETTERS_ICON), VK_L, CTRL_DOWN_MASK or SHIFT_DOWN_MASK
        ) {
            stylingTree.addListElement(PRESET_LETTER_STYLE.copy(name = l10n("ui.styling.newLetterStyleName")), true)
            onChange()
        }
        val duplicateStyleButton = makeToolbarBtn("duplicateStyleTooltip", DUPLICATE_ICON, listener = ::duplicateStyle)
        val removeStyleButton = makeToolbarBtn("removeStyleTooltip", TRASH_ICON, listener = ::removeStyle)

        // Add contextual keyboard shortcuts for the style duplication and removal buttons.
        duplicateStyleButton.toolTipText += " (${getModifiersExText(CTRL_DOWN_MASK)}+${getKeyText(VK_D)})"
        removeStyleButton.toolTipText += " (${getKeyText(VK_DELETE)})"
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

        // Layout the tree and the buttons.
        val leftPanel = JPanel(MigLayout()).apply {
            add(addPageStyleButton, "split, grow")
            add(addContentStyleButton, "grow")
            add(addLetterStyleButton, "grow")
            add(duplicateStyleButton, "grow")
            add(removeStyleButton, "grow")
            add(JScrollPane(stylingTree), "newline, grow, push")
        }

        // Put everything together in a split pane.
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, leftPanel, rightPanel)
        // Slightly postpone moving the dividers so that the panes know their height when the dividers are moved.
        SwingUtilities.invokeLater { splitPane.setDividerLocation(0.25) }

        // Use BorderLayout to maximize the size of the split pane.
        layout = BorderLayout()
        add(splitPane, BorderLayout.CENTER)

        globalForm.changeListeners += { widget ->
            stylingTree.setSingleton(globalForm.save())
            onChange(widget)
        }
        pageStyleForm.changeListeners += { widget ->
            stylingTree.updateSelectedListElement(pageStyleForm.save())
            onChange(widget)
        }
        contentStyleForm.changeListeners += { widget ->
            stylingTree.updateSelectedListElement(contentStyleForm.save())
            onChange(widget)
        }
        // Note: The change listener for the letterStyleForm is managed by openLetterStyle().
    }

    private fun duplicateStyle() {
        val copiedStyle = when (val style = stylingTree.getSelected() as Style?) {
            is PageStyle -> style.copy(name = l10n("ui.styling.copiedStyleName", style.name))
            is ContentStyle -> style.copy(name = l10n("ui.styling.copiedStyleName", style.name))
            is LetterStyle -> style.copy(name = l10n("ui.styling.copiedStyleName", style.name))
            null, is Global -> return
            is TextDecoration -> throw IllegalStateException()  // can never happen
        }
        stylingTree.addListElement(copiedStyle, select = true)
        onChange()
    }

    private fun removeStyle() {
        if (stylingTree.removeSelectedListElement(selectNext = true))
            onChange()
    }

    private fun openBlank() {
        openedForm = null
        rightPanelCards.show(rightPanel, "Blank")
    }

    private fun openGlobal(global: Global) {
        globalForm.open(global)
        postOpenForm("Global", globalForm)
    }

    private fun openPageStyle(style: PageStyle) {
        pageStyleForm.open(style)
        postOpenForm("PageStyle", pageStyleForm)
    }

    private fun openContentStyle(style: ContentStyle) {
        contentStyleForm.open(style)
        postOpenForm("ContentStyle", contentStyleForm)
    }

    private fun openLetterStyle(style: LetterStyle) {
        class TrackedRef(var contentStyle: ContentStyle, val body: Boolean, val head: Boolean, val tail: Boolean)

        // If the letter style has a unique name, we want to keep all content styles which reference that name in sync
        // with changes to the name. First, record all these referencing content styles with information on which of
        // their settings reference the name.
        val trackedRefs = mutableListOf<TrackedRef>()
        if (style !in unusedStyles)
            for (contentStyle in stylingTree.getList(ContentStyle::class.java)) {
                val body = contentStyle.bodyLetterStyleName == style.name
                val head = contentStyle.headLetterStyleName == style.name
                val tail = contentStyle.tailLetterStyleName == style.name
                if (body || head || tail)
                    trackedRefs.add(TrackedRef(contentStyle, body, head, tail))
            }

        var oldName = style.name
        letterStyleForm.open(style)
        letterStyleForm.changeListeners.clear()
        letterStyleForm.changeListeners += { widget ->
            val newStyle = letterStyleForm.save()
            stylingTree.updateSelectedListElement(newStyle)

            // If the letter style changed its name, update all previously recorded references to that name.
            val newName = newStyle.name
            if (oldName != newName)
                for (trackedRef in trackedRefs) {
                    var newContentStyle = trackedRef.contentStyle
                    if (trackedRef.body)
                        newContentStyle = newContentStyle.copy(bodyLetterStyleName = newName)
                    if (trackedRef.head)
                        newContentStyle = newContentStyle.copy(headLetterStyleName = newName)
                    if (trackedRef.tail)
                        newContentStyle = newContentStyle.copy(tailLetterStyleName = newName)
                    stylingTree.updateListElement(trackedRef.contentStyle, newContentStyle)
                    trackedRef.contentStyle = newContentStyle
                }
            oldName = newName

            onChange(widget)
        }
        postOpenForm("LetterStyle", letterStyleForm)
    }

    private fun postOpenForm(cardName: String, form: StyleForm<*>) {
        openedForm = form
        openCounter++
        adjustOpenedForm()
        rightPanelCards.show(rightPanel, cardName)
        // When the user selected a non-blank card, reset the vertical scrollbar position to the top.
        (form.parent.parent as JScrollPane).verticalScrollBar.value = 0
    }

    fun setStyling(styling: Styling) {
        this.styling = styling

        stylingTree.setSingleton(styling.global)
        stylingTree.replaceAllListElements(styling.pageStyles + styling.contentStyles + styling.letterStyles)
        refreshConstraintViolations()

        // Simulate the user selecting the node which is already selected currently. This triggers a callback
        // which then updates the right panel. If the node is a style node, that callback will also in turn call
        // postOpenForm(), which will in turn call adjustOpenedForm().
        stylingTree.triggerOnSelectOrOnDeselect()
    }

    fun updateProjectFontFamilies(projectFamilies: FontFamilies) {
        letterStyleForm.setProjectFontFamilies(projectFamilies)
        if (openedForm === letterStyleForm) {
            refreshConstraintViolations()
            adjustOpenedForm()
        }
    }

    private fun onChange(widget: Form.Widget<*>? = null) {
        val styling = Styling(
            stylingTree.getSingleton(Global::class.java),
            stylingTree.getList(PageStyle::class.java).toImmutableList(),
            stylingTree.getList(ContentStyle::class.java).toImmutableList(),
            stylingTree.getList(LetterStyle::class.java).toImmutableList(),
        )
        this.styling = styling

        refreshConstraintViolations()
        adjustOpenedForm()
        ctrl.stylingHistory.editedAndRedraw(styling, Pair(widget, openCounter))
    }

    fun updateProject(project: Project?) {
        updateUnusedStyles(project)
    }

    private fun refreshConstraintViolations() {
        constraintViolations = verifyConstraints(ctrl.stylingCtx, styling ?: return)

        val severityPerStyle = IdentityHashMap<Style, Severity>()
        for (violation in constraintViolations)
            severityPerStyle[violation.rootStyle] =
                maxOf(violation.severity, severityPerStyle.getOrDefault(violation.rootStyle, Severity.values()[0]))

        stylingTree.adjustAppearance(getExtraIcons = { style ->
            val severity = severityPerStyle[style]
            if (severity == null || severity == Severity.INFO) emptyList() else listOf(severity.icon)
        })
    }

    private fun adjustOpenedForm() {
        val curStyle = (stylingTree.getSelected() ?: return) as Style
        adjustForm((openedForm ?: return).castToStyle(curStyle.javaClass), curStyle)
    }

    private fun <S : Style> adjustForm(curForm: StyleForm<S>, curStyle: S) {
        val styling = this.styling ?: return

        curForm.setIneffectiveSettings(findIneffectiveSettings(ctrl.stylingCtx, curStyle))

        curForm.clearIssues()
        for (violation in constraintViolations)
            if (violation.leafStyle == curStyle) {
                val issue = Form.Notice(violation.severity, violation.msg)
                curForm.showIssueIfMoreSevere(violation.leafSetting, violation.leafIndex, issue)
            }

        for (constr in getStyleConstraints(curStyle.javaClass))
            if (constr is DynChoiceConstr<S, *>) {
                val choices = constr.choices(ctrl.stylingCtx, styling, curStyle)
                for (setting in constr.settings)
                    curForm.setChoices(setting, choices)
            } else if (constr is FontFeatureConstr) {
                val availableTags = constr.getAvailableTags(ctrl.stylingCtx, styling, curStyle)
                for (setting in constr.settings)
                    curForm.setChoices(setting, availableTags.toList(), unique = true)
            }

        for (spec in getStyleWidgetSpecs(curStyle.javaClass))
            if (spec is ToggleButtonGroupWidgetSpec<S, *>) {
                fun <V : Enum<*>> makeToIcon(spec: ToggleButtonGroupWidgetSpec<S, V>): ((V) -> Icon)? =
                    spec.getIcon?.let { return fun(item: V) = it(ctrl.stylingCtx, styling, curStyle, item) }

                val toIcon = makeToIcon(spec)
                if (toIcon != null)
                    for (setting in spec.settings)
                        curForm.setToIconFun(setting, toIcon)
            } else if (spec is TimecodeWidgetSpec) {
                val fps = spec.getFPS(ctrl.stylingCtx, styling, curStyle)
                val timecodeFormat = spec.getTimecodeFormat(ctrl.stylingCtx, styling, curStyle)
                for (setting in spec.settings)
                    curForm.setTimecodeFPSAndFormat(setting, fps, timecodeFormat)
            }

        for ((nestedForm, nestedStyle) in curForm.getNestedFormsAndStyles(curStyle))
            adjustForm(nestedForm.castToStyle(nestedStyle.javaClass), nestedStyle)
    }

    private fun updateUnusedStyles(project: Project?) {
        val unusedStyles = Collections.newSetFromMap(IdentityHashMap<Style, Boolean>())

        if (project != null) {
            val styling = project.styling

            // Mark all styles as unused. Next, we will gradually remove all styles which are actually used.
            unusedStyles.addAll(styling.pageStyles)
            unusedStyles.addAll(styling.contentStyles)
            unusedStyles.addAll(styling.letterStyles)

            for (contentStyle in styling.contentStyles) {
                // Remove the content style's body letter style.
                styling.letterStyles.find { it.name == contentStyle.bodyLetterStyleName }?.let(unusedStyles::remove)
                // If the content style supports heads, remove its head letter style.
                if (contentStyle.hasHead)
                    styling.letterStyles.find { it.name == contentStyle.headLetterStyleName }?.let(unusedStyles::remove)
                // If the content style supports heads, remove its tail letter style.
                if (contentStyle.hasTail)
                    styling.letterStyles.find { it.name == contentStyle.tailLetterStyleName }?.let(unusedStyles::remove)
            }

            for (page in project.pages)
                for (stage in page.stages) {
                    // Remove the stage's page style.
                    unusedStyles -= stage.style
                    for (segment in stage.segments)
                        for (spine in segment.spines)
                            for (block in spine.blocks) {
                                // Remove the block's content style.
                                unusedStyles -= block.style
                                // Remove the head's letter styles.
                                for ((_, letterStyle) in block.head.orEmpty())
                                    unusedStyles -= letterStyle
                                // Remove the tail's letter styles.
                                for ((_, letterStyle) in block.tail.orEmpty())
                                    unusedStyles -= letterStyle
                                // Remove the body's letter styles.
                                for (bodyElem in block.body)
                                    if (bodyElem is BodyElement.Str)
                                        for ((_, letterStyle) in bodyElem.str)
                                            unusedStyles -= letterStyle
                            }
                }
        }

        stylingTree.adjustAppearance(isGrayedOut = unusedStyles::contains)
        this.unusedStyles = unusedStyles
    }

}
