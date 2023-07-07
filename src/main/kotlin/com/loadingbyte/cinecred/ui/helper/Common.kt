package com.loadingbyte.cinecred.ui.helper

import com.formdev.flatlaf.FlatClientProperties.*
import com.formdev.flatlaf.icons.FlatCheckBoxIcon
import com.formdev.flatlaf.ui.FlatUIUtils
import com.formdev.flatlaf.util.SystemInfo
import com.formdev.flatlaf.util.UIScale
import com.loadingbyte.cinecred.common.colorFromHex
import com.loadingbyte.cinecred.common.preserveTransform
import java.awt.*
import java.awt.event.*
import java.awt.event.KeyEvent.*
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.basic.BasicFileChooserUI
import javax.swing.table.TableCellRenderer
import javax.swing.text.Document
import kotlin.math.roundToInt


const val PALETTE_RED: String = "#C75450"
const val PALETTE_GREEN: String = "#499C54"
const val PALETTE_BLUE: String = "#3592C4"
const val PALETTE_GRAY: String = "#AFB1B3"
val PALETTE_GRAY_COLOR: Color = colorFromHex(PALETTE_GRAY)
val PALETTE_BLUE_COLOR: Color = colorFromHex(PALETTE_BLUE)

val OVERLAY_COLOR: Color = Color.GRAY


/**
 * By enabling HTML in the JLabel, a branch that doesn't add ellipsis but instead clips the string is taken in
 * SwingUtilities.layoutCompoundLabelImpl().
 */
fun noEllipsisLabel(text: String) = "<html>$text</html>"


fun newLabelTextArea(text: String? = null, insets: Boolean = false) = object : JTextArea(text) {
    init {
        background = null
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        // If requested, set insets to 0, as JLabels also have insets of 0 and the text area should behave like a label.
        if (!insets)
            border = null
        // Without setting an explicit minimum width, the component would never ever again shrink once it has grown.
        // This would of course lead to trouble when first enlarging and then shrinking a container which contains
        // a label text area. By setting an explicit minimum width, we turn off this undesired behavior.
        minimumSize = Dimension(0, 0)
    }

    // Disable the capability of the text area to scroll any ancestor scroll pane. Text areas usually scroll to
    // themselves whenever their text changes. We do not want this behavior for label text areas.
    override fun scrollRectToVisible(aRect: Rectangle) {}
}


fun newLabelTextPane() = object : JTextPane() {
    init {
        background = null
        isEditable = false
        isFocusable = false
    }

    // Disable the ability to scroll for the same reason as explained above.
    override fun scrollRectToVisible(aRect: Rectangle) {}
}


fun newLabelEditorPane(type: String, text: String? = null) = object : JEditorPane(type, text) {
    init {
        background = null
        isEditable = false
        isFocusable = false
    }

    // Disable the ability to scroll for the same reason as explained above.
    override fun scrollRectToVisible(aRect: Rectangle) {}
}


fun newToolbarButton(
    icon: Icon,
    tooltip: String,
    shortcutKeyCode: Int,
    shortcutModifiers: Int,
    listener: (() -> Unit)? = null
) = JButton(icon).also { btn ->
    cfgForToolbar(btn, tooltip, shortcutKeyCode, shortcutModifiers)
    if (listener != null)
        btn.addActionListener { listener() }
}

fun newToolbarToggleButton(
    icon: Icon,
    tooltip: String,
    shortcutKeyCode: Int,
    shortcutModifiers: Int,
    isSelected: Boolean = false,
    listener: ((Boolean) -> Unit)? = null
) = JToggleButton(icon, isSelected).also { btn ->
    cfgForToolbar(btn, tooltip, shortcutKeyCode, shortcutModifiers)
    if (listener != null)
        btn.addItemListener { listener(it.stateChange == ItemEvent.SELECTED) }
}

private fun cfgForToolbar(btn: AbstractButton, tooltip: String, shortcutKeyCode: Int, shortcutModifiers: Int) {
    btn.putClientProperty(BUTTON_TYPE, BUTTON_TYPE_TOOLBAR_BUTTON)
    btn.isFocusable = false

    if (shortcutKeyCode == 0) {
        btn.toolTipText = tooltip
        return
    }
    var shortcutHint = getKeyText(shortcutKeyCode)
    if (shortcutModifiers != 0)
        shortcutHint = getModifiersExText(shortcutModifiers) + "+" + shortcutHint
    btn.toolTipText = if ("<br>" !in tooltip) "$tooltip ($shortcutHint)" else {
        val idx = tooltip.indexOf("<br>")
        tooltip.substring(0, idx) + " ($shortcutHint)" + tooltip.substring(idx)
    }
}


