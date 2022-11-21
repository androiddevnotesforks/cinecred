package com.loadingbyte.cinecred.common

import org.apache.batik.ext.awt.image.GraphicsUtil
import org.apache.pdfbox.util.Matrix
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.RenderingHints.*
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.MessageFormat
import java.util.*


val VERSION = useResourceStream("/version") { it.bufferedReader().readText().trim() }
val LOGGER: Logger = LoggerFactory.getLogger("Cinecred")


enum class Severity { INFO, WARN, ERROR }


data class FPS(val numerator: Int, val denominator: Int) {
    val frac: Float
        get() = numerator.toFloat() / denominator
}


fun String.toFiniteFloat(nonNeg: Boolean = false, non0: Boolean = false): Float {
    val f = replace(',', '.').toFloat()
    if (!f.isFinite() || nonNeg && f < 0f || non0 && f == 0f)
        throw NumberFormatException()
    return f
}

fun <T> enumFromName(name: String, enumClass: Class<T>): T =
    enumClass.enumConstants.first { (it as Enum<*>).name.equals(name, ignoreCase = true) }

fun Color.toHex24() = "#%06x".format(rgb and 0x00FFFFFF)
fun Color.toHex32() = "#%08x".format(rgb)

// Note: We first have to convert to long and then to int because String.toInt() throws an exception when an
// overflowing number is decoded (which happens whenever alpha > 128, since the first bit of the color number is then 1,
// which is interpreted as a negative sign, so this is an overflow).
fun colorFromHex(hex: String): Color {
    require((hex.length == 7 || hex.length == 9) && hex[0] == '#')
    return Color(hex.drop(1).toLong(16).toInt(), hex.length == 9)
}

fun FPS.toFraction() = "$numerator/$denominator"

fun fpsFromFraction(fraction: String): FPS {
    val parts = fraction.split("/")
    require(parts.size == 2)
    return FPS(parts[0].toInt(), parts[1].toInt())
}


/** Implements `ceil(a / b)`, but works only for non-negative numbers! */
fun ceilDiv(a: Int, b: Int) = (a + (b - 1)) / b


inline fun <R> useResourceStream(path: String, action: (InputStream) -> R): R =
    Severity::class.java.getResourceAsStream(path)!!.use(action)

inline fun <R> useResourcePath(path: String, action: (Path) -> R): R {
    val uri = (Severity::class.java).getResource(path)!!.toURI()
    return if (uri.scheme == "jar")
        FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { fs ->
            action(fs.getPath(path))
        }
    else
        action(Path.of(uri))
}


val TRANSLATED_LOCALES: List<Locale> = listOf(Locale.ENGLISH, Locale.GERMAN)
val FALLBACK_TRANSLATED_LOCALE: Locale = Locale.ENGLISH

/**
 * This is the translated locale which is closest to the system's default locale. It necessarily has to be computed
 * before the default locale is changed for the first time, which is fulfilled by a read prior to that action.
 */
val SYSTEM_LOCALE: Locale = run {
    // Our detection algorithm is very primitive, so ensure that all translated locales solely specify a language.
    check(TRANSLATED_LOCALES.all { '_' !in it.toString() })
    TRANSLATED_LOCALES.find { it.language == Locale.getDefault().language } ?: FALLBACK_TRANSLATED_LOCALE
}

private val BUNDLE_CONTROL = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
private fun getL10nBundle(locale: Locale) = ResourceBundle.getBundle("l10n.strings", locale, BUNDLE_CONTROL)

fun l10n(key: String, locale: Locale = Locale.getDefault()): String = getL10nBundle(locale).getString(key)
fun l10n(key: String, vararg args: Any?, locale: Locale = Locale.getDefault()): String =
    MessageFormat.format(l10n(key, locale), *args)


val gCfg: GraphicsConfiguration
    get() = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration

// This is a reference font render context used to measure the size of fonts.
val REF_FRC: FontRenderContext = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
    .createGraphics()
    .apply { setHighQuality() }
    .fontRenderContext


fun AffineTransform.translate(tx: Float, ty: Float) = translate(tx.toDouble(), ty.toDouble())
fun AffineTransform.scale(sx: Float, sy: Float) = scale(sx.toDouble(), sy.toDouble())
fun AffineTransform.scale(s: Float) = scale(s.toDouble(), s.toDouble())
fun AffineTransform.shear(shx: Float, shy: Float) = shear(shx.toDouble(), shy.toDouble())
fun Graphics2D.translate(tx: Float, ty: Float) = translate(tx.toDouble(), ty.toDouble())
fun Graphics2D.scale(sx: Float, sy: Float) = scale(sx.toDouble(), sy.toDouble())
fun Graphics2D.scale(s: Float) = scale(s.toDouble(), s.toDouble())
fun Graphics2D.scale(s: Double) = scale(s, s)
fun Matrix.translate(tx: Double, ty: Double) = translate(tx.toFloat(), ty.toFloat())
fun Matrix.scale(s: Float) = scale(s, s)


inline fun Graphics.withNewG2(block: (Graphics2D) -> Unit) {
    val g2 = create() as Graphics2D
    try {
        block(g2)
    } finally {
        g2.dispose()
    }
}


inline fun Graphics2D.preserveTransform(block: () -> Unit) {
    val prevTransform = transform  // creates a defensive copy
    block()
    transform = prevTransform
}


inline fun BufferedImage.withG2(block: (Graphics2D) -> Unit): BufferedImage {
    // Let Batik create the graphics object. It makes sure that SVG content can be painted correctly.
    val g2 = GraphicsUtil.createGraphics(this)
    try {
        block(g2)
    } finally {
        g2.dispose()
    }
    return this
}


fun Graphics2D.setHighQuality() {
    setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
    setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY)
    setRenderingHint(KEY_DITHERING, VALUE_DITHER_DISABLE)
    setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BICUBIC)
    setRenderingHint(KEY_ALPHA_INTERPOLATION, VALUE_ALPHA_INTERPOLATION_QUALITY)
    setRenderingHint(KEY_COLOR_RENDERING, VALUE_COLOR_RENDER_QUALITY)
    setRenderingHint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON)
    // Note that we not only activate fractional font metrics because the result obviously looks better, but also
    // because if we don't, the getStringWidth() method sometimes yields incorrect results.
    setRenderingHint(KEY_FRACTIONALMETRICS, VALUE_FRACTIONALMETRICS_ON)
}


const val KERNING_FONT_FEAT = "kern"
val LIGATURES_FONT_FEATS = setOf("liga", "clig")
const val SMALL_CAPS_FONT_FEAT = "smcp"
const val PETITE_CAPS_FONT_FEAT = "pcap"
const val CAPITAL_SPACING_FONT_FEAT = "cpsp"
val MANAGED_FONT_FEATS = LIGATURES_FONT_FEATS +
        setOf(KERNING_FONT_FEAT, SMALL_CAPS_FONT_FEAT, PETITE_CAPS_FONT_FEAT, CAPITAL_SPACING_FONT_FEAT)


val Font.lineMetrics: LineMetrics
    get() = getLineMetrics("Beispiel!", REF_FRC)
