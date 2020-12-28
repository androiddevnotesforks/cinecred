package com.loadingbyte.cinecred.ui

import com.loadingbyte.cinecred.Severity
import com.loadingbyte.cinecred.project.*
import com.loadingbyte.cinecred.projectio.toFPS
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import javax.swing.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.*


object EditStylingPanel : JPanel() {

    private var global: Global? = null

    private val globalNode = DefaultMutableTreeNode("Global Styling", false)
    private val pageStylesNode = DefaultMutableTreeNode("Page Styles", true)
    private val contentStylesNode = DefaultMutableTreeNode("Content Styles", true)
    private val treeModel: DefaultTreeModel
    private val tree: JTree
    private var disableTreeSelectionListener = false

    init {
        // Create a panel with the three style editing forms.
        val rightPanelCards = CardLayout()
        val rightPanel = JPanel(rightPanelCards).apply {
            add(JPanel(), "Blank")
            add(JScrollPane(GlobalForm), "Global")
            add(JScrollPane(PageStyleForm), "PageStyle")
            add(JScrollPane(ContentStyleForm), "ContentStyle")
        }

        // Create the tree that contains a node for the global settings, as well as nodes for the page & content styles.
        val rootNode = DefaultMutableTreeNode().apply {
            add(globalNode)
            add(pageStylesNode)
            add(contentStylesNode)
        }
        treeModel = DefaultTreeModel(rootNode, true)
        tree = object : JTree(treeModel) {
            override fun convertValueToText(
                value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ) = when (val userObject = (value as DefaultMutableTreeNode?)?.userObject) {
                is PageStyle -> userObject.name
                is ContentStyle -> userObject.name
                else -> userObject?.toString() ?: ""
            }
        }.apply {
            isRootVisible = false
            showsRootHandles = false
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            putClientProperty("JTree.lineStyle", "Horizontal")
        }
        // The page and content style "folders" must not be collapsed by the user.
        tree.addTreeWillExpandListener(object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {}
            override fun treeWillCollapse(event: TreeExpansionEvent) {
                throw ExpandVetoException(event)
            }
        })
        // Each node gets an icon depending on whether it stores the global settings, a page style, or a content style.
        tree.cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree, value: Any, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ): Component {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
                val node = value as DefaultMutableTreeNode
                when {
                    node == globalNode -> icon = GLOBAL_ICON
                    node.userObject is PageStyle -> icon = PAGE_ICON
                    node.userObject is ContentStyle -> icon = CONTENT_ICON
                }
                return this
            }
        }
        // When the user selects the global settings node or a page or content style node, open the corresponding form.
        tree.addTreeSelectionListener {
            if (disableTreeSelectionListener)
                return@addTreeSelectionListener
            val selectedNode = tree.lastSelectedPathComponent as DefaultMutableTreeNode?
            val selectedNodeUserObject = selectedNode?.userObject
            // Otherwise, open form corresponding to the selected node.
            var scrollPane: JScrollPane? = null
            when {
                selectedNode == globalNode -> {
                    scrollPane = GlobalForm.parent.parent as JScrollPane
                    GlobalForm.openGlobal(global!!, changeCallback = { global = it; onChange() })
                    rightPanelCards.show(rightPanel, "Global")
                }
                selectedNodeUserObject is PageStyle -> {
                    scrollPane = PageStyleForm.parent.parent as JScrollPane
                    PageStyleForm.openPageStyle(
                        selectedNodeUserObject,
                        changeCallback = { selectedNode.userObject = it; onChange(); sortNode(selectedNode) }
                    )
                    rightPanelCards.show(rightPanel, "PageStyle")
                }
                selectedNodeUserObject is ContentStyle -> {
                    scrollPane = ContentStyleForm.parent.parent as JScrollPane
                    ContentStyleForm.openContentStyle(
                        selectedNodeUserObject,
                        changeCallback = { selectedNode.userObject = it; onChange(); sortNode(selectedNode) }
                    )
                    rightPanelCards.show(rightPanel, "ContentStyle")
                }
                else ->
                    rightPanelCards.show(rightPanel, "Blank")
            }
            // When the user selected a non-blank card, reset the vertical scrollbar position to the top.
            // Note that we have to delay this change because for some reason, if we don't, the change has no effect.
            SwingUtilities.invokeLater { scrollPane?.verticalScrollBar?.value = 0 }
        }

        // Add buttons for adding and removing page and content style nodes.
        val addPageStyleButton = JButton("+", PAGE_ICON).apply { toolTipText = "Add new page style" }
        val addContentStyleButton = JButton("+", CONTENT_ICON).apply { toolTipText = "Add new content style" }
        val removeButton = JButton("\u2212").apply { toolTipText = "Remove selected style" }
        addPageStyleButton.addActionListener {
            insertAndSelectSortedLeaf(pageStylesNode, STANDARD_PAGE_STYLE.copy(name = "New Page Style"))
            onChange()
        }
        addContentStyleButton.addActionListener {
            insertAndSelectSortedLeaf(contentStylesNode, STANDARD_CONTENT_STYLE.copy(name = "New Content Style"))
            onChange()
        }
        removeButton.addActionListener {
            val selectedNode = (tree.lastSelectedPathComponent ?: return@addActionListener) as DefaultMutableTreeNode
            if (selectedNode.userObject is PageStyle || selectedNode.userObject is ContentStyle) {
                treeModel.removeNodeFromParent(selectedNode)
                onChange()
            }
        }

        // Layout the tree and the buttons.
        val leftPanel = JPanel(MigLayout()).apply {
            add(JScrollPane(tree), "grow, push")
            add(addPageStyleButton, "newline, split, grow")
            add(addContentStyleButton, "grow")
            add(removeButton, "grow")
        }

        // Put everything together in a split pane.
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, leftPanel, rightPanel)
        // Slightly postpone moving the dividers so that the panes know their height when the dividers are moved.
        SwingUtilities.invokeLater { splitPane.setDividerLocation(0.25) }

        // Use BorderLayout to maximize the size of the split pane.
        layout = BorderLayout()
        add(splitPane, BorderLayout.CENTER)
    }

    private fun sortNode(node: DefaultMutableTreeNode) {
        // Note: We temporarily disable the tree selection listener because the node removal and subsequent insertion
        // at another place should not close and re-open (and thus reset) the right-hand editing panel.
        disableTreeSelectionListener = true
        // We also remember the current selection path so that we can restore it later.
        val selectionPath = tree.selectionPath

        val parent = node.parent as DefaultMutableTreeNode
        treeModel.removeNodeFromParent(node)
        var idx = parent.children().asSequence().indexOfFirst {
            (it as DefaultMutableTreeNode).userObject.toString() > node.userObject.toString()
        }
        if (idx == -1)
            idx = parent.childCount
        treeModel.insertNodeInto(node, parent, idx)

        tree.selectionPath = selectionPath
        disableTreeSelectionListener = false
    }

    private fun insertSortedLeaf(parent: MutableTreeNode, userObject: Any): DefaultMutableTreeNode {
        val node = DefaultMutableTreeNode(userObject, false)
        treeModel.insertNodeInto(node, parent, 0)
        sortNode(node)
        return node
    }

    private fun insertAndSelectSortedLeaf(parent: MutableTreeNode, userObject: Any) {
        val leaf = insertSortedLeaf(parent, userObject)
        val leafPath = TreePath(leaf.path)
        tree.scrollPathToVisible(TreePath(leaf.path))
        tree.selectionPath = leafPath
    }

    fun onOpenProjectDir(initialStyling: Styling) {
        global = initialStyling.global

        for (node in pageStylesNode.children().toList() + contentStylesNode.children().toList())
            treeModel.removeNodeFromParent(node as MutableTreeNode)

        for (pageStyle in initialStyling.pageStyles)
            insertSortedLeaf(pageStylesNode, pageStyle)
        for (contentStyle in initialStyling.contentStyles)
            insertSortedLeaf(contentStylesNode, contentStyle)

        tree.expandPath(TreePath(pageStylesNode.path))
        tree.expandPath(TreePath(contentStylesNode.path))

        // Initially select the global settings.
        tree.setSelectionRow(0)
    }

    private fun onChange() {
        Controller.editStyling(Styling(
            global!!,
            pageStylesNode.children().toList().map { (it as DefaultMutableTreeNode).userObject as PageStyle },
            contentStylesNode.children().toList().map { (it as DefaultMutableTreeNode).userObject as ContentStyle }
        ))
    }

    private fun toDisplayString(enum: Enum<*>) =
        enum.name.replace('_', ' ').split(' ').joinToString(" ") { it.toLowerCase().capitalize() }


    private object GlobalForm : Form() {

        private val fpsComboBox = addComboBox(
            "Frames Per Second", arrayOf("23.97", "24", "25", "29.97", "30", "59.94", "60"),
            verify = {
                try {
                    (it ?: throw VerifyResult(Severity.ERROR, "Empty.")).toFPS()
                } catch (_: IllegalArgumentException) {
                    throw VerifyResult(Severity.ERROR, "Not an admissible frame rate.")
                }
            }
        ).apply { isEditable = true }

        private val widthPxSpinner = addSpinner("Picture Width (Pixels)", SpinnerNumberModel(1, 1, null, 10))
        private val heightPxSpinner = addSpinner("Picture Height (Pixels)", SpinnerNumberModel(1, 1, null, 10))
        private val backgroundColorChooserButton = addColorChooserButton("Background Color")
        private val unitHGapPxSpinner = addSpinner("Unit \"hgap\" (Pixels)", SpinnerNumberModel(1f, 0.01f, null, 1f))
        private val unitVGapPxSpinner = addSpinner("Unit \"vgap\" (Pixels)", SpinnerNumberModel(1f, 0.01f, null, 1f))

        fun openGlobal(global: Global, changeCallback: (Global) -> Unit) {
            clearChangeListeners()

            fpsComboBox.selectedItem =
                if (global.fps.denominator == 1) global.fps.numerator.toString()
                else "%.2f".format(global.fps.frac)
            widthPxSpinner.value = global.widthPx
            heightPxSpinner.value = global.heightPx
            backgroundColorChooserButton.selectedColor = global.background
            unitHGapPxSpinner.value = global.unitHGapPx
            unitVGapPxSpinner.value = global.unitVGapPx

            addChangeListener {
                if (isErrorFree) {
                    val newGlobal = Global(
                        (fpsComboBox.selectedItem as String).toFPS(),
                        widthPxSpinner.value as Int,
                        heightPxSpinner.value as Int,
                        backgroundColorChooserButton.selectedColor,
                        unitHGapPxSpinner.value as Float,
                        unitVGapPxSpinner.value as Float
                    )
                    changeCallback(newGlobal)
                }
            }
        }

    }


    private object PageStyleForm : Form() {

        private val nameField = addTextField("Page Style Name",
            verify = { if (it.trim().isEmpty()) throw VerifyResult(Severity.ERROR, "Name is blank.") }
        )
        private val behaviorComboBox = addComboBox("Page Behavior", PageBehavior.values(), toString = ::toDisplayString)
        private val afterwardSlugFramesSpinner =
            addSpinner("Afterward Slug (Frames)", SpinnerNumberModel(0, 0, null, 1))
        private val cardDurationFramesSpinner = addSpinner(
            "Card Duration (Frames)", SpinnerNumberModel(0, 0, null, 1),
            isVisible = { behaviorComboBox.selectedItem == PageBehavior.CARD }
        )
        private val cardFadeInFramesSpinner = addSpinner(
            "Fade In (Frames)", SpinnerNumberModel(0, 0, null, 1),
            isVisible = { behaviorComboBox.selectedItem == PageBehavior.CARD }
        )
        private val cardFadeOutFramesSpinner = addSpinner(
            "Fade Out (Frames)", SpinnerNumberModel(0, 0, null, 1),
            isVisible = { behaviorComboBox.selectedItem == PageBehavior.CARD }
        )
        private val scrollPxPerFrame = addSpinner(
            "Scroll Pixels per Frame", SpinnerNumberModel(1f, 0.01f, null, 1f),
            isVisible = { behaviorComboBox.selectedItem == PageBehavior.SCROLL }
        )

        fun openPageStyle(pageStyle: PageStyle, changeCallback: (PageStyle) -> Unit) {
            clearChangeListeners()

            nameField.text = pageStyle.name
            afterwardSlugFramesSpinner.value = pageStyle.afterwardSlugFrames
            behaviorComboBox.selectedItem = pageStyle.behavior
            cardDurationFramesSpinner.value = pageStyle.cardDurationFrames
            cardFadeInFramesSpinner.value = pageStyle.cardFadeInFrames
            cardFadeOutFramesSpinner.value = pageStyle.cardFadeOutFrames
            scrollPxPerFrame.value = pageStyle.scrollPxPerFrame

            addChangeListener {
                if (isErrorFree) {
                    val newPageStyle = PageStyle(
                        nameField.text.trim(),
                        behaviorComboBox.selectedItem as PageBehavior,
                        afterwardSlugFramesSpinner.value as Int,
                        cardDurationFramesSpinner.value as Int,
                        cardFadeInFramesSpinner.value as Int,
                        cardFadeOutFramesSpinner.value as Int,
                        scrollPxPerFrame.value as Float
                    )
                    changeCallback(newPageStyle)
                }
            }
        }

    }


    private object ContentStyleForm : Form() {

        private val nameField = addTextField("Content Style Name",
            verify = { if (it.trim().isEmpty()) throw VerifyResult(Severity.ERROR, "Name is blank.") }
        )
        private val spineDirComboBox = addComboBox("Spine Direction", SpineDir.values(), toString = ::toDisplayString)
        private val centerOnComboBox = addComboBox(
            "Center On", CenterOn.values(), toString = ::toDisplayString,
            isVisible = { spineDirComboBox.selectedItem == SpineDir.HORIZONTAL }
        )

        private val bodyLayoutComboBox = addComboBox(
            "Body Layout", BodyLayout.values(), toString = ::toDisplayString
        )
        private val colsLayoutColTypes = addComboBoxList(
            "Body Columns", ColType.values(), toString = ::toDisplayString,
            isVisible = { bodyLayoutComboBox.selectedItem == BodyLayout.COLUMNS },
            verify = {
                if (it.all { colType -> colType == null || colType == ColType.VACANT })
                    throw VerifyResult(Severity.ERROR, "There must be at least one non-vacant body column.")
            }
        )
        private val colsLayoutColGapPxSpinner = addSpinner(
            "Body Col. Gap (Px)", SpinnerNumberModel(0f, 0f, null, 1f),
            isVisible = {
                bodyLayoutComboBox.selectedItem == BodyLayout.COLUMNS &&
                        colsLayoutColTypes.selectedItems.size >= 2
            }
        )
        private val flowLayoutBodyWidthPxSpinner = addSpinner(
            "Body With (Pixels)", SpinnerNumberModel(1f, 0.01f, null, 10f),
            isVisible = { bodyLayoutComboBox.selectedItem == BodyLayout.FLOW }
        )
        private val flowLayoutJustifyComboBox = addComboBox(
            "Justify Body", FlowJustify.values(), toString = ::toDisplayString,
            isVisible = { bodyLayoutComboBox.selectedItem == BodyLayout.FLOW }
        )
        private val flowLayoutSeparatorField = addTextField(
            "Body Elem. Separator", grow = false,
            isVisible = { bodyLayoutComboBox.selectedItem == BodyLayout.FLOW }
        )
        private val flowLayoutSeparatorSpacingPxSpinner = addSpinner(
            "Sep. Spacing (Pixels)", SpinnerNumberModel(0f, 0f, null, 1f),
            isVisible = { bodyLayoutComboBox.selectedItem == BodyLayout.FLOW }
        )

        private val bodyFontSpecChooser = addFontSpecChooser("Body Font")

        private val hasHeadCheckBox = addCheckBox("Enable Head")
        private val headHJustifyComboBox = addComboBox(
            "Justify Head", HJustify.values(), toString = ::toDisplayString,
            isVisible = { hasHeadCheckBox.isSelected }
        )
        private val headVJustifyComboBox = addComboBox(
            "Align Head", VJustify.values(), toString = ::toDisplayString,
            isVisible = { hasHeadCheckBox.isSelected && spineDirComboBox.selectedItem == SpineDir.HORIZONTAL }
        )
        private val headGapPxSpinner = addSpinner(
            "Head-Body Gap", SpinnerNumberModel(0f, 0f, null, 1f),
            isVisible = { hasHeadCheckBox.isSelected }
        )
        private val headFontSpecChooser = addFontSpecChooser("Head Font",
            isVisible = { hasHeadCheckBox.isSelected }
        )

        private val hasTailCheckBox = addCheckBox("Enable Tail")
        private val tailHJustifyComboBox = addComboBox(
            "Justify Tail", HJustify.values(), toString = ::toDisplayString,
            isVisible = { hasTailCheckBox.isSelected }
        )
        private val tailVJustifyComboBox = addComboBox(
            "Align Tail", VJustify.values(), toString = ::toDisplayString,
            isVisible = { hasTailCheckBox.isSelected && spineDirComboBox.selectedItem == SpineDir.HORIZONTAL }
        )
        private val tailGapPxSpinner = addSpinner(
            "Body-Tail Gap", SpinnerNumberModel(0f, 0f, null, 1f),
            isVisible = { hasTailCheckBox.isSelected }
        )
        private val tailFontSpecChooser = addFontSpecChooser("Tail Font",
            isVisible = { hasTailCheckBox.isSelected }
        )

        fun openContentStyle(contentStyle: ContentStyle, changeCallback: (ContentStyle) -> Unit) {
            clearChangeListeners()

            nameField.text = contentStyle.name
            spineDirComboBox.selectedItem = contentStyle.spineDir
            centerOnComboBox.selectedItem = contentStyle.centerOn
            bodyLayoutComboBox.selectedItem = contentStyle.bodyLayout
            colsLayoutColTypes.selectedItems = contentStyle.colsBodyLayoutColTypes
            colsLayoutColGapPxSpinner.value = contentStyle.colsBodyLayoutColGapPx
            flowLayoutBodyWidthPxSpinner.value = contentStyle.flowBodyLayoutBodyWidthPx
            flowLayoutJustifyComboBox.selectedItem = contentStyle.flowBodyLayoutJustify
            flowLayoutSeparatorField.text = contentStyle.flowBodyLayoutSeparator
            flowLayoutSeparatorSpacingPxSpinner.value = contentStyle.flowBodyLayoutSeparatorSpacingPx
            bodyFontSpecChooser.selectedFontSpec = contentStyle.bodyFontSpec
            hasHeadCheckBox.isSelected = contentStyle.hasHead
            headHJustifyComboBox.selectedItem = contentStyle.headHJustify
            headVJustifyComboBox.selectedItem = contentStyle.headVJustify
            headGapPxSpinner.value = contentStyle.headGapPx
            headFontSpecChooser.selectedFontSpec = contentStyle.headFontSpec
            hasTailCheckBox.isSelected = contentStyle.hasTail
            tailHJustifyComboBox.selectedItem = contentStyle.tailHJustify
            tailVJustifyComboBox.selectedItem = contentStyle.tailVJustify
            tailGapPxSpinner.value = contentStyle.tailGapPx
            tailFontSpecChooser.selectedFontSpec = contentStyle.tailFontSpec

            addChangeListener {
                if (isErrorFree) {
                    val newContentStyle = ContentStyle(
                        nameField.text.trim(),
                        spineDirComboBox.selectedItem as SpineDir,
                        centerOnComboBox.selectedItem as CenterOn,
                        bodyLayoutComboBox.selectedItem as BodyLayout,
                        colsLayoutColTypes.selectedItems.filterNotNull(),
                        colsLayoutColGapPxSpinner.value as Float,
                        flowLayoutBodyWidthPxSpinner.value as Float,
                        flowLayoutJustifyComboBox.selectedItem as FlowJustify,
                        flowLayoutSeparatorField.text,
                        flowLayoutSeparatorSpacingPxSpinner.value as Float,
                        bodyFontSpecChooser.selectedFontSpec,
                        hasHeadCheckBox.isSelected,
                        headHJustifyComboBox.selectedItem as HJustify,
                        headVJustifyComboBox.selectedItem as VJustify,
                        headGapPxSpinner.value as Float,
                        headFontSpecChooser.selectedFontSpec,
                        hasTailCheckBox.isSelected,
                        tailHJustifyComboBox.selectedItem as HJustify,
                        tailVJustifyComboBox.selectedItem as VJustify,
                        tailGapPxSpinner.value as Float,
                        tailFontSpecChooser.selectedFontSpec
                    )
                    changeCallback(newContentStyle)
                }
            }
        }

    }

}