fun JComponent.setTableCellBackground(table: JTable, rowIdx: Int) {
    background = if (rowIdx % 2 == 0) table.background else UIManager.getColor("Table.alternateRowColor")
}


fun Document.addDocumentListener(listener: (DocumentEvent) -> Unit) {
    addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = listener(e)
        override fun removeUpdate(e: DocumentEvent) = listener(e)
        override fun changedUpdate(e: DocumentEvent) = listener(e)
    })
}


fun JFileChooser.fullySetSelectedFile(file: File) {
    selectedFile = file
    // If we're selecting a dir and the selection doesn't exist yet or is a regular file, calling setSelectedFile()
    // doesn't actually put the non-existent portion into the file name textbox, so we have to set that manually.
    // Adapted from MetalFileChooserUI.doSelectedFileChanged().
    if (fileSelectionMode == JFileChooser.DIRECTORIES_ONLY && !file.isDirectory)
        (ui as BasicFileChooserUI).fileName = file.path
}


class LargeCheckBox(size: Int) : JCheckBox() {

    init {
        icon = LargeCheckBoxIcon(size - 2)
        putClientProperty(STYLE, "margin: 1,0,1,0")
    }

    private class LargeCheckBoxIcon(private val size: Int) : FlatCheckBoxIcon() {

        private val innerFocusWidth = (UIManager.get("Component.innerFocusWidth") as Number).toFloat()

        init {
            focusWidth = 0f
            borderColor = UIManager.getColor("Component.borderColor")
            selectedBorderColor = borderColor
            focusedBackground = background
        }

        // scale() is actually necessary here because otherwise, the icon is too small, at least when the UI font size
        // is explicitly increased.
        override fun getIconWidth() = UIScale.scale(size)
        override fun getIconHeight() = UIScale.scale(size)

        override fun paintFocusBorder(c: Component, g: Graphics2D) {
            throw UnsupportedOperationException()
        }

        override fun paintBorder(c: Component, g: Graphics2D, borderWidth: Float) {
            if (borderWidth != 0f)
                g.fillRoundRect(0, 0, size, size, arc, arc)
        }

        override fun paintBackground(c: Component, g: Graphics2D, borderWidth: Float) {
            val bw = if (FlatUIUtils.isPermanentFocusOwner(c)) borderWidth + innerFocusWidth else borderWidth
            g.fill(RoundRectangle2D.Float(bw, bw, size - 2 * bw, size - 2 * bw, arc - bw, arc - bw))
        }

        override fun paintCheckmark(c: Component, g: Graphics2D) {
            g.preserveTransform {
                val offset = (size - 14) / 2
                g.translate(offset - 1, offset)
                g.stroke = BasicStroke(2f)
                g.draw(Path2D.Float().apply {
                    // Taken from FlatCheckBoxIcon:
                    moveTo(4.5f, 7.5f)
                    lineTo(6.6f, 10f)
                    lineTo(11.25f, 3.5f)
                })
            }
        }

    }

}


