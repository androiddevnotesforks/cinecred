package com.loadingbyte.cinecred.imaging

import com.loadingbyte.cinecred.common.Resolution
import com.loadingbyte.cinecred.common.ceilDiv
import com.loadingbyte.cinecred.common.roundingDiv
import com.loadingbyte.cinecred.common.roundingDivLog2
import com.loadingbyte.cinecred.imaging.Bitmap.Content.PROGRESSIVE_FRAME
import com.loadingbyte.cinecred.imaging.Bitmap.Scan.PROGRESSIVE
import org.bytedeco.ffmpeg.global.avutil.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong


class BitmapCompositor(
    private val canvasRepresentation: Bitmap.Representation,
    private val overlaySpec: Bitmap.Spec,
    private val composedOverlayResolution: Resolution
) {

    private val opaqueEnv: Environment
    private val alphaEnv: Environment

    init {
        require(!canvasRepresentation.isAlphaPremultiplied)

        // Determine a high quality intermediate representation that can be used for compositing if required.
        val canvasPixFmt = canvasRepresentation.pixelFormat
        val intermediatePixFmtCode =
            if (canvasPixFmt.isRGB)
                if (canvasPixFmt.hasAlpha) AV_PIX_FMT_GBRAP16 else AV_PIX_FMT_GBRP16
            else
                if (canvasPixFmt.hasAlpha) AV_PIX_FMT_YUVA444P16 else AV_PIX_FMT_YUV444P16
        val intermediateRepr = canvasRepresentation.copy(
            pixelFormat = Bitmap.PixelFormat.of(intermediatePixFmtCode), chromaLocation = AVCHROMA_LOC_UNSPECIFIED
        )

        // Compositing needs to happen in a pixel format without chroma subsampling.
        opaqueEnv =
            if (!canvasPixFmt.hasChromaSub)
                DirectEnvironment()
            else
                IntermediateEnvironment(intermediateRepr)
        // Alpha compositing additionally needs to happen in a color space with a linear transfer characteristic.
        alphaEnv =
            if (!canvasPixFmt.hasChromaSub && canvasRepresentation.transferCharacteristic == AVCOL_TRC_LINEAR)
                DirectEnvironment()
            else
                IntermediateEnvironment(intermediateRepr.copy(transferCharacteristic = AVCOL_TRC_LINEAR))
    }

    /**
     * When the [canvas] uses chroma subsampling, it is assumed that its top left corner is the start of a
     * "subsampling cell". This property is not checked at runtime.
     */
    fun compose(canvas: Bitmap, overlay: Bitmap, x: Int, y: Int, yStep: Int, overlayYTrim: Int, alpha: Double) {
        require(canvas.spec.representation == canvasRepresentation)
        require(overlay.spec == overlaySpec)
        require(yStep >= 1)
        require(overlayYTrim >= 0)

        if (alpha <= 0.0)
            return

        val (cw, ch) = canvas.spec.resolution
        val (ow, oh) = composedOverlayResolution
        val cViewX = max(0, x)
        val cViewY = if (y >= 0) y else y.mod(yStep)
        val oViewX = max(0, -x)
        val oViewY = -y + cViewY + overlayYTrim
        val viewW = min(ow - oViewX, cw - cViewX)
        val viewH = min(oh - oViewY, ch - cViewY)
        if (viewW <= 0 || viewH <= 0)
            return

        // Determine which environment to use.
        val env = if (alpha >= 1.0 && !overlaySpec.representation.pixelFormat.hasAlpha) opaqueEnv else alphaEnv

        // Get a bitmap filled with the content of the canvas at the location where the overlay should go.
        // We will perform compositing on this canvas view bitmap.
        val canvasView = env.extractCanvasView(canvas, cViewX, cViewY, viewW, viewH, yStep)

        // Convert the overlay into the same representation as the canvas view bitmap...
        val (convertedOverlay, alphaMatrix) = env.convertOverlay(overlay)
        // ... and crop it as needed.
        val convertedOverlayView = convertedOverlay.view(oViewX, oViewY, viewW, viewH, yStep)
        alphaMatrix.matrix?.let { alphaMatrix.matrix = arrayView(it, ow, oViewX, oViewY, viewW, viewH, yStep) }

        // Composite.
        val alphaNumerators = prepareAlphaNumerators(alpha.coerceIn(0.0, 1.0), alphaMatrix)
        if (alphaNumerators == null)
            canvasView.blit(convertedOverlayView, 0, 0, viewW, canvasView.spec.resolution.heightPx, 0, 0, 1)
        else if (!canvasRepresentation.pixelFormat.hasAlpha)
            blendOntoOpaqueCanvas(canvasView, convertedOverlayView, alphaNumerators)
        else
            blendOntoTranslucentCanvas(canvasView, convertedOverlayView, alphaNumerators)

        // If necessary, write the changes made to the canvas view bitmap back to the canvas.
        env.flushCanvasView()

        canvasView.close()
        convertedOverlayView.close()
    }

    private fun arrayView(array: LongArray, stride: Int, x: Int, y: Int, w: Int, h: Int, yStep: Int): LongArray {
        if (x == 0 && y == 0 && w == stride && h * stride == array.size && yStep == 1)
            return array
        val view = LongArray(w * h)
        for (i in 0..<ceilDiv(h, yStep))
            System.arraycopy(array, (y + i * yStep) * stride + x, view, i * w, w)
        return view
    }

    private fun prepareAlphaNumerators(alpha: Double, alphaMatrix: Bitmap2BitmapConverter.AlphaMatrix): LongArray? {
        val externalAlphaNum = (alpha * ALPHA_DENOMINATOR).roundToLong()
        return alphaMatrix.matrix?.also { m ->
            val depth = alphaMatrix.depth
            val shift = ALPHA_DENOMINATOR_LOG2 - depth
            require(shift >= 0) { "Alpha depths larger than $ALPHA_DENOMINATOR_LOG2 bit are not supported." }
            if (alpha == 1.0)
                for (i in m.indices)
                    m[i] = widen(m[i], depth) shl shift
            else
                for (i in m.indices)
                    m[i] = roundingDivLog2((widen(m[i], depth) shl shift) * externalAlphaNum, ALPHA_DENOMINATOR_LOG2)
        }
            ?: if (alpha != 1.0) longArrayOf(externalAlphaNum) else null
    }

    /** Widens the range "0 to (2^depth - 1)" to the range "0 to 2^depth". */
    private fun widen(alpha: Long, depth: Int): Long = alpha + (alpha shr (depth - 1))
    /** Narrows the range "0 to 2^depth" to the range "0 to (2^depth - 1)". */
    private fun narrow(alpha: Long, depth: Int): Long = alpha - (alpha shr depth) - ((alpha shr (depth - 1)) and 0x1)

    /*
     * Implementation note: The alpha blending formulas for non-alpha components (i.e., color, chroma, and luma)
     * work even when the components are offset from 0, which happens when the frame uses limited range.
     * Intuitively, this makes sense as those formulas are just computing a linear combination. As such, we don't
     * need to specially handle the limited range case in color, chroma, and luma blending.
     *
     * Also, the alpha component always uses full range, even when the other components use limited range. As such,
     * we also don't need to specially handle the limited range case in the computation of the resulting alpha.
     */

    private fun blendOntoOpaqueCanvas(canvas: Bitmap, overlay: Bitmap, alphaNumerators: LongArray) {
        val inc = if (alphaNumerators.size == 1) 0 else 1
        // Compute the new color/chroma/luma components using this formula:
        //     v_out = v_overlay * alpha_overlay + v_canvas * (1 - alpha_overlay)
        for (component in canvas.spec.representation.pixelFormat.components) {
            var i = 0
            canvas.mergeComponent(overlay, component) { vc, vo ->
                val alphaNum = alphaNumerators[i]; i += inc
                roundingDivLog2(vo * alphaNum + vc * (ALPHA_DENOMINATOR - alphaNum), ALPHA_DENOMINATOR_LOG2)
            }
        }
    }

    private fun blendOntoTranslucentCanvas(canvas: Bitmap, overlay: Bitmap, alphaNumerators: LongArray) {
        val inc = if (alphaNumerators.size == 1) 0 else 1
        val (w, h) = canvas.spec.resolution
        val pixelFormat = canvas.spec.representation.pixelFormat
        val alphaComp = pixelFormat.components.last()
        val alphaDepth = alphaComp.depth
        val alphaShift = ALPHA_DENOMINATOR_LOG2 - alphaDepth
        require(alphaShift >= 0) { "Alpha depths larger than $ALPHA_DENOMINATOR_LOG2 bit are not supported." }

        // First compute the new alpha component using this formular:
        //     alpha_out = alpha_overlay + alpha_canvas - alpha_overlay * alpha_canvas
        // Remember convenient representations of both alpha_canvas and alpha_out for later use.
        val canvasAlphaNumerators = LongArray(w * h)
        val outCanvasAlphaNumNums = LongArray(w * h)
        var i = 0
        var j = 0
        canvas.modifyComponent(alphaComp) { ac ->
            val overlayAlphaNum = alphaNumerators[i]; i += inc
            val canvasAlphaNum = widen(ac, alphaDepth) shl alphaShift
            // In contrast to the other numbers, which we store as numerators of the fraction X / ALPHA_DENOM, we store
            // this number as the numerator of the fraction X / ALPHA_DENOM^2, which allows us to defer division and
            // later do it in one single step.
            val outCanvasAlphaNumNum = ((overlayAlphaNum + canvasAlphaNum) shl ALPHA_DENOMINATOR_LOG2) -
                    overlayAlphaNum * canvasAlphaNum
            canvasAlphaNumerators[j] = canvasAlphaNum
            outCanvasAlphaNumNums[j++] = outCanvasAlphaNumNum
            narrow(roundingDivLog2(outCanvasAlphaNumNum, ALPHA_DENOMINATOR_LOG2 + alphaShift), alphaDepth)
        }

        // Then compute the new color/chroma/luma components using this formula:
        //     v_out = (v_overlay * alpha_overlay + v_canvas * alpha_canvas - v_canvas * alpha_canvas * alpha_overlay)
        //                 / alpha_out
        for (component in pixelFormat.components.let { c -> c.subList(0, c.size - 1) }) {
            var k = 0
            var l = 0
            canvas.mergeComponent(overlay, component) { vc, vo ->
                val overlayAlphaNum = alphaNumerators[k]; k += inc
                val canvasAlphaNum = canvasAlphaNumerators[l]
                val outCanvasAlphaNumNum = outCanvasAlphaNumNums[l++]
                // In this division, both the dividend and the divisor are numerators of the fraction X / ALPHA_DENOM^2,
                // so the denominators cancel out, which is why they don't appear in the computation.
                roundingDiv(
                    ((vo * overlayAlphaNum + vc * canvasAlphaNum) shl ALPHA_DENOMINATOR_LOG2) -
                            vc * canvasAlphaNum * overlayAlphaNum,
                    // We protect against divisions by 0 by making the divisor extremely large if it's 0.
                    // As a result, the color/chroma/luma components of pixels with 0 alpha also become 0.
                    outCanvasAlphaNumNum or ((outCanvasAlphaNumNum - 1) and (1L shl 62))
                )
            }
        }
    }


    companion object {
        private const val ALPHA_DENOMINATOR_LOG2 = 16
        private const val ALPHA_DENOMINATOR = 1 shl ALPHA_DENOMINATOR_LOG2
    }


    private interface Environment {
        fun convertOverlay(overlay: Bitmap): Pair<Bitmap, Bitmap2BitmapConverter.AlphaMatrix>
        fun extractCanvasView(canvas: Bitmap, x: Int, y: Int, w: Int, h: Int, yStep: Int): Bitmap
        fun flushCanvasView()
    }


    private abstract inner class AbstractEnvironment(workRepresentation: Bitmap.Representation) : Environment {

        // Allocate a bitmap and a converter for converting the overlay to the work representation.
        private val convertedOverlay = Bitmap.allocate(
            Bitmap.Spec(composedOverlayResolution, workRepresentation, overlaySpec.scan, overlaySpec.content)
        )
        private val overlayConverter = Bitmap2BitmapConverter(overlaySpec, convertedOverlay.spec)

        override fun convertOverlay(overlay: Bitmap): Pair<Bitmap, Bitmap2BitmapConverter.AlphaMatrix> {
            val alphaMatrix = Bitmap2BitmapConverter.AlphaMatrix()
            overlayConverter.convert(overlay, convertedOverlay, alphaMatrix)
            return Pair(convertedOverlay, alphaMatrix)
        }

    }


    private inner class DirectEnvironment : AbstractEnvironment(canvasRepresentation) {

        override fun extractCanvasView(canvas: Bitmap, x: Int, y: Int, w: Int, h: Int, yStep: Int) =
            canvas.view(x, y, w, h, yStep)

        override fun flushCanvasView() {}

    }


    private inner class IntermediateEnvironment(workRepresentation: Bitmap.Representation) :
        AbstractEnvironment(workRepresentation) {

        private val xGrid: Int
        private val yGrid: Int

        private val slice: Bitmap
        private val intermediate: Bitmap
        private val slice2intermediateConverter: Bitmap2BitmapConverter
        private val intermediate2sliceConverter: Bitmap2BitmapConverter

        private var canvas: Bitmap? = null
        private var sliceX = 0
        private var sliceY = 0
        private var sliceW = 0
        private var sliceH = 0

        init {
            val canvasPixFmt = canvasRepresentation.pixelFormat
            xGrid = 1 shl canvasPixFmt.hChromaSub
            yGrid = 1 shl canvasPixFmt.vChromaSub
            // Add enough room to compensate for offsets between the overlay position and the chroma subsampling grid.
            val workRes = Resolution(
                ceilToPowerOf2((xGrid - 1) + composedOverlayResolution.widthPx, xGrid),
                ceilToPowerOf2((yGrid - 1) + composedOverlayResolution.heightPx, yGrid),
            )
            // As we're using an intermediate representation for compositing, we need to allocate bitmaps and converters
            // for converting slices of the canvas to the intermediate format, and later converting them back.
            slice = Bitmap.allocate(Bitmap.Spec(workRes, canvasRepresentation, PROGRESSIVE, PROGRESSIVE_FRAME))
            intermediate = Bitmap.allocate(Bitmap.Spec(workRes, workRepresentation, PROGRESSIVE, PROGRESSIVE_FRAME))
            slice2intermediateConverter = Bitmap2BitmapConverter(slice.spec, intermediate.spec)
            intermediate2sliceConverter = Bitmap2BitmapConverter(intermediate.spec, slice.spec)
        }

        override fun extractCanvasView(canvas: Bitmap, x: Int, y: Int, w: Int, h: Int, yStep: Int): Bitmap {
            this.canvas = canvas
            val sliceOffsetX = x % xGrid
            val sliceOffsetY = y % yGrid
            sliceX = x - sliceOffsetX
            sliceY = y - sliceOffsetY
            sliceW = ceilToPowerOf2(sliceOffsetX + w, xGrid)
            sliceH = ceilToPowerOf2(sliceOffsetY + h, yGrid)
            slice.blit(canvas, sliceX, sliceY, sliceW, sliceH, 0, 0, 1)
            slice2intermediateConverter.convert(slice, intermediate)
            return intermediate.view(sliceOffsetX, sliceOffsetY, w, h, yStep)
        }

        override fun flushCanvasView() {
            intermediate2sliceConverter.convert(intermediate, slice)
            canvas!!.blit(slice, 0, 0, sliceW, sliceH, sliceX, sliceY, 1)
            canvas = null
        }

        private fun ceilToPowerOf2(value: Int, powerOf2: Int): Int =
            if (value and (powerOf2 - 1) == 0) value else (value or (powerOf2 - 1)) + 1

    }

}
