package com.loadingbyte.cinecred.ui.comms

import com.loadingbyte.cinecred.common.FPS
import com.loadingbyte.cinecred.common.Resolution
import com.loadingbyte.cinecred.common.TimecodeFormat
import com.loadingbyte.cinecred.imaging.Color4f
import com.loadingbyte.cinecred.projectio.SpreadsheetFormat
import com.loadingbyte.cinecred.projectio.service.Account
import com.loadingbyte.cinecred.projectio.service.Service
import com.loadingbyte.cinecred.ui.ConfigurableOverlay
import com.loadingbyte.cinecred.ui.LocaleWish
import com.loadingbyte.cinecred.ui.Preference
import com.loadingbyte.cinecred.ui.helper.FileExtAssortment
import java.awt.GraphicsConfiguration
import java.awt.event.KeyEvent
import java.nio.file.Path
import java.util.*


interface WelcomeCtrlComms {

    fun close()

    // ========== FOR MASTER CTRL ==========

    fun onGlobalKeyEvent(event: KeyEvent): Boolean
    fun commence(openProjectDir: Path? = null)
    fun setTab(tab: WelcomeTab)

    // ========== FOR WELCOME VIEW ==========

    fun onPassHintTrack()

    fun projects_start_onClickOpen()
    fun projects_start_onCompleteOpenDialog(projectDir: Path?)
    fun projects_start_onClickOpenMemorized(projectDir: Path)
    fun projects_start_onClickCreate()
    fun projects_start_onCompleteCreateDialog(projectDir: Path?)
    fun projects_start_onDrop(path: Path)

    fun projects_createConfigure_onClickBack()
    fun projects_createConfigure_onClickDone(
        locale: Locale,
        resolution: Resolution,
        fps: FPS,
        timecodeFormat: TimecodeFormat,
        sample: Boolean,
        creditsLocation: CreditsLocation,
        creditsFormat: SpreadsheetFormat,
        creditsAccount: Account?,
        creditsFilename: String
    )

    fun projects_createWait_onClickCancel()

    fun <P : Any> preferences_start_onChangeTopPreference(preference: Preference<P>, value: P)
    fun preferences_start_onClickAddAccount()
    fun preferences_start_onClickRemoveAccount(account: Account)
    fun preferences_start_onClickAddOverlay()
    fun preferences_start_onClickEditOverlay(overlay: ConfigurableOverlay)
    fun preferences_start_onClickRemoveOverlay(overlay: ConfigurableOverlay)

    fun preferences_configureAccount_verifyLabel(label: String): String? // Returns an error.
    fun preferences_configureAccount_onClickCancel()
    fun preferences_configureAccount_onClickAuthorize(label: String, service: Service)

    fun preferences_authorizeAccount_onClickCancel()

    fun preferences_configureOverlay_onClickCancel()
    fun preferences_configureOverlay_onClickDoneOrApply(
        done: Boolean,
        type: Class<out ConfigurableOverlay>,
        name: String,
        aspectRatioH: Double,
        aspectRatioV: Double,
        linesColor: Color4f?,
        linesH: List<Int>,
        linesV: List<Int>,
        imageFile: Path,
        imageUnderlay: Boolean
    )

}


interface WelcomeViewComms {

    fun display()
    fun close()
    fun isFromWelcomeWindow(event: KeyEvent): Boolean
    fun getMostOccupiedScreen(): GraphicsConfiguration

    fun playHintTrack()
    fun getTab(): WelcomeTab
    fun setTab(tab: WelcomeTab)
    fun setTabsLocked(locked: Boolean)

    fun projects_setCard(card: ProjectsCard)

    fun projects_start_setMemorized(projectDirs: List<Path>)
    fun projects_start_showOpenDialog(dir: Path?)
    fun projects_start_showCreateDialog(dir: Path?)

    fun projects_createConfigure_setProjectDir(projectDir: Path)
    fun projects_createConfigure_setAccounts(accounts: List<Account>)
    fun projects_createConfigure_setCreditsFilename(filename: String)

    fun projects_createWait_setError(error: String?)

    fun preferences_setCard(card: PreferencesCard)

    fun preferences_start_setInitialSetup(initialSetup: Boolean, doneListener: (() -> Unit)?)
    fun preferences_start_setUILocaleWish(wish: LocaleWish)
    fun preferences_start_setCheckForUpdates(check: Boolean)
    fun preferences_start_setWelcomeHintTrackPending(pending: Boolean)
    fun preferences_start_setProjectHintTrackPending(pending: Boolean)
    fun preferences_start_setAccounts(accounts: List<Account>)
    fun preferences_start_setAccountRemovalLocked(account: Account, locked: Boolean)
    fun preferences_start_setOverlays(overlays: List<ConfigurableOverlay>)

    fun preferences_configureAccount_resetForm()

    fun preferences_authorizeAccount_setError(error: String?)

    fun preferences_configureOverlay_setForm(
        type: Class<out ConfigurableOverlay>,
        name: String,
        aspectRatioH: Double,
        aspectRatioV: Double,
        linesColor: Color4f?,
        linesH: List<Int>,
        linesV: List<Int>,
        imageFile: Path,
        imageUnderlay: Boolean
    )

    fun preferences_configureOverlay_clearImageFile()
    fun preferences_configureOverlay_setImageFileExtAssortment(fileExtAssortment: FileExtAssortment?)

    fun setChangelog(changelog: String)
    fun setAbout(about: String)
    fun setLicenses(licenses: List<License>)
    fun setUpdate(version: String)

    fun showNotADirMessage(path: Path)
    fun showIllegalPathMessage(path: Path)
    fun showNotAProjectMessage(dir: Path)
    fun showAlreadyOpenMessage(projectDir: Path)
    fun showNewerVersionQuestion(projectDir: Path, projectVersion: String): Boolean
    fun showNotEmptyQuestion(projectDir: Path): Boolean
    fun showRestartUILocaleQuestion(newLocale: Locale): Boolean
    fun showCannotRemoveAccountMessage(account: Account, error: String)
    fun showCannotReadOverlayImageMessage(file: Path, error: String)

}


enum class WelcomeTab { PROJECTS, PREFERENCES, CHANGELOG, ABOUT, UPDATE }
enum class ProjectsCard { START, CREATE_CONFIGURE, CREATE_WAIT }
enum class CreditsLocation { LOCAL, SERVICE, SKIP }
enum class PreferencesCard { START, CONFIGURE_ACCOUNT, AUTHORIZE_ACCOUNT, CONFIGURE_OVERLAY }


class License(val name: String, val body: String)
