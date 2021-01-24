package com.loadingbyte.cinecred.common

import com.formdev.flatlaf.util.Graphics2DProxy
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawer
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextForcedDrawer
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.*
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import kotlin.math.max


class DeferredImage {

    var width = 0f; private set
    var height = 0f; private set

    private val instructions = mutableListOf<Instruction>()

    fun setMinWidth(minWidth: Float) {
        width = max(width, minWidth)
    }

    fun setMinHeight(minHeight: Float) {
        height = max(height, minHeight)
    }

    fun drawDeferredImage(image: DeferredImage, x: Float, y: Float, scaling: Float = 1f) {
        width = max(width, scaling * image.width)
        height = max(height, scaling * image.height)

        for (insn in image.instructions)
            when (insn) {
                is Instruction.DrawShape ->
                    drawShape(
                        insn.color, insn.shape, x + scaling * insn.x, y + scaling * insn.y, scaling * insn.scaling,
                        insn.fill, insn.isGuide
                    )
                is Instruction.DrawString ->
                    drawString(
                        insn.font, insn.str, x + scaling * insn.x, y + scaling * insn.y,
                        scaling * insn.justificationWidth, scaling * insn.scaling, insn.isGuide
                    )
                is Instruction.DrawPicture ->
                    drawPicture(
                        insn.pic.scaled(scaling), x + scaling * insn.x, y + scaling * insn.y, insn.isGuide
                    )
            }
    }

    fun drawShape(
        color: Color, shape: Shape, x: Float = 0f, y: Float = 0f, scaling: Float = 1f, fill: Boolean = false,
        isGuide: Boolean = false
    ) {
        val bounds = shape.bounds2D
        width = max(width, x + scaling * bounds.maxX.toFloat())
        height = max(height, y + scaling * bounds.maxY.toFloat())
        instructions.add(Instruction.DrawShape(color, shape, x, y, scaling, fill, isGuide))
    }

    fun drawLine(
        color: Color, x1: Float, y1: Float, x2: Float, y2: Float, fill: Boolean = false, isGuide: Boolean = false
    ) {
        drawShape(color, Line2D.Float(x1, y1, x2, y2), fill = fill, isGuide = isGuide)
    }

    fun drawRect(
        color: Color, x: Float, y: Float, width: Float, height: Float, fill: Boolean = false, isGuide: Boolean = false
    ) {
        drawShape(color, Rectangle2D.Float(x, y, width, height), fill = fill, isGuide = isGuide)
    }

    /**
     * The [y] coordinate used by this method differs from the one used by Graphics2D.
     * Here, the coordinate doesn't point to the baseline, but to the topmost part of the font.
     * If [justificationWidth] is provided, the string is fully justified to fit that exact width.
     */
    fun drawString(
        font: RichFont, str: String, x: Float, y: Float, justificationWidth: Float = Float.NaN, scaling: Float = 1f,
        isGuide: Boolean = false
    ) {
        val unscaledWidth = if (!justificationWidth.isNaN()) justificationWidth else font.awt.getStringWidth(str)
        width = max(width, x + scaling * unscaledWidth)
        height = max(height, y + scaling * font.spec.heightPx)
        instructions.add(Instruction.DrawString(font, str, x, y, justificationWidth, scaling, isGuide))
    }

    fun drawPicture(pic: Picture, x: Float, y: Float, isGuide: Boolean = false) {
        width = max(width, x + pic.width)
        height = max(height, y + pic.height)
        instructions.add(Instruction.DrawPicture(pic, x, y, isGuide))
    }


    private sealed class Instruction(val isGuide: Boolean) {

        class DrawShape(
            val color: Color, val shape: Shape, val x: Float, val y: Float, val scaling: Float, val fill: Boolean,
            isGuide: Boolean
        ) : Instruction(isGuide)

        class DrawString(
            val font: RichFont, val str: String, val x: Float, val y: Float, val justificationWidth: Float,
            val scaling: Float, isGuide: Boolean
        ) : Instruction(isGuide)

        class DrawPicture(
            val pic: Picture, val x: Float, val y: Float, isGuide: Boolean
        ) : Instruction(isGuide)

    }


