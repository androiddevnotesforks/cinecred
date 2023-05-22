package com.loadingbyte.cinecred.ui.view.welcome

import com.loadingbyte.cinecred.common.l10n
import com.loadingbyte.cinecred.projectio.STYLING_FILE_NAME
import com.loadingbyte.cinecred.projectio.service.Service
import com.loadingbyte.cinecred.ui.comms.*
import com.loadingbyte.cinecred.ui.helper.WINDOW_ICON_IMAGES
import com.loadingbyte.cinecred.ui.helper.center
import com.loadingbyte.cinecred.ui.helper.setup
import com.loadingbyte.cinecred.ui.makeWelcomeHintTrack
import com.loadingbyte.cinecred.ui.play
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import java.util.*
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities


class WelcomeFrame(private val welcomeCtrl: WelcomeCtrlComms) : JFrame(l10n("ui.welcome.title")), WelcomeViewComms {

    val panel = WelcomePanel(welcomeCtrl)

    init {
        setup()
        iconImages = WINDOW_ICON_IMAGES

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                // Due to the card layout inside the panel, the focus is initially "lost" somewhere, and hence tabbing
                // has no effect. By calling this method, we make the focus available again.
                requestFocusInWindow()
            }

            override fun windowClosing(e: WindowEvent) {
                welcomeCtrl.close()
            }
        })

        rememberedBounds?.also(::setBounds) ?: center(
            onScreen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration,
            0.4, 0.55
        )

        contentPane.add(panel)
    }


    /* ***************************
       ********** COMMS **********
       *************************** */

    // @formatter:off
    override fun display() { isVisible = true }
    override fun close() { rememberedBounds = bounds; dispose() }
    override fun isFromWelcomeWindow(event: KeyEvent): Boolean = SwingUtilities.getRoot(event.component) == this
    // @formatter:on

    override fun getMostOccupiedScreen() =
        if (isVisible)
            graphicsConfiguration!!
        else {
            // This branch is used when opening a project without actually showing the welcome frame, i.e., when
            // dragging a project folder onto the program.
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
                .map { dev -> dev.defaultConfiguration }
                .maxByOrNull { cfg -> cfg.bounds.intersection(bounds).run { width * height } }!!
        }

    override fun playHintTrack() {
        makeWelcomeHintTrack(this).play(onPass = welcomeCtrl::onPassHintTrack)
    }

    override fun getTab() = panel.getTab()
    override fun setTab(tab: WelcomeTab) = panel.setTab(tab)
    override fun setTabsLocked(locked: Boolean) = panel.setTabsLocked(locked)

    // @formatter:off
    override fun projects_setCard(card: ProjectsCard) =
        panel.projectsPanel.projects_setCard(card)
    override fun projects_start_setMemorized(projectDirs: List<Path>) =
        panel.projectsPanel.projects_start_setMemorized(projectDirs)
    override fun projects_openBrowse_setCurrentDir(dir: Path) =
        panel.projectsPanel.projects_openBrowse_setCurrentDir(dir)
    override fun projects_openBrowse_setDoneEnabled(enabled: Boolean) =
        panel.projectsPanel.projects_openBrowse_setDoneEnabled(enabled)
    override fun projects_createBrowse_setCurrentDir(dir: Path) =
        panel.projectsPanel.projects_createBrowse_setCurrentDir(dir)
    override fun projects_createBrowse_setSelection(dir: Path) =
        panel.projectsPanel.projects_createBrowse_setSelection(dir)
    override fun projects_createBrowse_setNextEnabled(enabled: Boolean) =
        panel.projectsPanel.projects_createBrowse_setNextEnabled(enabled)
    override fun projects_createConfigure_setProjectDir(projectDir: Path) =
        panel.projectsPanel.projects_createConfigure_setProjectDir(projectDir)
    override fun projects_createConfigure_setServices(services: List<Service>) =
        panel.projectsPanel.projects_createConfigure_setServices(services)
    override fun projects_createConfigure_setCreditsName(name: String) =
        panel.projectsPanel.projects_createConfigure_setCreditsName(name)
    override fun projects_createWait_setError(error: String?) =
        panel.projectsPanel.projects_createWait_setError(error)

    override fun preferences_setCard(card: PreferencesCard) =
        panel.preferencesPanel.preferences_setCard(card)
    override fun preferences_start_setPreferences(preferences: Preferences) =
        panel.preferencesPanel.preferences_start_setPreferences(preferences)
    override fun preferences_start_setInitialSetup(initialSetup: Boolean, doneListener: (() -> Unit)?) =
        panel.preferencesPanel.preferences_start_setInitialSetup(initialSetup, doneListener)
    override fun preferences_start_setServices(services: List<Service>) =
        panel.preferencesPanel.preferences_start_setServices(services)
    override fun preferences_start_setServiceRemovalLocked(service: Service, locked: Boolean) =
        panel.preferencesPanel.preferences_start_setServiceRemovalLocked(service, locked)
    override fun preferences_authorizeService_setError(error: String?) =
        panel.preferencesPanel.preferences_authorizeService_setError(error)
    // @formatter:on

    override fun setChangelog(changelog: String) = panel.setChangelog(changelog)
    override fun setLicenses(licenses: List<License>) = panel.setLicenses(licenses)
    override fun setUpdate(version: String) = panel.setUpdate(version)

    override fun showNotADirMessage(path: Path) {
        JOptionPane.showMessageDialog(
            this, l10n("ui.projects.notADir.msg", path),
            l10n("ui.projects.notADir.title"), JOptionPane.ERROR_MESSAGE
        )
    }

    override fun showIllegalPathMessage(path: Path) {
        JOptionPane.showMessageDialog(
            this, l10n("ui.projects.illegalPath.msg", path),
            l10n("ui.projects.illegalPath.title"), JOptionPane.ERROR_MESSAGE
        )
    }

    override fun showNotAProjectMessage(dir: Path) {
        JOptionPane.showMessageDialog(
            this, l10n("ui.projects.notAProject.msg", dir, STYLING_FILE_NAME),
            l10n("ui.projects.notAProject.title"), JOptionPane.ERROR_MESSAGE
        )
    }

    override fun showAlreadyOpenMessage(projectDir: Path) {
        JOptionPane.showMessageDialog(
            this, l10n("ui.projects.alreadyOpen.msg", projectDir),
            l10n("ui.projects.alreadyOpen.title"), JOptionPane.ERROR_MESSAGE
        )
    }

    override fun showNotEmptyQuestion(projectDir: Path): Boolean {
        return JOptionPane.showConfirmDialog(
            this, l10n("ui.projects.create.notEmpty.msg", projectDir),
            l10n("ui.projects.create.notEmpty.title"), JOptionPane.YES_NO_OPTION
        ) == JOptionPane.YES_OPTION
    }

    override fun showRestartUILocaleQuestion(newLocale: Locale): Boolean {
        return JOptionPane.showConfirmDialog(
            this, l10n("ui.preferences.restartUILocale.msg", newLocale),
            l10n("ui.preferences.restartUILocale.title"), JOptionPane.OK_CANCEL_OPTION
        ) == JOptionPane.OK_OPTION
    }

    override fun showCannotRemoveServiceMessage(service: Service, error: String) {
        JOptionPane.showMessageDialog(
            this, arrayOf(l10n("ui.preferences.services.cannotRemove.msg", service.id), error),
            l10n("ui.preferences.services.cannotRemove.title"), JOptionPane.ERROR_MESSAGE
        )
    }


    companion object {
        private var rememberedBounds: Rectangle? = null
    }

}
