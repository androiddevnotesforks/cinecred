package com.loadingbyte.cinecred.ui

import com.loadingbyte.cinecred.common.Picture
import com.loadingbyte.cinecred.common.Severity.ERROR
import com.loadingbyte.cinecred.drawer.VideoDrawer
import com.loadingbyte.cinecred.drawer.VideoDrawer.Mode.NO_DRAWING
import com.loadingbyte.cinecred.drawer.drawPages
import com.loadingbyte.cinecred.drawer.getBundledFont
import com.loadingbyte.cinecred.drawer.getSystemFont
import com.loadingbyte.cinecred.project.*
import com.loadingbyte.cinecred.projectio.*
import com.loadingbyte.cinecred.ui.comms.MasterCtrlComms
import com.loadingbyte.cinecred.ui.ctrl.PersistentStorage
import com.loadingbyte.cinecred.ui.helper.FontFamilies
import com.loadingbyte.cinecred.ui.helper.JobSlot
import kotlinx.collections.immutable.toPersistentList
import java.awt.Font
import java.awt.GraphicsConfiguration
import java.awt.event.KeyEvent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.swing.SwingUtilities


class ProjectController(
    val masterCtrl: MasterCtrlComms,
    val projectDir: Path,
    val openOnScreen: GraphicsConfiguration,
    private val onClose: () -> Unit
) {

    val projectName: String = projectDir.fileName.toString()

    val stylingCtx: StylingContext
    val stylingHistory: StylingHistory

    val projectFrame = ProjectFrame(this)
    val stylingDialog = StylingDialog(this)
    val videoDialog = VideoDialog(this)
    val deliveryDialog = DeliveryDialog(this)

    private val stylingFile = projectDir.resolve(STYLING_FILE_NAME)
    private var creditsFile: Path? = null

    private var creditsSpreadsheet = Spreadsheet(emptyList())
    private val fonts = HashMap<Path, List<Font>>()
    private val fontsByName = HashMap<String, Font>()
    private val pictureLoadersByRelPath = HashMap<Path, Lazy<Picture?>>()

    // The state that is relevant for pushStateIntoUI().
    private var creditsFileLocatingLog: List<ParserMsg> = emptyList()
    private var creditsFileLoadingLog: List<ParserMsg> = emptyList()
    private var creditsFileReadingLog: List<ParserMsg> = emptyList()
    private var stylingError = false
    private var excessivePageSizeError = false
    private var project: Project? = null
    private var drawnPages: List<DrawnPage> = emptyList()
    private var runtime = 0

    private val readCreditsAndRedrawJobSlot = JobSlot()

    init {
        stylingCtx = StylingContextImpl(fontsByName)

        projectFrame.isVisible = true
        stylingDialog.isVisible = true

        if (PersistentStorage.projectHintTrackPending)
            makeProjectHintTrack(this).play(onPass = { PersistentStorage.projectHintTrackPending = false })

        // Load the initially present auxiliary files (project fonts and pictures).
        for (projectFile in Files.walk(projectDir))
            tryReloadAuxFile(projectFile)

        // Load the initial state of the styling from disk.
        stylingHistory = StylingHistory(readStyling(stylingFile, stylingCtx))

        // Try to find a credits file.
        tryLocateCreditsFile()
        // If present, load the initial credits spreadsheet from disk.
        tryReloadCreditsFile()
        // If present, read and draw the credits.
        tryReadCreditsAndRedraw()

        // Watch for future changes in the new project dir.
        RecursiveFileWatcher.watch(projectDir) { event: RecursiveFileWatcher.Event, file: Path ->
            SwingUtilities.invokeLater {
                when {
                    hasCreditsFileName(file) -> {
                        val creditsFile = this.creditsFile
                        tryLocateCreditsFile()
                        val newCreditsFile = this.creditsFile
                        if (file == newCreditsFile || newCreditsFile == null ||
                            (creditsFile != null && !safeIsSameFile(creditsFile, newCreditsFile))
                        ) {
                            tryReloadCreditsFile()
                            tryReadCreditsAndRedraw()
                        } else
                            pushStateIntoUI()  // Update the log entry regarding multiple credits files.
                    }
                    event == RecursiveFileWatcher.Event.MODIFY ->
                        if (tryReloadAuxFile(file)) tryReadCreditsAndRedraw()
                    event == RecursiveFileWatcher.Event.DELETE ->
                        if (tryRemoveAuxFile(file)) tryReadCreditsAndRedraw()
                }
            }
        }
    }

    private fun safeIsSameFile(p1: Path, p2: Path) =
        try {
            Files.isSameFile(p1, p2)
        } catch (_: IOException) {
            false
        }

    private fun pushStateIntoUI() {
        val log = creditsFileLocatingLog + creditsFileLoadingLog + creditsFileReadingLog
        projectFrame.panel.updateProject(project, drawnPages, runtime, stylingError, excessivePageSizeError, log)
        stylingDialog.panel.updateProject(project, runtime)
        videoDialog.panel.updateProject(project, drawnPages)
        deliveryDialog.panel.configurationForm.updateProject(project, drawnPages)
    }

    private fun tryReloadAuxFile(file: Path): Boolean {
        // If the file has been generated by a render job, don't reload the project. Otherwise, generating image
        // sequences would be very expensive because we would constantly reload the project. Note that we do not
        // only consider the current render job, but all render jobs in the render job list. This ensures that even
        // the last file generated by a render job doesn't reload the project even when the render job has already
        // been marked as complete by the time the OS notifies us about the newly generated file.
        if (deliveryDialog.panel.renderQueuePanel.renderJobs.any { it.generatesFile(file) })
            return false

        val newFonts = tryReadFonts(file)
        if (newFonts.isNotEmpty()) {
            fonts[file] = newFonts
            newFonts.associateByTo(fontsByName) { font -> font.getFontName(Locale.ROOT) }
            stylingDialog.panel.updateProjectFontFamilies(FontFamilies(fonts.values.flatten()))
            return true
        }

        tryReadPictureLoader(file)?.let { pictureLoader ->
            pictureLoadersByRelPath[projectDir.relativize(file)] = pictureLoader
            return true
        }

        return false
    }

    private fun tryRemoveAuxFile(file: Path): Boolean {
        val remFonts = fonts.remove(file)
        if (remFonts != null) {
            fontsByName.values.removeAll(remFonts)
            stylingDialog.panel.updateProjectFontFamilies(FontFamilies(fonts.values.flatten()))
            return true
        }
        if (pictureLoadersByRelPath.remove(projectDir.relativize(file)) != null)
            return true
        return false
    }

    private fun tryLocateCreditsFile() {
        val (file, log) = locateCreditsFile(projectDir)
        creditsFile = file
        creditsFileLocatingLog = log
    }

    private fun tryReloadCreditsFile() {
        creditsSpreadsheet = Spreadsheet(emptyList())
        creditsFileLoadingLog = emptyList()

        creditsFile?.let { creditsFile ->
            try {
                val (spreadsheet, log) = loadCreditsFile(creditsFile)
                creditsSpreadsheet = spreadsheet
                creditsFileLoadingLog = log
            } catch (_: IOException) {
                // An IO exception can occur if the credits file has disappeared in the meantime.
                // If that happens, the file watcher will quickly trigger a locating and then a reloading call.
            }
        }
    }

    private fun tryReadCreditsAndRedraw() {
        // Capture these variables in the state they are in when the function is called.
        val styling = stylingHistory.current
        val creditsSpreadsheet = this.creditsSpreadsheet

        // Reset these variables. We will set some of them in the following code, depending on which problems occur.
        creditsFileReadingLog = emptyList()
        stylingError = false
        excessivePageSizeError = false
        project = null
        drawnPages = emptyList()
        runtime = 0

        // If the credits file could not be located or loaded, abort and notify the UI about the error.
        if (creditsFileLocatingLog.any { it.severity == ERROR } || creditsFileLoadingLog.any { it.severity == ERROR })
            return pushStateIntoUI()

        // Freeze the font and picture loader maps so that they do not change while processing the project.
        val stylingCtx = StylingContextImpl(HashMap(fontsByName))
        val pictureLoadersByRelPath = HashMap(this.pictureLoadersByRelPath)

        // Execute the reading and drawing in another thread to not block the UI thread.
        readCreditsAndRedrawJobSlot.submit {
            // Verify the styling in the extra thread because that is not entirely cheap.
            // If the styling is erroneous, abort and notify the UI about the error.
            if (verifyConstraints(stylingCtx, styling).any { it.severity == ERROR })
                return@submit SwingUtilities.invokeLater { stylingError = true; pushStateIntoUI() }

            val (pages, runtimeGroups, log) = readCredits(creditsSpreadsheet, styling, pictureLoadersByRelPath)

            // If the credits spreadsheet could not be read and parsed, abort and notify the UI about the error.
            if (log.any { it.severity == ERROR })
                return@submit SwingUtilities.invokeLater { creditsFileReadingLog = log; pushStateIntoUI() }

            val project = Project(styling, stylingCtx, pages.toPersistentList(), runtimeGroups.toPersistentList())
            val drawnPages = drawPages(project)
            val runtime = VideoDrawer(project, drawnPages, mode = NO_DRAWING).numFrames

            SwingUtilities.invokeLater {
                creditsFileReadingLog = log
                this.project = project
                this.runtime = runtime
                // Limit each page's height to prevent the program from crashing due to misconfiguration.
                if (drawnPages.none { it.defImage.height.resolve() > 1_000_000.0 })
                    this.drawnPages = drawnPages
                else
                    excessivePageSizeError = true
                pushStateIntoUI()
            }
        }
    }

    fun tryCloseProject(force: Boolean = false): Boolean {
        if (force) {
            projectFrame.panel.onTryCloseProject()
            deliveryDialog.panel.renderQueuePanel.onTryCloseProject()
        } else if (
            !projectFrame.panel.onTryCloseProject() ||
            !deliveryDialog.panel.renderQueuePanel.onTryCloseProject()
        )
            return false

        onClose()

        projectFrame.dispose()
        for (type in ProjectDialogType.values())
            getDialog(type).dispose()

        // Cancel the previous project dir change watching order.
        RecursiveFileWatcher.unwatch(projectDir)

        // Dispose of all loaded pictures.
        for (pictureLoader in pictureLoadersByRelPath.values)
            if (pictureLoader.isInitialized())
                pictureLoader.value?.dispose()

        return true
    }

    private fun getDialog(type: ProjectDialogType) = when (type) {
        ProjectDialogType.STYLING -> stylingDialog
        ProjectDialogType.VIDEO -> videoDialog
        ProjectDialogType.DELIVERY -> deliveryDialog
    }

    fun setDialogVisible(type: ProjectDialogType, isVisible: Boolean) {
        getDialog(type).isVisible = isVisible
        projectFrame.panel.onSetDialogVisible(type, isVisible)
        if (type == ProjectDialogType.VIDEO && !isVisible)
            videoDialog.panel.onHide()
    }

    fun onGlobalKeyEvent(event: KeyEvent): Boolean {
        val window = SwingUtilities.getRoot(event.component)
        return when {
            window == videoDialog && videoDialog.panel.onKeyEvent(event) -> true
            (window == projectFrame || window == stylingDialog || window == videoDialog) &&
                    projectFrame.panel.onKeyEvent(event) -> true
            (window == projectFrame || window == stylingDialog || window == videoDialog) &&
                    stylingDialog.isVisible && stylingDialog.panel.onKeyEvent(event) -> true
            else -> false
        }
    }


    private class StylingContextImpl(private val fontsByName: Map<String, Font>) : StylingContext {
        override fun resolveFont(name: String): Font? =
            fontsByName[name] ?: getBundledFont(name) ?: getSystemFont(name)
    }


    inner class StylingHistory(private var saved: Styling) {

        private val history = mutableListOf(saved)
        private var currentIdx = 0

        val current: Styling
            get() = history[currentIdx]

        private var lastEditedId: Any? = null
        private var lastEditedMillis = 0L

        init {
            projectFrame.onStylingChange(isUnsaved = false)
            projectFrame.panel.onStylingChange(isUnsaved = false, isUndoable = false, isRedoable = false)
            stylingDialog.panel.setStyling(saved)
        }

        fun editedAndRedraw(new: Styling, editedId: Any?) {
            if (new != current) {
                // If the user edits the styling after having undoed some steps, those steps are now dropped.
                while (history.lastIndex != currentIdx)
                    history.removeAt(history.lastIndex)
                // If the user edits the same setting multiple times in quick succession, do not memorize a new
                // state for each edit, but instead overwrite the last state after each edit. This for example avoids
                // a new state being created for each increment of a spinner.
                val currMillis = System.currentTimeMillis()
                val rapidSucc = editedId != null && editedId == lastEditedId && currMillis - lastEditedMillis < 1000
                lastEditedId = editedId
                lastEditedMillis = currMillis
                if (rapidSucc) {
                    // Normally, if the user edits the same widget multiple times in rapid succession, we skip over
                    // the following case and overwrite the history's last state (the previous version of the rapid
                    // succession edit) by the current state. However, if the user round-tripped back to the state
                    // where he started out at the beginning of his rapid succession edits, we remove the rapid
                    // succession state entirely and terminate the rapid succession edit. We then overwrite the state
                    // from before the rapid succession edits by the current state; we do this despite them being
                    // equivalent to ensure that the current state is always reference-equivalent to the objects stored
                    // in the UI's styling tree.
                    if (history.size >= 2 && history[currentIdx - 1] == new) {
                        history.removeAt(currentIdx--)
                        lastEditedId = null
                    }
                    history[currentIdx] = new
                } else {
                    history.add(new)
                    currentIdx++
                }
                onStylingChange()
            }
        }

        fun undoAndRedraw() {
            if (currentIdx != 0) {
                currentIdx--
                lastEditedId = null
                onStylingChange()
                stylingDialog.panel.setStyling(current)
            }
        }

        fun redoAndRedraw() {
            if (currentIdx != history.lastIndex) {
                currentIdx++
                lastEditedId = null
                onStylingChange()
                stylingDialog.panel.setStyling(current)
            }
        }

        fun resetAndRedraw() {
            if (saved != current) {
                editedAndRedraw(saved, null)
                stylingDialog.panel.setStyling(saved)
            }
        }

        fun save() {
            writeStyling(stylingFile, stylingCtx, current)
            saved = current
            lastEditedId = null  // Saving should always create a new undo state.
            projectFrame.panel.onStylingSave()
        }

        private fun onStylingChange() {
            val isUnsaved = !current.equalsIgnoreStyleOrderAndIneffectiveSettings(stylingCtx, saved)
            projectFrame.onStylingChange(isUnsaved)
            projectFrame.panel.onStylingChange(
                isUnsaved,
                isUndoable = currentIdx != 0,
                isRedoable = currentIdx != history.lastIndex
            )
            tryReadCreditsAndRedraw()
        }

    }

}