    /**
     * The [guideStrokeWidth] is only relevant for guides drawn with [drawShape] or its derivative methods.
     */
    fun materialize(g2: Graphics2D, drawGuides: Boolean = false, guideStrokeWidth: Float = 1f) {
        for (insn in instructions) {
            if (!drawGuides && insn.isGuide)
                continue

            when (insn) {
                is Instruction.DrawShape -> @Suppress("NAME_SHADOWING") g2.withNewG2 { g2 ->
                    g2.color = insn.color
                    if (insn.isGuide)
                        g2.stroke = BasicStroke(guideStrokeWidth)
                    g2.translate(insn.x.toDouble(), insn.y.toDouble())
                    g2.scale(insn.scaling.toDouble(), insn.scaling.toDouble())
                    if (insn.fill)
                        g2.fill(insn.shape)
                    else
                        g2.draw(insn.shape)
                }
                is Instruction.DrawString -> {
                    val scaledFontSize = insn.font.awt.size2D * insn.scaling
                    val scaledFont = insn.font.awt.deriveFont(scaledFontSize)
                    var scaledTextLayout = TextLayout(insn.str, scaledFont, g2.fontRenderContext)
                    // Fully justify the text layout if requested.
                    if (!insn.justificationWidth.isNaN())
                        scaledTextLayout = scaledTextLayout.getJustifiedLayout(insn.justificationWidth)
                    val baselineY = insn.y + scaledTextLayout.ascent + scaledTextLayout.leading / 2f

                    @Suppress("NAME_SHADOWING")
                    g2.withNewG2 { g2 ->
                        g2.color = insn.font.spec.color
                        g2.translate(insn.x.toDouble(), baselineY.toDouble())
                        // We render the text by first converting the string to a path via the TextLayout and then
                        // filling that path. This has the following vital advantages:
                        //   - Native text rendering via TextLayout.draw(), which internally eventually calls
                        //     Graphics2D.drawGlyphVector(), typically ensures that each glyph is aligned at pixel
                        //     boundaries. To achieve this, glyphs are slightly shifted to the left or right. This
                        //     leads to inconsistent glyph spacing, which is acceptable for desktop purposes in
                        //     exchange for higher readability, but not acceptable in a movie context. By converting
                        //     the text layout to a path and then filling that path, we avoid calling the native text
                        //     renderer and instead call the regular vector graphics renderer, which renders the glyphs
                        //     at the exact positions where the text layouter has put them, without applying the
                        //     counterproductive glyph shifting.
                        //   - Vector-based means of imaging like SVG exactly match the raster-based means.
                        // For these advantages, we put up with the following disadvantages:
                        //   - Rendering this way is slower than natively rendering text via TextLayout.draw().
                        //   - Since the glyphs are no longer aligned at pixel boundaries, heavier antialiasing kicks
                        //     in, leading to the rendered text sometimes appearing more blurry. However, this is an
                        //     inherent disadvantage of rendering text with perfect glyph spacing and is typically
                        //     acceptable in a movie context.
                        g2.fill(scaledTextLayout.getOutline(null))
                    }

                    // When drawing to a PDF, additionally draw some invisible strings where the visible, vectorized
                    // strings already lie. Even though this is nowhere near accurate, it enables text copying in PDFs.
                    if (g2 is PdfBoxGraphics2D) {
                        // Force the following text to be drawn using any font it can find.
                        g2.setFontTextDrawer(PdfBoxGraphics2DFontTextForcedDrawer())
                        // We use a placeholder default PDF font. We make it slightly smaller than the visible font
                        // to make sure that the invisible text completely fits into the bounding box of the visible
                        // text, even when the visible font is a narrow one. This way, spaces between words are
                        // correctly recognized by the PDF reader.
                        g2.font = Font("SansSerif", 0, (scaledFontSize * 0.75f).toInt())
                        // The invisible text should of course be invisible.
                        g2.color = Color(0, 0, 0, 0)
                        // Draw each word separately at the position of the vectorized version of the word.
                        // This way, inaccuracies concerning font family, weight, kerning, etc. don't hurt too much.
                        var charIdx = 0
                        for (word in insn.str.split(' ')) {
                            // Estimate the x coordinate where the word starts from the word's first glyph's bounds.
                            val xOffset = scaledTextLayout.getBlackBoxBounds(charIdx, charIdx + 1).bounds2D.x.toFloat()
                            // Note: Append a space to all words because without it, some PDF viewers give text
                            // without any spaces when the user tries to copy it. Note that we also append a space
                            // to the last word of the sentence to make sure that when the user tries to copy multiple
                            // sentences, e.g., multiple lines of text, there are at least spaces between the lines.
                            g2.drawString("$word ", insn.x + xOffset, baselineY)
                            charIdx += word.length + 1
                        }
                        // We are done. Future text should again be vectorized, as indicated by
                        // the presence of the default, unconfigured FontTextDrawer.
                        g2.setFontTextDrawer(PdfBoxGraphics2DFontTextDrawer())
                    }
                }
                is Instruction.DrawPicture -> when (val pic = insn.pic) {
                    is Picture.Raster -> {
                        val tx = AffineTransform()
                        tx.translate(insn.x.toDouble(), insn.y.toDouble())
                        tx.scale(pic.scaling.toDouble(), pic.scaling.toDouble())
                        g2.drawImage(pic.img, tx, null)
                    }
                    is Picture.SVG -> @Suppress("NAME_SHADOWING") g2.withNewG2 { g2 ->
                        g2.translate(insn.x.toDouble(), insn.y.toDouble())
                        g2.scale(pic.scaling.toDouble(), pic.scaling.toDouble())
                        // Batik might not be thread-safe, even though we haven't tested that.
                        synchronized(pic.gvtRoot) {
                            if (pic.isCropped)
                                g2.translate(-pic.gvtRoot.bounds.x, -pic.gvtRoot.bounds.y)
                            pic.gvtRoot.paint(g2)
                        }
                    }
                    is Picture.PDF -> @Suppress("NAME_SHADOWING") g2.withNewG2 { g2 ->
                        g2.translate(insn.x.toDouble(), insn.y.toDouble())
                        if (pic.isCropped)
                            g2.translate(-pic.minBox.x * pic.scaling, -pic.minBox.y * pic.scaling)
                        // PDFBox calls clearRect() before starting to draw. This sometimes results in a black
                        // box even if g2.background is set to a transparent color. The most thorough fix is
                        // to just block all calls to clearRect().
                        val g2Proxy = object : Graphics2DProxy(g2) {
                            override fun clearRect(x: Int, y: Int, width: Int, height: Int) {
                                // Block call.
                            }
                        }
                        // PDFBox is definitely not thread-safe.
                        synchronized(pic.doc) {
                            PDFRenderer(pic.doc).renderPageToGraphics(0, g2Proxy, pic.scaling)
                        }
                    }
                }
            }
        }
    }

}
