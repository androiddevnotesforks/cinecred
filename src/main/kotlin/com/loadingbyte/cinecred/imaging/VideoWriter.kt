package com.loadingbyte.cinecred.imaging

import com.loadingbyte.cinecred.common.FPS
import com.loadingbyte.cinecred.common.Resolution
import com.loadingbyte.cinecred.common.VERSION
import com.loadingbyte.cinecred.common.l10n
import com.loadingbyte.cinecred.natives.zimg.zimg_graph_builder_params
import com.loadingbyte.cinecred.natives.zimg.zimg_h.*
import com.loadingbyte.cinecred.natives.zimg.zimg_image_buffer
import com.loadingbyte.cinecred.natives.zimg.zimg_image_buffer_const
import com.loadingbyte.cinecred.natives.zimg.zimg_image_format
import jdk.incubator.foreign.CLinker
import jdk.incubator.foreign.MemoryAddress
import jdk.incubator.foreign.MemoryAddress.NULL
import jdk.incubator.foreign.MemoryLayout.PathElement.groupElement
import jdk.incubator.foreign.MemoryLayout.PathElement.sequenceElement
import jdk.incubator.foreign.MemoryLayouts.JAVA_LONG
import jdk.incubator.foreign.MemorySegment
import jdk.incubator.foreign.ResourceScope
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVCodecContext.FF_COMPLIANCE_STRICT
import org.bytedeco.ffmpeg.avcodec.AVCodecContext.FF_PROFILE_H264_CONSTRAINED_BASELINE
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVPixFmtDescriptor
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.ffmpeg.global.swscale.*
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.Pointer
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.Closeable
import java.lang.invoke.VarHandle
import java.nio.file.Path
import kotlin.io.path.pathString


class MuxerFormat(val name: String, val supportedCodecIds: Set<Int>, val extensions: List<String>) {
    companion object {
        val ALL: List<MuxerFormat>

        init {
            val codecIds = collect { av_codec_iterate(it)?.id() }
            val avMuxerFormats = collect { av_muxer_iterate(it) }

            ALL = avMuxerFormats.map { avMuxerFormat ->
                MuxerFormat(
                    name = avMuxerFormat.name().string,
                    supportedCodecIds = codecIds.filter { codecId ->
                        avformat_query_codec(avMuxerFormat, codecId, FF_COMPLIANCE_STRICT) == 1
                    }.toSet(),
                    extensions = avMuxerFormat.extensions()?.string?.split(',').orEmpty()
                )
            }
        }

        private inline fun <R> collect(iterate: (Pointer) -> R?): List<R> {
            val list = mutableListOf<R>()
            val iter = BytePointer()
            while (true)
                list.add(iterate(iter) ?: return list)
        }
    }
}


/**
 * This class provides a nice interface to FFmpeg and hides all its nastiness.
 * The code inside this class is adapted from here: https://ffmpeg.org/doxygen/trunk/muxing_8c-example.html
 */
