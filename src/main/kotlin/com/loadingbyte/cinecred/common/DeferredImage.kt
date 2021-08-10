package com.loadingbyte.cinecred.common

import com.formdev.flatlaf.util.Graphics2DProxy
import com.loadingbyte.cinecred.common.Y.Companion.toY
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawer
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextForcedDrawer
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.text.AttributedCharacterIterator
import java.text.AttributedCharacterIterator.Attribute
import java.text.CharacterIterator
import kotlin.math.max


class DeferredImage(var width: Float = 0f, var height: Y = 0f.toY()) {

    private val instructions = HashMap<Layer, MutableList<Instruction>>()

    private fun addInstruction(layer: Layer, insn: Instruction) {
        instructions.getOrPut(layer) { ArrayList() }.add(insn)
    }

    fun copy(universeScaling: Float = 1f, elasticScaling: Float = 1f): DeferredImage {
        val copy = DeferredImage(
            width = width * universeScaling,
            height = (height * universeScaling).scaleElastic(elasticScaling)
        )
        copy.drawDeferredImage(this, universeScaling = universeScaling, elasticScaling = elasticScaling)
        return copy
    }

    fun drawDeferredImage(
        image: DeferredImage, x: Float = 0f, y: Y = 0f.toY(), universeScaling: Float = 1f, elasticScaling: Float = 1f
    ) {
        for (layer in image.instructions.keys) {
            val insn = Instruction.DrawDeferredImageLayer(image, layer, x, y, universeScaling, elasticScaling)
            addInstruction(layer, insn)
        }
    }

    fun drawShape(
        color: Color, shape: Shape, x: Float, y: Y, fill: Boolean = false, layer: Layer = FOREGROUND
    ) {
        addInstruction(layer, Instruction.DrawShapes(listOf(Pair(shape, color)), x, y, fill))
    }

    fun drawLine(
        color: Color, x1: Float, y1: Y, x2: Float, y2: Y, fill: Boolean = false, layer: Layer = FOREGROUND
    ) {
        addInstruction(layer, Instruction.DrawLine(color, x1, y1, x2, y2, fill))
    }

    fun drawRect(
        color: Color, x: Float, y: Y, width: Float, height: Y, fill: Boolean = false,
        layer: Layer = FOREGROUND
    ) {
        addInstruction(layer, Instruction.DrawRect(color, x, y, width, height, fill))
    }

    /**
     * The [y] coordinate used by this method differs from the one used by Graphics2D.
     * Here, the coordinate doesn't point to the baseline, but to the topmost part of the largest font.
     * If [justificationWidth] is provided, the string is fully justified to fit that exact width.
     *
     * @throws IllegalArgumentException If [attrCharIter] is not equipped with both an [TextAttribute.FONT] and a
     *     [TextAttribute.FOREGROUND] at every character.
     */
    fun drawString(
        attrCharIter: AttributedCharacterIterator, x: Float, y: Y, justificationWidth: Float = Float.NaN,
        layer: Layer = FOREGROUND
    ) {
        // Find the distance between y and the baseline of the tallest font in the attributed string.
        // In case there are multiple tallest fonts, take the largest distance.
        var maxFontHeight = 0f
        var aboveBaseline = 0f
        attrCharIter.forEachRunOf(TextAttribute.FONT) { runEndIdx ->
            val font = attrCharIter.getAttribute(TextAttribute.FONT) as Font? ?: throw IllegalArgumentException()
            val normFontLM = font.deriveFont(FONT_NORMALIZATION_ATTRS)
                .getLineMetrics(attrCharIter, attrCharIter.index, runEndIdx, REF_FRC)
            if (normFontLM.height >= maxFontHeight) {
                maxFontHeight = normFontLM.height
                aboveBaseline = max(aboveBaseline, normFontLM.ascent + normFontLM.leading / 2f)
            }
        }
        // The drawing instruction requires a y coordinate that points to the baseline of the string, so
        // compute that now.
        val baselineY = y + aboveBaseline

        // Layout the text.
        var textLayout = TextLayout(attrCharIter, REF_FRC)
        // Fully justify the text layout if requested.
        if (!justificationWidth.isNaN())
            textLayout = textLayout.getJustifiedLayout(justificationWidth)

        // We render the text by first converting the string to a path via the TextLayout and then
        // later filling that path. This has the following vital advantages:
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
        val fill = mutableListOf<Pair<Shape, Color>>()
        // Start with the background fillings for each run with non-null background color.
        attrCharIter.forEachRunOf(TextAttribute.BACKGROUND) { runEndIdx ->
            val bg = attrCharIter.getAttribute(TextAttribute.BACKGROUND) as Color?
            if (bg != null) {
                val highlightShape = textLayout.getLogicalHighlightShape(attrCharIter.index, runEndIdx)
                fill.add(Pair(highlightShape, bg))
            }
        }
        // Then lay the foreground outline fillings for each run of different foreground color on top of that.
        attrCharIter.forEachRunOf(TextAttribute.FOREGROUND) { runEndIdx ->
            val fg = attrCharIter.getAttribute(TextAttribute.FOREGROUND) as Color? ?: throw IllegalArgumentException()
            val outline = textLayout.getOutline(attrCharIter.index, runEndIdx)
            fill.add(Pair(outline, fg))
        }

        // Finally, add a drawing instruction using all prepared information.
        addInstruction(layer, Instruction.DrawShapes(fill, x, baselineY, fill = true))

        // When drawing to a PDF, we additionally want to draw some invisible strings at the places where the visible,
        // vectorized strings already lie. Even though this is nowhere near accurate, it enables text copying in PDFs.
        // We now collect information in order to create an instruction for this purpose.
        val invisParts = mutableListOf<Instruction.DrawInvisiblePDFStrings.Part>()
        // Extract the string from the character iterator.
        val str = attrCharIter.getString()
        // Handle each run of a different font separately.
        attrCharIter.forEachRunOf(TextAttribute.FONT) { runEndIdx ->
            val font = attrCharIter.getAttribute(TextAttribute.FONT) as Font
            // We make the invisible placeholder default PDF font slightly smaller than the visible font
            // to make sure that the invisible text completely fits into the bounding box of the visible
            // text, even when the visible font is a narrow one. This way, spaces between words are
            // correctly recognized by the PDF reader.
            val invisFontSize = font.size2D * 0.75f
            // We want to draw each word separately at the position of the vectorized version of the word.
            // This way, inaccuracies concerning font family, weight, kerning, etc. don't hurt too much.
            var charIdx = 0
            for (word in str.substring(attrCharIter.index, runEndIdx).split(' '))
                if (word.isNotBlank()) {
                    // Estimate the relative x coordinate where the word starts from the word's first glyph's bounds.
                    val xOffset = textLayout.getBlackBoxBounds(charIdx, charIdx + 1).bounds2D.x.toFloat()
                    // Note: Append a space to all words because without it, some PDF viewers yield text
                    // without any spaces when the user tries to copy it. Note that we also append a space
                    // to the last word of the sentence to make sure that when the user tries to copy multiple
                    // sentences, e.g., multiple lines of text, there are at least spaces between the lines.
                    invisParts.add(Instruction.DrawInvisiblePDFStrings.Part("$word ", xOffset, invisFontSize))
                    charIdx += word.length + 1
                }
        }
        // Finally, add a drawing instruction for the invisible PDF strings.
        addInstruction(layer, Instruction.DrawInvisiblePDFStrings(invisParts, x, baselineY))
    }

