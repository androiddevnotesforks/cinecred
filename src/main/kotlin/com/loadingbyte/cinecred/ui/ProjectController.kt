package com.loadingbyte.cinecred.ui

import com.loadingbyte.cinecred.common.Picture
import com.loadingbyte.cinecred.common.l10n
import com.loadingbyte.cinecred.drawer.draw
import com.loadingbyte.cinecred.project.Project
import com.loadingbyte.cinecred.project.Styling
import com.loadingbyte.cinecred.projectio.*
import com.loadingbyte.cinecred.ui.helper.FontFamilies
import com.loadingbyte.cinecred.ui.helper.JobSlot
import com.loadingbyte.cinecred.ui.styling.EditStylingDialog
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.apache.commons.csv.CSVRecord
import java.awt.Font
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.WatchEvent
import java.util.*
import javax.swing.JOptionPane
import javax.swing.SwingUtilities


class ProjectController(val projectDir: Path) {

    val projectName: String = projectDir.fileName.toString()

    val stylingHistory: StylingHistory

    val projectFrame = ProjectFrame(this)
    val editStylingDialog = EditStylingDialog(this)

    private val stylingFile = OpenController.getStylingFile(projectDir)
    private val creditsFile = OpenController.getCreditsFile(projectDir)

    private var creditsCsv: List<CSVRecord>? = null
    private val fonts = mutableMapOf<Path, Font>()
    private val pictureLoaders = mutableMapOf<Path, Lazy<Picture?>>()

    private val readCreditsAndRedrawJobSlot = JobSlot()

    private var isEditTabActive = true
    private var isEditStylingDialogVisible = true

    init {
        projectFrame.isVisible = true
        editStylingDialog.isVisible = true

        // Load the initial credits CSV from disk.
        tryReloadCreditsFile()

        // Load the initially present auxiliary files (project fonts and pictures).
        for (projectFile in Files.walk(projectDir))
            tryReloadAuxFile(projectFile)

        // Load the initial state of the styling from disk.
        stylingHistory = StylingHistory(readStyling(stylingFile))

        // Read and draw the credits.
        readCreditsAndRedraw()

        // Watch for future changes in the new project dir.
        RecursiveFileWatcher.watch(projectDir) { file: Path, kind: WatchEvent.Kind<*> ->
            val isCreditsFile = try {
                Files.isSameFile(file, creditsFile)
            } catch (_: IOException) {
                false
            }
            when {
                isCreditsFile ->
                    SwingUtilities.invokeLater { tryReloadCreditsFile(); readCreditsAndRedraw() }
                kind == ENTRY_DELETE ->
                    SwingUtilities.invokeLater { if (tryRemoveAuxFile(file)) readCreditsAndRedraw() }
                else ->
                    SwingUtilities.invokeLater { if (tryReloadAuxFile(file)) readCreditsAndRedraw() }
            }
        }
    }

    private fun tryReloadAuxFile(file: Path): Boolean {
        // If the file has been generated by a render job, don't reload the project. Otherwise, generating image
        // sequences would be very expensive because we would constantly reload the project. Note that we do not
        // only consider the current render job, but all render jobs in the render job list. This ensures that even
        // the last file generated by a render job doesn't reload the project even when the render job has already
        // been marked as complete by the time the OS notifies us about the newly generated file.
        if (projectFrame.panel.deliverPanel.renderQueuePanel.renderJobs.any { it.generatesFile(file) })
            return false

        tryReadFont(file)?.let { font ->
            fonts[file] = font
            editStylingDialog.panel.updateProjectFontFamilies(FontFamilies(fonts.values))
            return true
        }
        tryReadPictureLoader(file)?.let { pictureLoader ->
            pictureLoaders[file] = pictureLoader
            return true
        }
        return false
    }

    private fun tryRemoveAuxFile(file: Path): Boolean {
        if (fonts.remove(file) != null) {
            editStylingDialog.panel.updateProjectFontFamilies(FontFamilies(fonts.values))
            return true
        }
        if (pictureLoaders.remove(file) != null)
            return true
        return false
    }

    private fun tryReloadCreditsFile() {
        if (!Files.exists(creditsFile)) {
            JOptionPane.showMessageDialog(
                projectFrame, l10n("ui.edit.missingCreditsFile.msg", creditsFile),
                l10n("ui.edit.missingCreditsFile.title"), JOptionPane.ERROR_MESSAGE
            )
            tryCloseProject()
            return
        }

        creditsCsv = loadCreditsFile(creditsFile)
    }

