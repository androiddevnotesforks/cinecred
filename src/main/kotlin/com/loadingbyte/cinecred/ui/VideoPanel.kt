package com.loadingbyte.cinecred.ui

import com.formdev.flatlaf.FlatClientProperties.*
import com.formdev.flatlaf.util.SystemInfo
import com.formdev.flatlaf.util.UIScale
import com.loadingbyte.cinecred.common.*
import com.loadingbyte.cinecred.imaging.DeferredImage.Companion.DELIVERED_LAYERS
import com.loadingbyte.cinecred.imaging.DeferredVideo
import com.loadingbyte.cinecred.project.DrawnProject
import com.loadingbyte.cinecred.ui.helper.*
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.*
import java.awt.image.BufferedImage
import javax.swing.*
import kotlin.math.*


class VideoPanel(private val ctrl: ProjectController) : JPanel() {

    // ========== ENCAPSULATION LEAKS ==========
    @Deprecated("ENCAPSULATION LEAK") val leakedPlayButton get() = playButton
    @Deprecated("ENCAPSULATION LEAK") val leakedFrameSlider get() = frameSlider
    // =========================================

    private val keyListeners = mutableListOf<KeyListener>()

    private val canvas: JPanel = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val video = scaledVideo
            val videoBackend = scaledVideoBackend
            if (video != null && videoBackend != null) {
                val (videoWidth, videoHeight) = video.resolution
                g as Graphics2D
                g.translate((width - videoWidth / systemScaling) / 2.0, (height - videoHeight / systemScaling) / 2.0)
                g.scale(1.0 / systemScaling)
                g.color = grounding
                g.fillRect(0, 0, videoWidth, videoHeight)
                g.clipRect(0, 0, videoWidth, videoHeight)
                videoBackend.materializeFrame(g, frameSlider.value)
            }
        }
    }

    private val actualSizeButton = newToolbarToggleButtonWithKeyListener(
        X_1_TO_1_ICON, l10n("ui.video.actualSize"),
        VK_1, CTRL_DOWN_MASK
    ) { restartDrawing() }
    // Do not add the full-screen button on macOS, as full screen is supported though native window buttons there.
    // Additionally, our shortcut F11 is already occupied by "show desktop", and when using Java's full-screen API,
    // the dock (presumably) blocks mouse click events in the lower part of the window, where our buttons are.
    private val fullScreenButton = if (SystemInfo.isMacOS) null else newToolbarToggleButtonWithKeyListener(
        SCREEN_ICON, l10n("ui.video.fullScreen"),
        VK_F11, 0
    ) { isSelected ->
        graphicsConfiguration.device.fullScreenWindow = if (isSelected) ctrl.videoDialog else null
    }

    private val rewindButton: JToggleButton
    private val pauseButton: JToggleButton
    private val playButton: JToggleButton

    private val frameSlider = JSlider().also { frameSlider ->
        frameSlider.value = 0
        frameSlider.isFocusable = false
        frameSlider.addChangeListener {
            // When the slider is moved, either automatically or by hand, draw the selected frame.
            // Use paintImmediately() because repaint() might postpone the painting, which we do not want.
            canvas.paintImmediately(0, 0, canvas.width, canvas.height)
            // Also adjust the timecode label.
            adjustTimecodeLabel()
        }
    }

    private val timecodeLabel = JLabel().apply {
        putClientProperty(STYLE_CLASS, "monospaced")
    }

    private var drawnProject: DrawnProject? = null

    private val makeVideoBackendJobSlot = JobSlot()
    private var scaledVideo: DeferredVideo? = null
    private var scaledVideoBackend: DeferredVideo.Graphics2DBackend? = null
    private var grounding = Color.BLACK
    private var systemScaling = 1.0

    private var playTimer: Timer? = null
    private var playRate = 0
        set(value) {
            val project = drawnProject?.project
            val playRate = if (project == null) 0 else value
            if (field == playRate)
                return
            field = playRate
            when {
                playRate < 0 -> rewindButton.isSelected = true
                playRate == 0 -> pauseButton.isSelected = true
                playRate > 0 -> playButton.isSelected = true
            }
            playTimer?.stop()
            playTimer = if (playRate == 0) null else {
                val frameSpacing /* ms */ = 1000.0 / (abs(playRate) * project!!.styling.global.fps.frac)
                // We presume that we cannot draw faster than roughly 80 frames per second, and hence increase the step
                // size once that framerate is exceeded.
                val stepSize = ceil(12.0 / frameSpacing).roundToInt()
                Timer((frameSpacing * stepSize).roundToInt()) {
                    if (!ctrl.projectFrame.isVisible) {
                        // If the main frame has been disposed, also stop the timer, because would otherwise keep
                        // generating action events, which keep AWT's EDT thread from stopping and in turn keep the
                        // whole application from closing.
                        playTimer?.stop()
                    } else {
                        frameSlider.value += playRate.sign * stepSize
                        if (frameSlider.value.let { it == 0 || it == frameSlider.maximum })
                            this.playRate = 0
                    }
                }.apply { initialDelay = 0; start() }
            }
        }

    private fun newToolbarToggleButtonWithKeyListener(
        icon: Icon,
        tooltip: String,
        shortcutKeyCode: Int,
        shortcutModifiers: Int,
        listener: (Boolean) -> Unit
    ): JToggleButton {
        val btn = newToolbarToggleButton(icon, tooltip, shortcutKeyCode, shortcutModifiers, false, listener)
        keyListeners.add(KeyListener(shortcutKeyCode, shortcutModifiers) { btn.isSelected = !btn.isSelected })
        return btn
    }

    init {
        val rewindIcon = PLAY_ICON.getScaledIcon(-1.0, 1.0)
        val buttonGroup = ButtonGroup()
        fun newToolbarPlayButton(icon: Icon, ttip: String, kc: Int, listener: () -> Unit, isSelected: Boolean = false) =
            newToolbarToggleButton(icon, ttip, kc, 0, isSelected).also { btn ->
                buttonGroup.add(btn)
                // Register an ActionListener (and not an ItemListener) to prevent feedback loops.
                btn.addActionListener { listener() }
            }
        rewindButton = newToolbarPlayButton(rewindIcon, l10n("ui.video.rewind"), VK_J, ::rewind)
        pauseButton = newToolbarPlayButton(PAUSE_ICON, l10n("ui.video.pause"), VK_K, ::pause, isSelected = true)
        playButton = newToolbarPlayButton(PLAY_ICON, l10n("ui.video.play"), VK_L, ::play)

        layout = MigLayout(
            "insets 0",
            "[]8[]" + (if (fullScreenButton != null) "0[]" else "") + "rel[]rel[]2[]2[][][]14[]",
            "[]0[]8[]8"
        )
        add(canvas, "span, grow, push")
        add(JSeparator(), "newline, span, growx, shrink 0 0")
        add(actualSizeButton, "newline, skip 1")
        fullScreenButton?.let(::add)
        add(JSeparator(JSeparator.VERTICAL), "growy, shrink 0 0")
        add(rewindButton)
        add(pauseButton)
        add(playButton)
        // If we use % or growx instead of "sp", the slider becomes laggy due to the quickly changing timecode label.
        add(frameSlider, "width 100sp")
        add(timecodeLabel)

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                restartDrawing()
            }
        })
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (keyListeners.any { it.onKeyEvent(event) })
            return true

        if (event.id != KEY_PRESSED)
            return false

        val project = drawnProject?.project
        when (event.modifiersEx) {
            0 -> when (event.keyCode) {
                VK_ESCAPE -> fullScreenButton?.isSelected = false
                VK_J -> rewind()
                VK_K -> pause()
                VK_L -> play()
                VK_SPACE -> playRate = if (playRate == 0) 1 else 0
                VK_LEFT, VK_KP_LEFT -> frameSlider.value--
                VK_RIGHT, VK_KP_RIGHT -> frameSlider.value++
                VK_HOME -> frameSlider.value = 0
                VK_END -> frameSlider.value = frameSlider.maximum
                else -> return false
            }
            SHIFT_DOWN_MASK -> when (event.keyCode) {
                VK_LEFT, VK_KP_LEFT -> project?.let { frameSlider.value -= it.styling.global.fps.frac.roundToInt() }
                VK_RIGHT, VK_KP_RIGHT -> project?.let { frameSlider.value += it.styling.global.fps.frac.roundToInt() }
                else -> return false
            }
            else -> return false
        }
        return true
    }

    // @formatter:off
    private fun rewind() { if (playRate > 0) playRate = -1 else playRate-- }
    private fun pause() { playRate = 0 }
    private fun play() { if (playRate < 0) playRate = 1 else playRate++ }
    // @formatter:on

    fun onHide() {
        playRate = 0
    }

    fun updateProject(drawnProject: DrawnProject?) {
        this.drawnProject = drawnProject

        playRate = 0
        if (drawnProject == null) {
            scaledVideo = null
            scaledVideoBackend = null
            canvas.repaint()
            timecodeLabel.text = "\u2014 / \u2014"
        } else {
            restartDrawing()
            adjustTimecodeLabel()
        }
    }

    private fun restartDrawing() {
        val (project, _, video) = drawnProject ?: return

        // Abort if the canvas has never been shown on the screen yet, which would have it in a pre-initialized state
        // that this method can't cope with. As soon as it is shown for the first time, the resize listener will be
        // notified and call this method again.
        val graphics = canvas.graphics as Graphics2D? ?: return

        systemScaling = UIScale.getSystemScaleFactor(graphics)
        val scaling = if (actualSizeButton.isSelected) 1.0 else
            systemScaling * min(
                canvas.width.toDouble() / project.styling.global.resolution.widthPx,
                canvas.height.toDouble() / project.styling.global.resolution.heightPx
            )
        // Protect against too small canvas sizes.
        if (scaling < 0.001)
            return

        // Adjust the frame slider's range, then capture its current position.
        frameSlider.maximum = video.numFrames - 1
        val currentFrameIdx = frameSlider.value

        makeVideoBackendJobSlot.submit {
            val scaledVideo = video.copy(resolutionScaling = scaling)
            val scaledVideoBackend = object : DeferredVideo.Graphics2DBackend(
                scaledVideo, DELIVERED_LAYERS, draft = true, preloading = true
            ) {
                override fun createIntermediateImage(width: Int, height: Int) =
                    // If the canvas was disposed in the meantime, create a dummy image to avoid crashing.
                    canvas.graphicsConfiguration.createCompatibleImage(width, height, Transparency.TRANSLUCENT)
                        ?: BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
            }
            // Simulate materializing the currently selected frame in the background thread. As expensive operations
            // are cached, the subsequent materialization of that frame in the EDT thread will be very fast.
            scaledVideoBackend.preloadFrame(currentFrameIdx)
            SwingUtilities.invokeLater {
                this.scaledVideo = scaledVideo
                this.scaledVideoBackend = scaledVideoBackend
                this.grounding = project.styling.global.grounding
                canvas.repaint()
            }
        }
    }

    private fun adjustTimecodeLabel() {
        val (project, _, video) = drawnProject ?: return
        val fps = project.styling.global.fps
        val timecodeFormat = project.styling.global.timecodeFormat
        val curTc = formatTimecode(fps, timecodeFormat, frameSlider.value)
        val runtimeTc = formatTimecode(fps, timecodeFormat, video.numFrames)
        timecodeLabel.text = "$curTc / $runtimeTc"
    }

}