    fun drawPicture(pic: Picture, x: Float, y: Y, layer: Layer = FOREGROUND) {
        addInstruction(layer, Instruction.DrawPicture(pic, x, y))
    }

    fun materialize(g2: Graphics2D, layers: List<Layer>) {
        materializeDeferredImage(g2, 0f, 0f, 1f, 1f, this, layers)
    }

    private fun materializeDeferredImage(
        g2: Graphics2D, x: Float, y: Float, universeScaling: Float, elasticScaling: Float,
        image: DeferredImage, layers: List<Layer>
    ) {
        for (layer in layers)
            for (insn in image.instructions.getOrDefault(layer, emptyList()))
                materializeInstruction(g2, x, y, universeScaling, elasticScaling, insn)
    }

    private fun materializeInstruction(
        g2: Graphics2D, x: Float, y: Float, universeScaling: Float, elasticScaling: Float,
        insn: Instruction
    ) {
        when (insn) {
            is Instruction.DrawDeferredImageLayer -> materializeDeferredImage(
                g2, x + universeScaling * insn.x, y + universeScaling * insn.y.resolve(elasticScaling),
                universeScaling * insn.universeScaling, elasticScaling * insn.elasticScaling, insn.image,
                listOf(insn.layer)
            )
            is Instruction.DrawShapes -> materializeShapes(
                g2, x + universeScaling * insn.x, y + universeScaling * insn.y.resolve(elasticScaling), universeScaling,
                insn.shapes, insn.fill
            )
            is Instruction.DrawLine -> materializeShape(
                g2, Line2D.Float(
                    x + universeScaling * insn.x1, y + universeScaling * insn.y1.resolve(elasticScaling),
                    x + universeScaling * insn.x2, y + universeScaling * insn.y2.resolve(elasticScaling)
                ), insn.color, insn.fill
            )
            is Instruction.DrawRect -> materializeShape(
                g2, Rectangle2D.Float(
                    x + universeScaling * insn.x, y + universeScaling * insn.y.resolve(elasticScaling),
                    universeScaling * insn.width, universeScaling * insn.height.resolve(elasticScaling)
                ), insn.color, insn.fill
            )
            is Instruction.DrawPicture -> materializePicture(
                g2, x + universeScaling * insn.x, y + universeScaling * insn.y.resolve(elasticScaling),
                insn.pic.scaled(universeScaling)
            )
            is Instruction.DrawInvisiblePDFStrings -> materializeInvisiblePDFStrings(
                g2, x + universeScaling * insn.x, y + universeScaling * insn.baselineY.resolve(elasticScaling),
                universeScaling, insn.parts
            )
        }
    }