class DropdownPopupMenu(
    private val owner: Component,
    private val preShow: (() -> Unit)? = null,
    private val postShow: (() -> Unit)? = null
) : JPopupMenu(), PopupMenuListener {

    var lastOpenTime = 0L
        private set
    private var lastCloseTime = 0L

    init {
        // borderInsets: Based on Component.borderWidth, which is 1 by default.
        // background: Should be ComboBox.popupBackground, but that's not set by default, and the fallback is List.
        putClientProperty(STYLE, "borderInsets: 1,1,1,1; background: \$List.background")

        addPopupMenuListener(this)
    }

    // @formatter:off
    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) { lastOpenTime = System.currentTimeMillis() }
    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) { lastCloseTime = System.currentTimeMillis() }
    override fun popupMenuCanceled(e: PopupMenuEvent) {}
    // @formatter:on

    fun toggle() {
        // When the user clicks on the box button while the popup is open, it first closes because the user clicked
        // outside the popup, and then the button is informed, triggering this method. This would immediately re-open
        // the popup. We avoid that via this hack.
        if (System.currentTimeMillis() - lastCloseTime < 100)
            return

        if (isVisible)
            isVisible = false
        else if (componentCount != 0) {
            preShow?.invoke()
            val ownerY = owner.locationOnScreen.y
            val ownerHeight = owner.height
            val popupHeight = preferredSize.height
            val screenBounds = owner.graphicsConfiguration.usableBounds
            val popupYRelToOwnerY = when {
                ownerY + ownerHeight + popupHeight <= screenBounds.y + screenBounds.height -> ownerHeight
                ownerY - popupHeight >= screenBounds.y -> -popupHeight
                else -> screenBounds.y + (screenBounds.height - popupHeight) / 2 - ownerY
            }
            show(owner, 0, popupYRelToOwnerY)
            postShow?.invoke()
        }
    }

    fun reactToOwnerKeyPressed(e: KeyEvent) {
        val m = e.modifiersEx
        val k = e.keyCode
        if (m == 0 && k == VK_SPACE ||
            m == ALT_DOWN_MASK && (k == VK_DOWN || k == VK_KP_DOWN || k == VK_UP || k == VK_KP_UP)
        )
            toggle()
    }

}

open class DropdownPopupMenuCheckBoxItem<E>(
    private val popup: DropdownPopupMenu,
    val item: E,
    label: String,
    icon: Icon? = null,
    isSelected: Boolean = false
) : JCheckBoxMenuItem(label, icon, isSelected), ActionListener {

    init {
        putClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick", true)
        addActionListener(this)
    }

    final override fun actionPerformed(e: ActionEvent) {
        // If we don't do this, the menu item loses the hover/navigation effect when it's toggled.
        SwingUtilities.invokeLater { isArmed = true }

        // When the user opens a popup that is so long it overlaps the box button, him releasing the mouse
        // immediately afterward actually selects the item he's hovering over if he moved the mouse ever so
        // slightly. To avoid this undesired behavior, we cancel any item change that comes in too soon after the
        // popup has been opened.
        if (System.currentTimeMillis() - popup.lastOpenTime < 300) {
            removeActionListener(this)
            isSelected = !isSelected
            addActionListener(this)
            return
        }

        onToggle()
    }

    open fun onToggle() {}

}


class WordWrapCellRenderer(allowHtml: Boolean = false, private val shrink: Boolean = false) : TableCellRenderer {

    private val comp = when (allowHtml) {
        false -> newLabelTextArea(insets = true)
        true -> newLabelEditorPane("text/html")
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, rowIdx: Int, colIdx: Int
    ) = comp.apply {
        text = value as String
        setSize(table.columnModel.getColumn(colIdx).width, preferredSize.height)
        val newHeight = preferredSize.height
        val oldHeight = table.getRowHeight(rowIdx)
        if (if (shrink) newHeight != oldHeight else newHeight > oldHeight)
            table.setRowHeight(rowIdx, newHeight)
        setTableCellBackground(table, rowIdx)
    }

}


class CustomToStringListCellRenderer<E>(
    private val itemClass: Class<E>,
    private val toString: (E) -> String
) : ListCellRenderer<E> {

    private val delegate = DefaultListCellRenderer()

    override fun getListCellRendererComponent(
        list: JList<out E>?, value: E, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        delegate.text = value?.let { toString(itemClass.cast(it)) } ?: ""
        return delegate
    }

}