    private fun readCreditsAndRedraw() {
        // Capture these variables in the state they are in when the function is called.
        val styling = stylingHistory.current
        val creditsCsv = this.creditsCsv!!

        // Execute the reading and drawing in another thread to not block the UI thread.
        readCreditsAndRedrawJobSlot.submit {
            // We only now build these maps because it is expensive to build them and we don't want to do it
            // each time the function is called, but only when the issued reload & redraw actually gets through
            // (which is quite a lot less because the function is often called multiple times in rapid succession).
            val fontsByName = fonts.mapKeys { (_, font) -> font.getFontName(Locale.ROOT) }
            val pictureLoadersByRelPath = pictureLoaders.mapKeys { (path, _) -> projectDir.relativize(path) }

            val (log, pages) = readCredits(creditsCsv, styling, pictureLoadersByRelPath)

            val project = Project(styling, fontsByName.toImmutableMap(), (pages ?: emptyList()).toImmutableList())
            val drawnPages = when (pages) {
                null -> emptyList()
                else -> draw(project)
            }

            // Make sure to update the UI from the UI thread because Swing is not thread-safe.
            SwingUtilities.invokeLater {
                projectFrame.panel.editPanel.updateProjectAndLog(project, drawnPages, log)
                projectFrame.panel.videoPanel.updateProject(project, drawnPages)
                projectFrame.panel.deliverPanel.configurationForm.updateProject(project, drawnPages)
            }
        }
    }

    fun tryCloseProject(force: Boolean = false): Boolean {
        if (force) {
            projectFrame.panel.editPanel.onTryCloseProject()
            projectFrame.panel.deliverPanel.renderQueuePanel.onTryCloseProject()
        } else if (
            !projectFrame.panel.editPanel.onTryCloseProject() ||
            !projectFrame.panel.deliverPanel.renderQueuePanel.onTryCloseProject()
        )
            return false

        OpenController.onCloseProject(this)

        projectFrame.dispose()
        editStylingDialog.dispose()
        // Cancel the previous project dir change watching order.
        RecursiveFileWatcher.unwatch(projectDir)

        return true
    }

    fun onChangeTab(changedToEdit: Boolean) {
        isEditTabActive = changedToEdit
        editStylingDialog.isVisible = changedToEdit && isEditStylingDialogVisible
    }

    fun setEditStylingDialogVisible(isVisible: Boolean) {
        isEditStylingDialogVisible = isVisible
        editStylingDialog.isVisible = isEditTabActive && isVisible
        projectFrame.panel.editPanel.onSetEditStylingDialogVisible(isVisible)
    }


    inner class StylingHistory(private var saved: Styling) {

        private val history = mutableListOf(saved)
        private var currentIdx = 0

        val current: Styling
            get() = history[currentIdx]

        init {
            projectFrame.panel.editPanel.onStylingChange(isUnsaved = false, isUndoable = false, isRedoable = false)
            editStylingDialog.panel.setStyling(saved)
        }

        fun editedAndRedraw(new: Styling) {
            if (new != current) {
                while (history.lastIndex != currentIdx)
                    history.removeAt(history.lastIndex)
                history.add(new)
                currentIdx++
                onStylingChange()
            }
        }

        fun undoAndRedraw() {
            if (currentIdx != 0) {
                currentIdx--
                onStylingChange()
                editStylingDialog.panel.setStyling(current)
            }
        }

        fun redoAndRedraw() {
            if (currentIdx != history.lastIndex) {
                currentIdx++
                onStylingChange()
                editStylingDialog.panel.setStyling(current)
            }
        }

        fun resetAndRedraw() {
            if (saved != current) {
                editedAndRedraw(saved)
                editStylingDialog.panel.setStyling(saved)
            }
        }

        fun save() {
            writeStyling(stylingFile, current)
            saved = current
            projectFrame.panel.editPanel.onStylingSave()
        }

        private fun onStylingChange() {
            projectFrame.panel.editPanel.onStylingChange(
                isUnsaved = current != saved,
                isUndoable = currentIdx != 0,
                isRedoable = currentIdx != history.lastIndex
            )
            readCreditsAndRedraw()
        }

    }

}
