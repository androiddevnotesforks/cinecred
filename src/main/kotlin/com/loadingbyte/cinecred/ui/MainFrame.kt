package com.loadingbyte.cinecred.ui

import com.loadingbyte.cinecred.common.l10n
import com.loadingbyte.cinecred.ui.helper.*
import java.awt.GraphicsEnvironment
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JTabbedPane


object MainFrame : JFrame("Cinecred") {

    private val tabs = JTabbedPane()

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                Controller.tryExit()
            }
        })

        // Make the window fill the right half of the screen.
        val maxWinBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        setSize(maxWinBounds.width / 2, maxWinBounds.height)
        setLocation(maxWinBounds.x, maxWinBounds.y)

        iconImages = WINDOW_ICON_IMAGES

        tabs.apply {
            addTab(l10n("ui.main.createAndOpen"), FOLDER_ICON, OpenPanel)
            addTab(l10n("ui.main.style"), EYE_ICON, EditPanel)
            addTab(l10n("ui.main.video"), PLAY_ICON, VideoPanel)
            addTab(l10n("ui.main.deliver"), DELIVER_ICON, DeliverPanel)
            for (tabIdx in 1 until tabCount)
                setEnabledAt(tabIdx, false)
            addChangeListener {
                Controller.onChangeTab(changedToEdit = selectedComponent == EditPanel)
            }
        }
        contentPane.add(tabs)
    }

    fun onOpenProjectDir() {
        tabs.apply {
            for (tabIdx in 1 until tabCount)
                setEnabledAt(tabIdx, true)
            selectedIndex = 1
        }
    }

}