class VideoWriter(
    fileOrDir: Path,
    resolution: Resolution,
    fps: FPS,
    codecId: Int,
    outPixelFormat: Int,
    outRange: Range,
    outTransferCharacteristic: TransferCharacteristic,
    outYCbCrCoefficients: YCbCrCoefficients,
    muxerOptions: Map<String, String>,
    codecOptions: Map<String, String>
) : Closeable {

    enum class Range { FULL, LIMITED }
    enum class TransferCharacteristic { SRGB, BT709 }
    enum class YCbCrCoefficients { BT601, BT709 }

    private val pipeProcs = mutableListOf<FrameProcessor>()
    private var pipe: FramePipeline? = null

    private var oc: AVFormatContext? = null
    private var st: AVStream? = null
    private var enc: AVCodecContext? = null

    // Pts of the next frame that will be generated.
    private var frameCounter = 0L

    init {
        callSetup(::release) {
            setup(
                fileOrDir, resolution, fps, codecId,
                outPixelFormat, outRange, outTransferCharacteristic, outYCbCrCoefficients,
                muxerOptions, codecOptions
            )
        }
    }

    private fun setup(
        fileOrDir: Path,
        resolution: Resolution,
        fps: FPS,
        codecId: Int,
        outPixelFormat: Int,
        outRange: Range,
        outTransferCharacteristic: TransferCharacteristic,
        outYCbCrCoefficients: YCbCrCoefficients,
        muxerOptions: Map<String, String>,
        codecOptions: Map<String, String>
    ) {
        // Build a pipeline that converts BufferedImages into the desired format.
        configurePipeline(resolution, outPixelFormat, outRange, outTransferCharacteristic, outYCbCrCoefficients)

        // Allocate the output media context.
        val oc = AVFormatContext(null)
        this.oc = oc
        avformat_alloc_output_context2(oc, null, null, fileOrDir.pathString)
            .throwIfErrnum("delivery.ffmpeg.unknownMuxerError")
        // Will be freed by avformat_free_context().
        oc.metadata(AVDictionary().also { metaDict ->
            av_dict_set(metaDict, "encoding_tool", "Cinecred $VERSION", 0)
            av_dict_set(metaDict, "company_name", "Cinecred", 0)
            av_dict_set(metaDict, "product_name", "Cinecred $VERSION", 0)
        })

        // Find the encoder.
        val codec = avcodec_find_encoder(codecId)
            .throwIfNull("delivery.ffmpeg.unknownCodecError", avcodec_get_name(codecId).string)

        // Add the video stream.
        val st = avformat_new_stream(oc, null)
            .throwIfNull("delivery.ffmpeg.allocStreamError")
        this.st = st
        // Assigning the stream ID dynamically is technically unnecessary because we only have one stream.
        st.id(oc.nb_streams() - 1)

        // Allocate and configure the codec.
        val enc = avcodec_alloc_context3(codec)
            .throwIfNull("delivery.ffmpeg.allocEncoderError")
        this.enc = enc
        configureCodec(
            resolution, fps, codecId, outPixelFormat, outRange, outTransferCharacteristic, outYCbCrCoefficients
        )

        // Now that all the parameters are set, we can open the video codec and allocate the necessary encode buffer.
        withOptionsDict(codecOptions) { codecOptionsDict ->
            avcodec_open2(enc, codec, codecOptionsDict)
                .throwIfErrnum("delivery.ffmpeg.openEncoderError")
        }

        // Copy the stream parameters to the muxer.
        avcodec_parameters_from_context(st.codecpar(), enc).throwIfErrnum("delivery.ffmpeg.copyParamsError")

        withOptionsDict(muxerOptions) { muxerOptionsDict ->
            // Open the output file, if needed.
            if (oc.oformat().flags() and AVFMT_NOFILE == 0) {
                val pb = AVIOContext(null)
                avio_open2(pb, fileOrDir.pathString, AVIO_FLAG_WRITE, null, muxerOptionsDict)
                    .throwIfErrnum("delivery.ffmpeg.openFileError", fileOrDir)
                oc.pb(pb)
            }

            // Write the stream header, if any.
            avformat_write_header(oc, muxerOptionsDict).throwIfErrnum("delivery.ffmpeg.openFileError", fileOrDir)
        }
    }

    private fun configurePipeline(
        resolution: Resolution,
        outPixelFormat: Int,
        outRange: Range,
        outTransferCharacteristic: TransferCharacteristic,
        outYCbCrCoefficients: YCbCrCoefficients
    ) {
        val outPixFmtDesc = av_pix_fmt_desc_get(outPixelFormat).throwIfNull("delivery.ffmpeg.getPixFmtError")
        val hasAlpha = outPixFmtDesc.flags() and AV_PIX_FMT_FLAG_ALPHA.toLong() != 0L
        val outPlanar = outPixFmtDesc.flags() and AV_PIX_FMT_FLAG_PLANAR.toLong() != 0L
        val sameCS = outPixFmtDesc.flags() and AV_PIX_FMT_FLAG_RGB.toLong() != 0L &&
                outRange == Range.FULL && outTransferCharacteristic == TransferCharacteristic.SRGB
        val inPixelFormat = if (hasAlpha) AV_PIX_FMT_ABGR else AV_PIX_FMT_BGR24
        val interPixelFormat = if (hasAlpha) AV_PIX_FMT_GBRAP else AV_PIX_FMT_GBRP

        // Note: In principle, we could add another case for when the transfer characteristic remains sRGB, in which
        // case we could ditch the zimg processor. However, for consistency, we decided to always let zimg handle the
        // RGB-to-YCbCr conversion.
        // Note for future self: If we were to let swscale handle all conversion, we'd need this code:
        //     val swsCtx = sws_alloc_context()
        //     av_opt_set_int(swsCtx, "srcw", ..., 0)
        //     av_opt_set_int(swsCtx, "srch", ..., 0)
        //     av_opt_set_int(swsCtx, "src_format", ..., 0)
        //     av_opt_set_int(swsCtx, "dstw", ..., 0)
        //     av_opt_set_int(swsCtx, "dsth", ..., 0)
        //     av_opt_set_int(swsCtx, "dst_format", ..., 0)
        //     av_opt_set_int(swsCtx, "sws_flags", SWS_ACCURATE_RND.toLong(), 0)
        //     sws_setColorspaceDetails(
        //         swsCtx,
        //         sws_getCoefficients(SWS_CS_DEFAULT), 1,
        //         sws_getCoefficients(...), ...,
        //         0, 1 shl 16, 1 shl 16
        //     )
        //     sws_init_context(swsCtx, null, null)
        pipeProcs += SwsFrameProcessor(resolution, inPixelFormat, if (sameCS) outPixelFormat else interPixelFormat)
        if (!sameCS) {
            pipeProcs += ZimgFrameProcessor(
                resolution, interPixelFormat, if (outPlanar) outPixelFormat else interPixelFormat,
                outRange, outTransferCharacteristic, outYCbCrCoefficients
            )
            if (!outPlanar)
                pipeProcs += SwsFrameProcessor(resolution, interPixelFormat, outPixelFormat)
        }

        pipe = FramePipeline(resolution, pipeProcs)
    }

    private fun configureCodec(
        resolution: Resolution,
        fps: FPS,
        codecId: Int,
        outPixelFormat: Int,
        outRange: Range,
        outTransferCharacteristic: TransferCharacteristic,
        outYCbCrCoefficients: YCbCrCoefficients
    ) {
        val oc = this.oc!!
        val st = this.st!!
        val enc = this.enc!!

        enc.codec_id(codecId)
        enc.width(resolution.widthPx)
        enc.height(resolution.heightPx)
        enc.pix_fmt(outPixelFormat)

        // Specify color space metadata.
        val outPixFmtDesc = av_pix_fmt_desc_get(outPixelFormat).throwIfNull("delivery.ffmpeg.getPixFmtError")
        val outRGB = outPixFmtDesc.flags() and AV_PIX_FMT_FLAG_RGB.toLong() != 0L
        val outRangeFFmpeg = when (outRange) {
            Range.FULL -> AVCOL_RANGE_JPEG
            Range.LIMITED -> AVCOL_RANGE_MPEG
        }
        val outTRCFFmpeg = when (outTransferCharacteristic) {
            TransferCharacteristic.SRGB -> AVCOL_TRC_IEC61966_2_1
            TransferCharacteristic.BT709 -> AVCOL_TRC_BT709
        }
        val outYCbCrCoeffsFFmpeg = when (outYCbCrCoefficients) {
            YCbCrCoefficients.BT601 -> AVCOL_SPC_BT470BG
            YCbCrCoefficients.BT709 -> AVCOL_SPC_BT709
        }
        enc.color_primaries(AVCOL_PRI_BT709)
        enc.color_trc(outTRCFFmpeg)
        enc.colorspace(if (outRGB) AVCOL_SPC_RGB else outYCbCrCoeffsFFmpeg)
        enc.color_range(outRangeFFmpeg)

        // Timebase: This is the fundamental unit of time (in seconds) in terms
        // of which frame timestamps are represented. For fixed-fps content,
        // timebase should be 1/framerate and timestamp increments should be
        // identical to 1.
        val timebase = AVRational().apply { num(fps.denominator); den(fps.numerator) }
        st.time_base(timebase)
        enc.time_base(timebase)

        // Some muxers, for example MKV, require the framerate to be set directly on the stream.
        // Otherwise, they infer it incorrectly.
        st.avg_frame_rate(AVRational().apply { num(fps.numerator); den(fps.denominator) })

        when (codecId) {
            // Needed to avoid using macroblocks in which some coeffs overflow.
            // This does not happen with normal video, it just happens here as
            // the motion of the chroma plane does not match the luma plane.
            AV_CODEC_ID_MPEG1VIDEO -> enc.mb_decision(2)
            // Default to constrained baseline to produce content that plays back on anything,
            // without any significant tradeoffs for most use cases.
            AV_CODEC_ID_H264 -> enc.profile(FF_PROFILE_H264_CONSTRAINED_BASELINE)
        }

        // Some formats want stream headers to be separate.
        if (oc.oformat().flags() and AVFMT_GLOBALHEADER != 0)
            enc.flags(enc.flags() or AV_CODEC_FLAG_GLOBAL_HEADER)
    }

    private inline fun withOptionsDict(options: Map<String, String>, block: (AVDictionary) -> Unit) {
        val dict = AVDictionary()
        try {
            for ((key, value) in options)
                av_dict_set(dict, key, value, 0)
            block(dict)
        } finally {
            av_dict_free(dict)
        }
    }

    /**
     * Encodes one video frame and sends it to the muxer.
     * Images must be encoded as [BufferedImage.TYPE_3BYTE_BGR] or [BufferedImage.TYPE_4BYTE_ABGR],
     * depending on whether the outPixelFormat has an alpha channel. They must also use the sRGB color space.
     */
    fun writeFrame(image: BufferedImage) {
        // Convert the BufferedImage to a frame.
        val frame = pipe!!.process(image)
            .pts(frameCounter++)
        writeFrame(frame)
    }

    private fun writeFrame(frame: AVFrame?) {
        val st = this.st!!
        val enc = this.enc!!

        // Send the frame to the encoder.
        avcodec_send_frame(enc, frame).throwIfErrnum("delivery.ffmpeg.sendFrameError")

        var ret = 0
        while (ret >= 0) {
            val pkt = AVPacket()
            try {
                ret = avcodec_receive_packet(enc, pkt)
                if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF)
                    break
                ret.throwIfErrnum("delivery.ffmpeg.encodeFrameError")

                // Inform the packet about which stream it should be written to.
                pkt.stream_index(st.index())

                // Set the duration of the frame delivered by the current packet. This is required for most codec/muxer
                // combinations to recognize the framerate. Relative to the encoder timebase (which is 1/fps) for now.
                pkt.duration(1)

                // Now rescale output packet timestamp values (including duration) from codec to stream timebase.
                av_packet_rescale_ts(pkt, enc.time_base(), st.time_base())

                // Write the compressed frame to the media file.
                ret = av_interleaved_write_frame(oc, pkt).throwIfErrnum("delivery.ffmpeg.writeFrameError")
            } finally {
                av_packet_unref(pkt)
            }
        }
    }

    override fun close() {
        try {
            // Write a null frame to terminate the stream.
            writeFrame(null)

            // Write the trailer, if any.
            av_write_trailer(oc).throwIfErrnum("delivery.ffmpeg.closeFileError")
        } finally {
            release()
        }
    }

    private fun release() {
        pipeProcs.forEach(FrameProcessor::release)
        pipe?.release()
        enc.letIfNonNull(::avcodec_free_context)

        oc.letIfNonNull { oc ->
            // Close the output file, if needed.
            if (oc.oformat().flags() and AVFMT_NOFILE == 0)
                oc.pb().letIfNonNull(::avio_closep)

            avformat_free_context(oc)
        }
    }


    companion object {

        // SIMD instructions require the buffers to have certain alignment. The strictest of them all is AVX-512, which
        // requires 512-bit alignment.
        const val BYTE_ALIGNMENT = 64

        private fun callSetup(release: () -> Unit, setup: () -> Unit) {
            try {
                setup()
            } catch (e: AVException) {
                try {
                    release()
                } catch (e2: AVException) {
                    e.addSuppressed(e2)
                }
                throw e
            }
        }

        private inline fun <P : Pointer> P?.letIfNonNull(block: (P) -> Unit) {
            if (this != null && !this.isNull)
                block(this)
        }

        private fun <P : Pointer> P?.throwIfNull(l10nKey: String, vararg l10nArgs: Any?): P =
            if (this == null || this.isNull)
                throw AVException(l10n(l10nKey, *l10nArgs) + ".")
            else this

        private fun Int.throwIfErrnum(l10nKey: String, vararg l10nArgs: Any?): Int =
            if (this < 0)
                throw AVException(
                    "${l10n(l10nKey, *l10nArgs)}: ${err2str(this)} (${l10n("delivery.ffmpeg.errorNumber")} $this)."
                )
            else this

        // Replicates the macro of the same name from error.h.
        private fun err2str(errnum: Int): String {
            val string = BytePointer(AV_ERROR_MAX_STRING_SIZE.toLong())
            av_make_error_string(string, AV_ERROR_MAX_STRING_SIZE.toLong(), errnum)
            string.limit(BytePointer.strlen(string))
            return string.string
        }

    }


    private class AVException(message: String) : RuntimeException(message)


    /** @see [VideoWriter.writeFrame] for restrictions on the input image type and color space. */
    private class FramePipeline(
        private val resolution: Resolution,
        private val processors: List<FrameProcessor>
    ) {

        private val inPixelFormat = processors.first().inPixelFormat

        // We only accept BufferedImages whose format is equivalent to the input pixel format.
        private val permittedImageType = when (inPixelFormat) {
            AV_PIX_FMT_BGR24 -> BufferedImage.TYPE_3BYTE_BGR
            AV_PIX_FMT_ABGR -> BufferedImage.TYPE_4BYTE_ABGR
            else -> throw AVException("Illegal pipeline input pixel format: $inPixelFormat")
        }

        private val frames = mutableListOf<AVFrame>()

        init {
            callSetup(::release, ::setup)
        }

        private fun setup() {
            // Allocate reusable frames, which connect the processors with each other and with the input and output.
            for (i in 0..processors.size) {
                // Ensure that the output format of a processor is equal to the input format of the following processor.
                if (i < processors.size - 1)
                    require(processors[i].outPixelFormat == processors[i + 1].inPixelFormat)
                // Allocate the frame struct.
                val frame = av_frame_alloc().throwIfNull("delivery.ffmpeg.allocFrameError").apply {
                    format(if (i < processors.size) processors[i].inPixelFormat else processors[i - 1].outPixelFormat)
                    width(resolution.widthPx)
                    height(resolution.heightPx)
                }
                // Allocate buffers to hold the image data.
                av_frame_get_buffer(frame, 8 * BYTE_ALIGNMENT).throwIfErrnum("delivery.ffmpeg.allocFrameDataError")
                frames += frame
            }
        }

        fun process(image: BufferedImage): AVFrame {
            require(image.type == permittedImageType)
            require(image.colorModel.colorSpace.isCS_sRGB)

            val inFrame = frames.first()
            val outFrame = frames.last()

            // When we pass a frame to the encoder, it may keep a reference to it internally;
            // make sure we do not overwrite it here.
            if (processors.isNotEmpty())
                av_frame_make_writable(outFrame).throwIfErrnum("delivery.ffmpeg.makeFrameWritableError")

            // Transfer the BufferedImage's data into the input frame. Two copies are necessary because:
            //   - JavaCPP cannot directly pass pointers to Java arrays on the heap to the native C code.
            //   - We need av_image_copy_plane() to insert padding between lines according to inFrame's linesize.
            // We have empirically confirmed that two copies are just as fast as one copy in the grand scheme of things.
            val imageArray = ((image.raster.dataBuffer) as DataBufferByte).data
            val imageData = BytePointer(imageArray.size.toLong()).put(imageArray, 0, imageArray.size)
            val imageLinesize = av_image_get_linesize(inPixelFormat, resolution.widthPx, 0)
            av_image_copy_plane(
                inFrame.data(0), inFrame.linesize(0), imageData, imageLinesize,
                imageLinesize, resolution.heightPx
            )

            // Apply all processors in sequence.
            for ((i, processor) in processors.withIndex())
                processor.process(frames[i], frames[i + 1])

            return outFrame
        }

        /** Warning: This method doesn't release the wrapped [processors]! */
        fun release() {
            for (frame in frames)
                frame.letIfNonNull(::av_frame_free)
        }

    }


    private interface FrameProcessor {
        val inPixelFormat: Int
        val outPixelFormat: Int
        fun process(inFrame: AVFrame, outFrame: AVFrame)
        fun release()
    }


    private class SwsFrameProcessor(
        private val resolution: Resolution,
        override val inPixelFormat: Int,
        override val outPixelFormat: Int
    ) : FrameProcessor {

        private var swsCtx: SwsContext? = null

        init {
            callSetup(::release, ::setup)
        }

        private fun setup() {
            swsCtx = sws_getContext(
                resolution.widthPx, resolution.heightPx, inPixelFormat,
                resolution.widthPx, resolution.heightPx, outPixelFormat,
                0, null, null, null as DoublePointer?
            ).throwIfNull("delivery.ffmpeg.getConverterError")
        }

        override fun process(inFrame: AVFrame, outFrame: AVFrame) {
            sws_scale(
                swsCtx, inFrame.data(), inFrame.linesize(), 0, resolution.heightPx, outFrame.data(), outFrame.linesize()
            )
        }

        override fun release() {
            swsCtx.letIfNonNull(::sws_freeContext)
        }

    }


    /**
     * The processor makes the following assumptions about the pixel formats:
     *   - The input frame is full range and uses the sRGB primaries and transfer characteristic.
     *   - [inPixelFormat] is planar GBR(A) with 8-bit samples.
     *   - [outPixelFormat] is planar GBR(A) or planar YCbCr(A).
     */
    private class ZimgFrameProcessor(
        resolution: Resolution,
        override val inPixelFormat: Int,
        override val outPixelFormat: Int,
        outRange: Range,
        outTransferCharacteristic: TransferCharacteristic,
        outYCbCrCoefficients: YCbCrCoefficients
    ) : FrameProcessor {

        private var inPixFmtDesc: AVPixFmtDescriptor? = null
        private var outPixFmtDesc: AVPixFmtDescriptor? = null
        private var hasAlpha: Boolean = false

        private var scope: ResourceScope? = null
        private var graph: MemoryAddress? = null
        private var srcBuf: MemorySegment? = null
        private var dstBuf: MemorySegment? = null
        private var tmpBuf: MemorySegment? = null

        init {
            callSetup(::release) { setup(resolution, outRange, outTransferCharacteristic, outYCbCrCoefficients) }
        }

        fun setup(
            resolution: Resolution,
            outRange: Range,
            outTransferCharacteristic: TransferCharacteristic,
            outYCbCrCoefficients: YCbCrCoefficients
        ) {
            inPixFmtDesc = av_pix_fmt_desc_get(inPixelFormat).throwIfNull("delivery.ffmpeg.getPixFmtError")
            val outPixFmtDesc = av_pix_fmt_desc_get(outPixelFormat).throwIfNull("delivery.ffmpeg.getPixFmtError")
            this.outPixFmtDesc = outPixFmtDesc

            hasAlpha = outPixFmtDesc.flags() and AV_PIX_FMT_FLAG_ALPHA.toLong() != 0L
            val outRGB = outPixFmtDesc.flags() and AV_PIX_FMT_FLAG_RGB.toLong() != 0L
            val outDepth = outPixFmtDesc.comp(0).depth()

            val outRangeZimg = when (outRange) {
                Range.FULL -> ZIMG_RANGE_FULL()
                Range.LIMITED -> ZIMG_RANGE_LIMITED()
            }
            val outTRCZimg = when (outTransferCharacteristic) {
                TransferCharacteristic.SRGB -> ZIMG_TRANSFER_IEC_61966_2_1()
                TransferCharacteristic.BT709 -> ZIMG_TRANSFER_BT709()
            }
            val outYCbCrCoeffsZimg = when (outYCbCrCoefficients) {
                YCbCrCoefficients.BT601 -> ZIMG_MATRIX_BT470_BG()
                YCbCrCoefficients.BT709 -> ZIMG_MATRIX_BT709()
            }

            scope = ResourceScope.newConfinedScope()
            val srcFmt = zimg_image_format.allocate(scope)
            val dstFmt = zimg_image_format.allocate(scope)
            zimg_image_format_default(srcFmt, ZIMG_API_VERSION())
            zimg_image_format_default(dstFmt, ZIMG_API_VERSION())
            zimg_image_format.`width$set`(srcFmt, resolution.widthPx)
            zimg_image_format.`width$set`(dstFmt, resolution.widthPx)
            zimg_image_format.`height$set`(srcFmt, resolution.heightPx)
            zimg_image_format.`height$set`(dstFmt, resolution.heightPx)
            zimg_image_format.`pixel_type$set`(srcFmt, ZIMG_PIXEL_BYTE())
            zimg_image_format.`pixel_type$set`(dstFmt, if (outDepth > 8) ZIMG_PIXEL_WORD() else ZIMG_PIXEL_BYTE())
            if (!outRGB) {
                zimg_image_format.`subsample_w$set`(dstFmt, outPixFmtDesc.log2_chroma_w().toInt())
                zimg_image_format.`subsample_h$set`(dstFmt, outPixFmtDesc.log2_chroma_h().toInt())
            }
            zimg_image_format.`color_family$set`(srcFmt, ZIMG_COLOR_RGB())
            zimg_image_format.`color_family$set`(dstFmt, if (outRGB) ZIMG_COLOR_RGB() else ZIMG_COLOR_YUV())
            zimg_image_format.`matrix_coefficients$set`(srcFmt, ZIMG_MATRIX_RGB())
            zimg_image_format.`matrix_coefficients$set`(dstFmt, if (outRGB) ZIMG_MATRIX_RGB() else outYCbCrCoeffsZimg)
            zimg_image_format.`transfer_characteristics$set`(srcFmt, ZIMG_TRANSFER_IEC_61966_2_1() /* sRGB */)
            zimg_image_format.`transfer_characteristics$set`(dstFmt, outTRCZimg)
            zimg_image_format.`color_primaries$set`(srcFmt, ZIMG_PRIMARIES_709())
            zimg_image_format.`color_primaries$set`(dstFmt, ZIMG_PRIMARIES_709())
            zimg_image_format.`depth$set`(srcFmt, 8)
            zimg_image_format.`depth$set`(dstFmt, outDepth)
            zimg_image_format.`pixel_range$set`(srcFmt, ZIMG_RANGE_FULL())
            zimg_image_format.`pixel_range$set`(dstFmt, outRangeZimg)
            zimg_image_format.`alpha$set`(srcFmt, if (hasAlpha) ZIMG_ALPHA_STRAIGHT() else ZIMG_ALPHA_NONE())
            zimg_image_format.`alpha$set`(dstFmt, if (hasAlpha) ZIMG_ALPHA_STRAIGHT() else ZIMG_ALPHA_NONE())
            val params = zimg_graph_builder_params.allocate(scope)
            zimg_graph_builder_params_default(params, ZIMG_API_VERSION())
            zimg_graph_builder_params.`cpu_type$set`(params, ZIMG_CPU_AUTO_64B())
            graph = zimg_filter_graph_build(srcFmt, dstFmt, params).zimgThrowIfNull("delivery.zimg.buildGraphError")

            srcBuf = zimg_image_buffer_const.allocate(scope)
            dstBuf = zimg_image_buffer.allocate(scope)
            zimg_image_buffer_const.`version$set`(srcBuf, ZIMG_API_VERSION())
            zimg_image_buffer.`version$set`(dstBuf, ZIMG_API_VERSION())
            for (plane in 0L until 4L) {
                ZIMG_BUF_CONST_MASK.set(srcBuf, plane, ZIMG_BUFFER_MAX())
                ZIMG_BUF_MASK.set(dstBuf, plane, ZIMG_BUFFER_MAX())
            }

            val tmpSize = MemorySegment.allocateNative(JAVA_LONG, scope)
            zimg_filter_graph_get_tmp_size(graph, tmpSize).zimgThrowIfErrnum("delivery.zimg.getTmpSizeError")
            tmpBuf = MemorySegment.allocateNative(tmpSize.toLongArray().single(), BYTE_ALIGNMENT.toLong(), scope)
        }

        override fun process(inFrame: AVFrame, outFrame: AVFrame) {
            for (zimgPlane in 0 until if (hasAlpha) 4 else 3) {
                val zimgPlaneL = zimgPlane.toLong()
                val ffmpegInPlane = inPixFmtDesc!!.comp(zimgPlane).plane()
                val ffmpegOutPlane = outPixFmtDesc!!.comp(zimgPlane).plane()
                ZIMG_BUF_CONST_DATA.set(srcBuf, zimgPlaneL, inFrame.data(ffmpegInPlane).address())
                ZIMG_BUF_CONST_STRIDE.set(srcBuf, zimgPlaneL, inFrame.linesize(ffmpegInPlane).toLong())
                ZIMG_BUF_DATA.set(dstBuf, zimgPlaneL, outFrame.data(ffmpegOutPlane).address())
                ZIMG_BUF_STRIDE.set(dstBuf, zimgPlaneL, outFrame.linesize(ffmpegOutPlane).toLong())
            }
            zimg_filter_graph_process(graph, srcBuf, dstBuf, tmpBuf, NULL, NULL, NULL, NULL)
                .zimgThrowIfErrnum("delivery.zimg.processError")
        }

        override fun release() {
            graph?.let(::zimg_filter_graph_free)
            scope?.close()
        }

        private fun MemoryAddress?.zimgThrowIfNull(l10nKey: String): MemoryAddress =
            if (this == null || this.toRawLongValue() == 0L)
                throw AVException(zimgExcStr(l10nKey))
            else this

        private fun Int.zimgThrowIfErrnum(l10nKey: String): Int =
            if (this < 0)
                throw AVException(zimgExcStr(l10nKey))
            else this

        private fun zimgExcStr(l10nKey: String): String {
            val string = MemorySegment.allocateNative(1024, scope)
            val errnum = zimg_get_last_error(string, string.byteSize())
            return "${l10n(l10nKey)}: ${CLinker.toJavaString(string)} (${l10n("delivery.zimg.errorNumber")} $errnum)."
        }

        companion object {
            private val ZIMG_BUF_CONST_DATA: VarHandle
            private val ZIMG_BUF_CONST_STRIDE: VarHandle
            private val ZIMG_BUF_CONST_MASK: VarHandle
            private val ZIMG_BUF_DATA: VarHandle
            private val ZIMG_BUF_STRIDE: VarHandle
            private val ZIMG_BUF_MASK: VarHandle

            init {
                val planePath = arrayOf(groupElement("plane"), sequenceElement())
                val bufConst = zimg_image_buffer_const.`$LAYOUT`()
                val buf = zimg_image_buffer.`$LAYOUT`()
                ZIMG_BUF_CONST_DATA = bufConst.varHandle(Long::class.java, *planePath, groupElement("data"))
                ZIMG_BUF_CONST_STRIDE = bufConst.varHandle(Long::class.java, *planePath, groupElement("stride"))
                ZIMG_BUF_CONST_MASK = bufConst.varHandle(Int::class.java, *planePath, groupElement("mask"))
                ZIMG_BUF_DATA = buf.varHandle(Long::class.java, *planePath, groupElement("data"))
                ZIMG_BUF_STRIDE = buf.varHandle(Long::class.java, *planePath, groupElement("stride"))
                ZIMG_BUF_MASK = buf.varHandle(Int::class.java, *planePath, groupElement("mask"))
            }
        }

    }

}