class LabeledListCellRenderer<E>(
    private val wrapped: ListCellRenderer<in E>,
    private val groupSpacing: Int = 0,
    private val getLabelLines: (Int) -> List<String>
) : ListCellRenderer<E> {

    override fun getListCellRendererComponent(
        list: JList<out E>, value: E?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val cell = wrapped.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        // Show the label lines only in the popup menu, but not in the combo box.
        if (index != -1) {
            val labelLines = getLabelLines(index)
            if (labelLines.isNotEmpty())
                (cell as JComponent).border = CompoundBorder(LabelListCellBorder(list, labelLines), cell.border)
        }
        return cell
    }

    /**
     * To be used in list cell components. Displays a separating label above the component.
     * Alternatively displays multiple separating labels above the component, interspersed by
     * non-separating labels. This can be used to show that some list category is empty.
     *
     * Inspired from here:
     * https://github.com/JFormDesigner/FlatLaf/blob/master/flatlaf-demo/src/main/java/com/formdev/flatlaf/demo/intellijthemes/ListCellTitledBorder.java
     */
    private inner class LabelListCellBorder(private val list: JList<*>, val lines: List<String>) : Border {

        override fun isBorderOpaque() = true
        override fun getBorderInsets(c: Component) =
            Insets(lines.size * c.getFontMetrics(list.font).height + groupSpacing * (lines.size - 1) / 2, 0, 0, 0)

        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val g2 = g as Graphics2D
            val fontMetrics = c.getFontMetrics(list.font)

            // Draw the list background.
            g2.color = list.background
            g2.fillRect(x, y, width, getBorderInsets(c).top)

            FlatUIUtils.setRenderingHints(g2)
            g2.color = UIManager.getColor("Label.disabledForeground")
            g2.font = list.font

            for ((line, text) in lines.withIndex()) {
                val lineY = y + line * fontMetrics.height + line / 2 * groupSpacing
                val textWidth = fontMetrics.stringWidth(text)
                // Draw the centered string.
                FlatUIUtils.drawString(list, g2, text, x + (width - textWidth) / 2, lineY + fontMetrics.ascent)
                // On even lines, draw additional separator lines.
                if (line % 2 == 0) {
                    val sepGap = 4
                    val sepWidth = (width - textWidth) / 2 - 2 * sepGap
                    if (sepWidth > 0) {
                        val sepY = lineY + fontMetrics.height / 2
                        val sepHeight = 1
                        g2.fillRect(x + sepGap, sepY, sepWidth, sepHeight)
                        g2.fillRect((x + width - sepGap - sepWidth), sepY, sepWidth, sepHeight)
                    }
                }
            }
        }

    }

}


class CustomToStringKeySelectionManager<E>(
    private val itemClass: Class<E>,
    private val toString: (E) -> String
) : JComboBox.KeySelectionManager {

    private var lastTime = 0L
    private var prefix = ""

    override fun selectionForKey(key: Char, model: ComboBoxModel<*>): Int {
        var startIdx = model.getElements().indexOfFirst { it === model.selectedItem }

        val timeFactor = UIManager.get("ComboBox.timeFactor") as Long? ?: 1000L
        val currTime = System.currentTimeMillis()
        if (currTime - lastTime < timeFactor)
            if (prefix.length == 1 && key == prefix[0])
                startIdx++
            else
                prefix += key
        else {
            startIdx++
            prefix = key.toString()
        }
        lastTime = currTime

        fun startsWith(elem: E) = toString(elem).startsWith(prefix, ignoreCase = true)
        val foundIdx = model.getElements(startIdx).indexOfFirst(::startsWith)
        return if (foundIdx != -1)
            startIdx + foundIdx
        else
            model.getElements(0, startIdx).indexOfFirst(::startsWith)
    }

    private fun ComboBoxModel<*>.getElements(startIdx: Int = 0, endIdx: Int = -1): List<E> = mutableListOf<E>().also {
        val endIdx2 = if (endIdx == -1) size else endIdx
        for (idx in startIdx..<endIdx2)
            it.add(itemClass.cast(getElementAt(idx)))
    }

}


var minimumWindowSize = Dimension(600, 450)

fun Window.setup() {
    minimumSize = minimumWindowSize
    when (this) {
        is JFrame -> defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        is JDialog -> defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
    }
}

fun Window.center(onScreen: GraphicsConfiguration, widthFrac: Double, heightFrac: Double) {
    val winBounds = onScreen.usableBounds
    val width = (winBounds.width * widthFrac).roundToInt().coerceAtLeast(minimumSize.width)
    val height = (winBounds.height * heightFrac).roundToInt().coerceAtLeast(minimumSize.height)
    setBounds(
        winBounds.x + (winBounds.width - width) / 2,
        winBounds.y + (winBounds.height - height) / 2,
        width, height
    )
}

var disableSnapToSide = false

