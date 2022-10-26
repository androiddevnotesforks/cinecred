@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.loadingbyte.cinecred.common

import com.loadingbyte.cinecred.natives.harfbuzz.*
import com.loadingbyte.cinecred.natives.harfbuzz.hb_h.*
import jdk.incubator.foreign.CLinker.toCString
import jdk.incubator.foreign.MemoryAddress
import jdk.incubator.foreign.MemoryAddress.NULL
import jdk.incubator.foreign.MemoryLayouts.JAVA_BYTE
import jdk.incubator.foreign.MemoryLayouts.JAVA_CHAR
import jdk.incubator.foreign.MemorySegment
import jdk.incubator.foreign.MemorySegment.globalNativeSegment
import jdk.incubator.foreign.ResourceScope
import jdk.incubator.foreign.ResourceScope.newConfinedScope
import jdk.incubator.foreign.SegmentAllocator.ofScope
import sun.font.*
import java.awt.geom.Point2D
import java.lang.ref.Cleaner
import java.util.*


class CustomGlyphLayoutEngine private constructor(
    private val key: GlyphLayout.LayoutEngineKey,
    private val configSource: ExtConfigSource
) : GlyphLayout.LayoutEngine {

    override fun layout(
        sd: FontStrikeDesc,
        mat: FloatArray,
        ptSize: Float,
        gmask: Int,
        baseIndex: Int,
        tr: TextRecord,
        typoFlags: Int,
        startPt: Point2D.Float,
        gvData: GlyphLayout.GVData
    ) {
        val font = key.getFont()
        val script = key.getScript()
        val config = configSource.get(tr.start, tr.limit)

        val hbFace = getHBFace(font)

        val kern = typoFlags and 0x00000001 != 0
        val liga = typoFlags and 0x00000002 != 0
        val rtl = typoFlags and 0x80000000.toInt() != 0
        val lang = config.locale.toLanguageTag()
        val trackingPx = config.trackingEm * ptSize
        val bearingLeftPx = config.bearingLeftEm * ptSize
        val bearingRightPx = config.bearingRightEm * ptSize
        val userFeats = config.features

        // Note: Whenever we allocate memory here, we do not have to check for allocation errors in the form of a NULL
        // pointer since (a) Java throws an OutOfMemoryError whenever some allocation we directly request fails and (b)
        // HarfBuzz returns empty singletons instead of NULL pointers whenever it can't allocate memory.
        newConfinedScope().use { scope ->
            // Create an HB font object with the requested size.
            val hbFont = hb_font_create(hbFace)
            val fixedPtSize = (ptSize * FLOAT_TO_HB_FIXED).toUInt().toInt()
            hb_font_set_scale(hbFont, fixedPtSize, fixedPtSize)

            // Create an HB buffer, configure it and fill it with the text.
            val hbBuffer = hb_buffer_create()
            hb_buffer_set_script(hbBuffer, icuToHBScriptCode(script))
            hb_buffer_set_language(hbBuffer, hb_language_from_string(toCString(lang, scope), -1))
            hb_buffer_set_direction(hbBuffer, if (rtl) HB_DIRECTION_RTL() else HB_DIRECTION_LTR())
            // Note: We need this cluster level for correctly identifying graphemes, which tracking shouldn't break.
            hb_buffer_set_cluster_level(hbBuffer, HB_BUFFER_CLUSTER_LEVEL_MONOTONE_GRAPHEMES())
            val chars = ofScope(scope).allocateArray(JAVA_CHAR, tr.text)
            hb_buffer_add_utf16(hbBuffer, chars, tr.text.size, tr.start, tr.limit - tr.start)

            // Create an HB feature array and fill it.
            val numFeatures = 1 + LIGATURES_FONT_FEATS.size + userFeats.size
            val hbFeatures = hb_feature_t.allocateArray(numFeatures, scope)
            var featureIdx = 0L
            configureFeature(hbFeatures, featureIdx++, KERNING_FONT_FEAT, if (kern) 1 else 0)
            for (tag in LIGATURES_FONT_FEATS)
                configureFeature(hbFeatures, featureIdx++, tag, if (liga) 1 else 0)
            for (feat in userFeats)
                configureFeature(hbFeatures, featureIdx++, feat.tag, feat.value)

            // Run the HB shaping algorithm.
            hb_shape(hbFont, hbBuffer, hbFeatures, numFeatures)

            // Extract the shaping result.
            val glyphCount = hb_buffer_get_length(hbBuffer)
            val glyphInfo = globalNativeSegment().asSlice(hb_buffer_get_glyph_infos(hbBuffer, NULL))
            val glyphPos = globalNativeSegment().asSlice(hb_buffer_get_glyph_positions(hbBuffer, NULL))

            // For easier access, get the array into which we will store the shaping result.
            val glyphs = gvData._glyphs
            val positions = gvData._positions
            val indices = gvData._indices

            // Expand those arrays if necessary.
            val delta = (gvData._count + glyphCount) - glyphs.size
            if (delta > 0)
                gvData.grow(delta)

            // Transfer the shaping result into those arrays.
            var x = startPt.x + bearingLeftPx
            var y = startPt.y
            for (getIdx in 0L until glyphCount.toLong()) {
                val setIdx = gvData._count++
                glyphs[setIdx] = hb_glyph_info_t.`codepoint$get`(glyphInfo, getIdx) or gmask
                indices[setIdx] = baseIndex + hb_glyph_info_t.`cluster$get`(glyphInfo, getIdx) - tr.start
                if (setIdx != 0 && indices[setIdx] != indices[setIdx - 1])
                    x += trackingPx
                positions[setIdx * 2] = x + hb_glyph_position_t.`x_offset$get`(glyphPos, getIdx) / FLOAT_TO_HB_FIXED
                positions[setIdx * 2 + 1] = y - hb_glyph_position_t.`y_offset$get`(glyphPos, getIdx) / FLOAT_TO_HB_FIXED
                x += hb_glyph_position_t.`x_advance$get`(glyphPos, getIdx) / FLOAT_TO_HB_FIXED
                y += hb_glyph_position_t.`y_advance$get`(glyphPos, getIdx) / FLOAT_TO_HB_FIXED
            }

            // Now, x respectively y hold the start point plus the advance of the string. Move the start point there
            // in preparation for the next layout step, and also store it in the positions array since the calling code
            // expects that.
            x += bearingRightPx
            startPt.x = x
            startPt.y = y
            positions[gvData._count * 2] = x
            positions[gvData._count * 2 + 1] = y

            // Free the manually allocated memory.
            hb_font_destroy(hbFont)
            hb_buffer_destroy(hbBuffer)
        }
    }


    companion object {

        private val configSource = ThreadLocal<ExtConfigSource?>()

        // Get the original factory before we change it a couple of lines below.
        private val SUN_LAYOUT_ENGINE_FACTORY = SunLayoutEngine.instance()

        init {
            val factory = object : GlyphLayout.LayoutEngineFactory {
                override fun getEngine(font: Font2D, script: Int, lang: Int) = throw NotImplementedError()
                override fun getEngine(key: GlyphLayout.LayoutEngineKey): GlyphLayout.LayoutEngine =
                    when (val configSource = configSource.get()) {
                        null -> SUN_LAYOUT_ENGINE_FACTORY.getEngine(key)
                        else -> CustomGlyphLayoutEngine(key, configSource)
                    }
            }
            SunLayoutEngine::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, factory)
        }

        fun begin(configSource: ExtConfigSource) {
            this.configSource.set(configSource)
        }

        fun end() {
            configSource.set(null)
        }


        private val hbFaces = WeakHashMap<Font2D, MemoryAddress>()
        private val hbFaceCleaner = Cleaner.create()

        private fun getHBFace(font: Font2D) =
            synchronized(hbFaces) {
                hbFaces.computeIfAbsent(font) {
                    val faceScope = newConfinedScope()
                    val tableFunc = hb_reference_table_func_t.allocate({ _: MemoryAddress, tag: Int, _: MemoryAddress ->
                        try {
                            val javaArr = font.getTableBytes(tag) ?: return@allocate NULL
                            val blobScope = newConfinedScope()
                            val cArr = ofScope(blobScope).allocateArray(JAVA_BYTE, javaArr)
                            hb_blob_create(cArr, javaArr.size, HB_MEMORY_MODE_WRITABLE(), NULL, destroyFunc(blobScope))
                        } catch (t: Throwable) {
                            // We have to catch all exceptions because if one escapes, a segfault happens.
                            runCatching { LOGGER.error("Cannot read table $tag of font ${font.postscriptName}", t) }
                            NULL
                        }
                    }, faceScope)
                    val face = hb_face_create_for_tables(tableFunc, NULL, destroyFunc(faceScope))
                    hbFaceCleaner.register(font) { hb_face_destroy(face) }
                    face
                }
            }

        private fun destroyFunc(scope: ResourceScope) =
            hb_destroy_func_t.allocate({
                try {
                    scope.close()
                } catch (t: Throwable) {
                    // We have to catch all exceptions because if one escapes, a segfault happens.
                    runCatching { LOGGER.error("Cannot close native memory resource scope", t) }
                }
            }, scope)


        private const val FLOAT_TO_HB_FIXED = (1 shl 16).toFloat()


        private fun icuToHBScriptCode(code: Int) =
            ICU_TO_HB_SCRIPT_CODE.getOrElse(code) { HB_SCRIPT_INVALID() }

        // Adapted from "scriptMapping.c" from the JDK sources.
        private val ICU_TO_HB_SCRIPT_CODE = intArrayOf(
            HB_SCRIPT_COMMON(),           /* 0 */
            HB_SCRIPT_INHERITED(),        /* 1 */
            HB_SCRIPT_ARABIC(),           /* 2 */
            HB_SCRIPT_ARMENIAN(),         /* 3 */
            HB_SCRIPT_BENGALI(),          /* 4 */
            HB_SCRIPT_BOPOMOFO(),         /* 5 */
            HB_SCRIPT_CHEROKEE(),         /* 6 */
            HB_SCRIPT_COPTIC(),           /* 7 */
            HB_SCRIPT_CYRILLIC(),         /* 8 */
            HB_SCRIPT_DESERET(),          /* 9 */
            HB_SCRIPT_DEVANAGARI(),       /* 10 */
            HB_SCRIPT_ETHIOPIC(),         /* 11 */
            HB_SCRIPT_GEORGIAN(),         /* 12 */
            HB_SCRIPT_GOTHIC(),           /* 13 */
            HB_SCRIPT_GREEK(),            /* 14 */
            HB_SCRIPT_GUJARATI(),         /* 15 */
            HB_SCRIPT_GURMUKHI(),         /* 16 */
            HB_SCRIPT_HAN(),              /* 17 */
            HB_SCRIPT_HANGUL(),           /* 18 */
            HB_SCRIPT_HEBREW(),           /* 19 */
            HB_SCRIPT_HIRAGANA(),         /* 20 */
            HB_SCRIPT_KANNADA(),          /* 21 */
            HB_SCRIPT_KATAKANA(),         /* 22 */
            HB_SCRIPT_KHMER(),            /* 23 */
            HB_SCRIPT_LAO(),              /* 24 */
            HB_SCRIPT_LATIN(),            /* 25 */
            HB_SCRIPT_MALAYALAM(),        /* 26 */
            HB_SCRIPT_MONGOLIAN(),        /* 27 */
            HB_SCRIPT_MYANMAR(),          /* 28 */
            HB_SCRIPT_OGHAM(),            /* 29 */
            HB_SCRIPT_OLD_ITALIC(),       /* 30 */
            HB_SCRIPT_ORIYA(),            /* 31 */
            HB_SCRIPT_RUNIC(),            /* 32 */
            HB_SCRIPT_SINHALA(),          /* 33 */
            HB_SCRIPT_SYRIAC(),           /* 34 */
            HB_SCRIPT_TAMIL(),            /* 35 */
            HB_SCRIPT_TELUGU(),           /* 36 */
            HB_SCRIPT_THAANA(),           /* 37 */
            HB_SCRIPT_THAI(),             /* 38 */
            HB_SCRIPT_TIBETAN(),          /* 39 */
            HB_SCRIPT_CANADIAN_SYLLABICS(),   /* 40 */
            HB_SCRIPT_YI(),               /* 41 */
            HB_SCRIPT_TAGALOG(),          /* 42 */
            HB_SCRIPT_HANUNOO(),          /* 43 */
            HB_SCRIPT_BUHID(),            /* 44 */
            HB_SCRIPT_TAGBANWA(),         /* 45 */
        )


        private fun configureFeature(seg: MemorySegment, idx: Long, tag: String, value: Int) {
            if (tag.length == 4 && tag.all { it.code in 0..255 }) {
                val tagCode = (tag[0].code shl 24) or (tag[1].code shl 16) or (tag[2].code shl 8) or tag[3].code
                hb_feature_t.`tag$set`(seg, idx, tagCode)
            }
            if (value > 0)
                hb_feature_t.`value$set`(seg, idx, value)
            hb_feature_t.`start$set`(seg, idx, HB_FEATURE_GLOBAL_START())
            hb_feature_t.`end$set`(seg, idx, HB_FEATURE_GLOBAL_END())
        }

    }


    fun interface ExtConfigSource {
        fun get(startCharIdx: Int, endCharIdx: Int): ExtConfig
    }

    class ExtConfig(
        val locale: Locale,
        val trackingEm: Float,
        val bearingLeftEm: Float,
        val bearingRightEm: Float,
        val features: List<FormattedString.Font.Feature>
    )

}