    private fun materializeShapes(
        g2: Graphics2D, x: Float, y: Float, universeScaling: Float,
        shapes: List<Pair<Shape, Color>>, fill: Boolean
    ) {
        // We first transform the shapes and then draw them without scaling the graphics object.
        // This ensures that the shapes will exhibit the graphics object's stroke width,
        // which is 1 pixel by default.
        val tx = AffineTransform()
        tx.translate(x.toDouble(), y.toDouble())
        tx.scale(universeScaling.toDouble(), universeScaling.toDouble())
        for ((shape, color) in shapes) {
            val transformedShape = tx.createTransformedShape(shape)
            materializeShape(g2, transformedShape, color, fill)
        }
    }

    private fun materializeShape(
        g2: Graphics2D,
        shape: Shape, color: Color, fill: Boolean
    ) {
        g2.color = color
        if (fill)
            g2.fill(shape)
        else
            g2.draw(shape)
    }

    private fun materializePicture(
        g2: Graphics2D, x: Float, y: Float,
        pic: Picture
    ) {
        when (pic) {
            is Picture.Raster -> {
                val tx = AffineTransform()
                tx.translate(x.toDouble(), y.toDouble())
                tx.scale(pic.scaling.toDouble(), pic.scaling.toDouble())
                g2.drawImage(pic.img, tx, null)
            }
            is Picture.SVG -> g2.preserveTransform {
                g2.translate(x.toDouble(), y.toDouble())
                g2.scale(pic.scaling.toDouble(), pic.scaling.toDouble())
                // Batik might not be thread-safe, even though we haven't tested that.
                synchronized(pic.gvtRoot) {
                    if (pic.isCropped)
                        g2.translate(-pic.gvtRoot.bounds.x, -pic.gvtRoot.bounds.y)
                    pic.gvtRoot.paint(g2)
                }
            }
            // Note: We have to create a new Graphics2D object here because PDFBox modifies it heavily
            // and sometimes even makes it totally unusable.
            is Picture.PDF -> @Suppress("NAME_SHADOWING") g2.withNewG2 { g2 ->
                g2.translate(x.toDouble(), y.toDouble())
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

    private fun materializeInvisiblePDFStrings(
        g2: Graphics2D, x: Float, baselineY: Float, universeScaling: Float,
        parts: List<Instruction.DrawInvisiblePDFStrings.Part>
    ) {
        if (g2 is PdfBoxGraphics2D) {
            // Force the following text to be drawn using any font it can find.
            g2.setFontTextDrawer(PdfBoxGraphics2DFontTextForcedDrawer())
            // The invisible text should of course be invisible.
            g2.color = Color(0, 0, 0, 0)
            for (part in parts) {
                // We use a placeholder default PDF font.
                g2.font = Font("SansSerif", 0, (universeScaling * part.fontSize).toInt())
                g2.drawString(part.str, x + universeScaling * part.unscaledXOffset, baselineY)
            }
            // We are done. Future text should again be vectorized, as indicated by
            // the presence of the default, unconfigured FontTextDrawer.
            g2.setFontTextDrawer(PdfBoxGraphics2DFontTextDrawer())
        }
    }


    companion object {

        // These three common layers are typically used. Additional layers may be defined by users of this class.
        val FOREGROUND = Layer()
        val BACKGROUND = Layer()
        val GUIDES = Layer()

        private val FONT_NORMALIZATION_ATTRS = mapOf(TextAttribute.SUPERSCRIPT to null)

        private fun CharacterIterator.getString(): String {
            val result = StringBuilder(endIndex - beginIndex)
            index = beginIndex
            var c = first()
            while (c != AttributedCharacterIterator.DONE) {
                result.append(c)
                c = next()
            }
            return result.toString()
        }

        private inline fun AttributedCharacterIterator.forEachRunOf(attr: Attribute, block: (Int) -> Unit) {
            index = beginIndex
            while (index != endIndex) {
                val runEndIdx = getRunLimit(attr)
                block(runEndIdx)
                index = runEndIdx
            }
        }

    }


    class Layer


    private sealed class Instruction {

        class DrawDeferredImageLayer(
            val image: DeferredImage, val layer: Layer, val x: Float, val y: Y, val universeScaling: Float,
            val elasticScaling: Float
        ) : Instruction()

        class DrawShapes(
            val shapes: List<Pair<Shape, Color>>, val x: Float, val y: Y, val fill: Boolean
        ) : Instruction()

        class DrawLine(
            val color: Color, val x1: Float, val y1: Y, val x2: Float, val y2: Y, val fill: Boolean
        ) : Instruction()

        class DrawRect(
            val color: Color, val x: Float, val y: Y, val width: Float, val height: Y, val fill: Boolean
        ) : Instruction()

        class DrawPicture(
            val pic: Picture, val x: Float, val y: Y
        ) : Instruction()

        class DrawInvisiblePDFStrings(
            val parts: List<Part>, val x: Float, val baselineY: Y
        ) : Instruction() {
            class Part(val str: String, val unscaledXOffset: Float, val fontSize: Float)
        }

    }

}