fun Window.snapToSide(onScreen: GraphicsConfiguration, rightSide: Boolean) {
    if (disableSnapToSide)
        return

    val winBounds = onScreen.usableBounds
    winBounds.width /= 2

    // If the window should snap to the right side, move its x coordinate.
    if (rightSide)
        winBounds.x += winBounds.width

    // Apply the computed window bounds.
    bounds = winBounds

    // On Windows 10, windows have a thick invisible border which resides inside the window bounds.
    // If we do nothing about it, the window is not flush with the sides of the screen, but there's a thick strip
    // of empty space between the window and the left, right, and bottom sides of the screen.
    // To fix this, we find the exact thickness of the border by querying the window's insets (which we can only do
    // after the window has been opened, hence the listener) and add those insets to the window bounds on the left,
    // right, and bottom sides.
    if (SystemInfo.isWindows_10_orLater)
        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent?) {
                val winInsets = insets
                setBounds(
                    winBounds.x - winInsets.left,
                    winBounds.y,
                    winBounds.width + winInsets.left + winInsets.right,
                    winBounds.height + winInsets.bottom
                )
            }
        })
}

val GraphicsConfiguration.usableBounds: Rectangle
    get() {
        val bounds = bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(this)
        return Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom
        )
    }


class KeyListener(
    private val shortcutKeyCode: Int,
    private val shortcutModifiers: Int,
    private val listener: () -> Unit
) {
    fun onKeyEvent(e: KeyEvent): Boolean {
        val match = e.id == KEY_PRESSED && e.keyCode == shortcutKeyCode && e.modifiersEx == shortcutModifiers
        if (match)
            listener()
        return match
    }
}


fun tryOpen(file: Path) = openCascade(file.toUri(), Desktop.Action.OPEN) { Desktop.getDesktop().open(file.toFile()) }
fun tryBrowse(uri: URI) = openCascade(uri, Desktop.Action.BROWSE) { Desktop.getDesktop().browse(uri) }
fun tryMail(uri: URI) = openCascade(uri, Desktop.Action.MAIL) { Desktop.getDesktop().mail(uri) }

private fun openCascade(uri: URI, desktopAction: Desktop.Action, desktopFunction: () -> Unit) {
    fun tryExec(cmd: Array<String>) = try {
        Runtime.getRuntime().exec(cmd)
        true
    } catch (_: IOException) {
        false
    }

    // This cascade is required because:
    //   - Desktop.open() doesn't always open the actually configured file browser on KDE.
    //   - KDE is not supported by Desktop.browse()/mail().
    if (SystemInfo.isKDE && tryExec(arrayOf("kde-open", uri.toString())))
        return
    if (SystemInfo.isLinux && tryExec(arrayOf("xdg-open", uri.toString())))
        return
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(desktopAction))
        desktopFunction()
}


/**
 * On macOS, one is unable to show a progress bar on top of the app's icon in the dock as soon as the app is launched
 * via a jpackage-built binary. Fortunately, updating the icon once on startup with this function resolves the issue.
 *
 * This bug will hopefully be fixed in a future version of the JDK. It is tracked here:
 * https://bugs.openjdk.org/browse/JDK-8300037
 */
fun fixTaskbarProgressBarOnMacOS() {
    if (SystemInfo.isMacOS && Taskbar.Feature.ICON_IMAGE.isSupported)
        taskbar!!.iconImage = taskbar.iconImage
}

fun trySetTaskbarIconBadge(badge: Int) {
    if (Taskbar.Feature.ICON_BADGE_NUMBER.isSupported)
        taskbar!!.setIconBadge(if (badge == 0) null else badge.toString())
}

fun trySetTaskbarProgress(window: Window, percent: Int) {
    if (Taskbar.Feature.PROGRESS_VALUE.isSupported)
        taskbar!!.setProgressValue(percent)
    if (Taskbar.Feature.PROGRESS_VALUE_WINDOW.isSupported)
        taskbar!!.setWindowProgressValue(window, percent)
}

fun tryRequestUserAttentionInTaskbar(window: Window) {
    if (Taskbar.Feature.USER_ATTENTION.isSupported)
        taskbar!!.requestUserAttention(true, false)
    if (Taskbar.Feature.USER_ATTENTION_WINDOW.isSupported)
        taskbar!!.requestWindowUserAttention(window)
}

private val taskbar = if (Taskbar.isTaskbarSupported()) Taskbar.getTaskbar() else null

private val Taskbar.Feature.isSupported
    get() = taskbar != null && taskbar.isSupported(this)
