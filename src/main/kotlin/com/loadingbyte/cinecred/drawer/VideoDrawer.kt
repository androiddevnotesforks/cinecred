package com.loadingbyte.cinecred.drawer

import com.loadingbyte.cinecred.common.DeferredImage.Companion.BACKGROUND
import com.loadingbyte.cinecred.common.DeferredImage.Companion.FOREGROUND
import com.loadingbyte.cinecred.common.setHighQuality
import com.loadingbyte.cinecred.common.withG2
import com.loadingbyte.cinecred.project.DrawnPage
import com.loadingbyte.cinecred.project.DrawnStageInfo
import com.loadingbyte.cinecred.project.Project
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt


open class VideoDrawer(
    private val project: Project,
    drawnPages: List<DrawnPage>,
    private val scaling: Float = 1f,
    private val transparentGrounding: Boolean = false,
    private val previewMode: Boolean = false
) {

    /* ************************************
       ********** INITIALIZATION **********
       ************************************ */

    // Note: A pageIdx of -1 indicates an empty frame.
    private class Insn(val pageIdx: Int, val imgTopY: Float, val alpha: Float)

    private val insns = ArrayList<Insn>()

    init {
        // Write frames for each page as has been configured.
        for ((pageIdx, page) in project.pages.withIndex()) {
            for ((stageIdx, stage) in page.stages.withIndex())
                when (val stageInfo = drawnPages[pageIdx].stageInfo[stageIdx]) {
                    is DrawnStageInfo.Card -> {
                        val imgTopY = scaling * (stageInfo.middleY.resolve() - project.styling.global.heightPx / 2f)
                        if (stageIdx == 0)
                            writeFade(pageIdx, imgTopY, stage.style.cardFadeInFrames, fadeOut = false)
                        writeStatic(pageIdx, imgTopY, stage.style.cardDurationFrames)
                        if (stageIdx == page.stages.lastIndex)
                            writeFade(pageIdx, imgTopY, stage.style.cardFadeOutFrames, fadeOut = true)
                    }
                    is DrawnStageInfo.Scroll -> {
                        val imgTopYStart =
                            scaling * (stageInfo.scrollStartY.resolve() - project.styling.global.heightPx / 2f)
                        writeScroll(
                            pageIdx, imgTopYStart, stageInfo.frames, stageInfo.initialAdvance,
                            scrollPxPerFrame = scaling * stage.style.scrollPxPerFrame
                        )
                    }
                }

            if (pageIdx != project.pages.lastIndex)
                writeStatic(-1, 0f, page.stages.last().style.afterwardSlugFrames)
        }
    }

    private fun writeStatic(pageIdx: Int, imgTopY: Float, numFrames: Int) {
        for (frame in 0 until numFrames)
            insns.add(Insn(pageIdx, imgTopY, 1f))
    }

    private fun writeFade(pageIdx: Int, imgTopY: Float, numFrames: Int, fadeOut: Boolean) {
        for (frame in 0 until numFrames) {
            // Choose alpha such that the fade sequence doesn't contain a fully empty or fully opaque frame.
            var alpha = (frame + 1).toFloat() / (numFrames + 1)
            if (fadeOut)
                alpha = 1f - alpha
            insns.add(Insn(pageIdx, imgTopY, alpha))
        }
    }

    private fun writeScroll(
        pageIdx: Int, imgTopYStart: Float, numFrames: Int, initialAdvance: Float, scrollPxPerFrame: Float
    ) {
        for (frame in 0 until numFrames) {
            val imgTopY = imgTopYStart + (frame + initialAdvance) * scrollPxPerFrame
            insns.add(Insn(pageIdx, imgTopY, 1f))
        }
    }

    val numFrames = insns.size

    val width = (scaling * project.styling.global.widthPx).roundToInt()
    val height = (scaling * project.styling.global.heightPx).roundToInt()


    /* ************************************
       ********** RASTER DRAWING **********
       ************************************ */

    private val scaledPageDefImages = drawnPages.map { it.defImage.copy(universeScaling = scaling) }

    private val groundingImage: BufferedImage
    private val shiftedPageImages = Array(drawnPages.size) { HashMap<Float, BufferedImage>() }

    init {
        groundingImage = createIntermediateImage(width, height).withG2 { g2 ->
            g2.color = project.styling.global.grounding
            g2.fillRect(0, 0, width, height)
        }

        // In preview mode, only 0-shifted rasterized page images are used. We already precompute these now
        // to avoid wait times later when pages must be drawn in real-time.
        if (previewMode)
            for (pageIdx in 0 until project.pages.size)
                getShiftedPageImage(pageIdx, 0f)
    }

    private fun getShiftedPageImage(pageIdx: Int, shift: Float): BufferedImage {
        val cache = shiftedPageImages[pageIdx]
        // It is important to not use computeIfAbsent() here, as our manual modification of the cache inside the lambda
        // would then throw a concurrent modification exception.
        return cache.getOrPut(shift) {
            // If the cache is full, remove one cached image (doesn't matter which one we remove).
            if (cache.size >= MAX_SHIFTED_PAGE_IMAGES)
                cache.iterator().run { next(); remove() }

            require(shift >= 0f && shift < 1f)
            val scaledPageDefImg = scaledPageDefImages[pageIdx]
            // Note: We add 1 to the height to make room for the shift (which is between 0 and 1).
            // We add 2*height to make room for buffers above and below the content; they will be in frame
            // when a scrolling page starts and ends and need to provide the correct grounding color.
            val imageHeight = ceil(scaledPageDefImg.height.resolve()).toInt() + 1 + 2 * height
            createIntermediateImage(width, imageHeight).withG2 { g2 ->
                g2.setHighQuality()
                if (!transparentGrounding) {
                    // If the final image should not have an alpha channel, the intermediate images, which also don't
                    // have alpha, have to have the proper grounding, as otherwise their grounding would be black.
                    // Note that we can't simply use the deferred image's grounding layer because that doesn't extend
                    // to the whole height of the raster image, since the raster image is higher than the deferred
                    // image to make room for the shift and the start/end buffers.
                    g2.color = project.styling.global.grounding
                    g2.fillRect(0, 0, width, imageHeight)
                }
                g2.translate(0.0, shift.toDouble() + height)
                scaledPageDefImg.materialize(g2, layers = listOf(BACKGROUND, FOREGROUND))
            }
        }
    }

    /**
     * This method must be overwritten by users of this class who exactly know onto what kind of
     * graphics object they will later draw their frames. The returned image should support alpha if and only if
     * [transparentGrounding] is true.
     * Even though this method is not abstract, it throws an [UnsupportedOperationException]. Not having it abstract
     * makes it easier to create a video drawer just for the purpose of computing the overall number of frames.
     */
    protected open fun createIntermediateImage(width: Int, height: Int): BufferedImage {
        throw UnsupportedOperationException()
    }

    fun drawFrame(g2: Graphics2D, frameIdx: Int) {
        require(frameIdx in 0..numFrames) { "Frame #$frameIdx exceeds number of available frames ($numFrames)." }

        val insn = insns[frameIdx]

        // Note: pageIdx == -1 means that the frame should be empty.
        if (insn.pageIdx == -1) {
            if (!transparentGrounding)
                g2.drawImage(groundingImage, 0, 0, null)
        } else {
            if (!transparentGrounding && insn.alpha != 1f) {
                g2.drawImage(groundingImage, 0, 0, null)
                g2.composite = AlphaComposite.SrcOver.derive(insn.alpha)
            }

            val shift = -insn.imgTopY
            if (!previewMode) {
                val img = getShiftedPageImage(insn.pageIdx, shift - floor(shift))
                g2.drawImage(img, 0, floor(shift).toInt() - height, null)
            } else {
                val img = getShiftedPageImage(insn.pageIdx, 0f)
                val tx = AffineTransform.getTranslateInstance(0.0, shift.toDouble() - height)
                g2.drawImage(img, tx, null)
            }
        }
    }


    companion object {
        private const val MAX_SHIFTED_PAGE_IMAGES = 16
    }

}
