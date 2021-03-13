package com.loadingbyte.cinecred.project

import com.loadingbyte.cinecred.common.l10n
import kotlinx.collections.immutable.persistentListOf
import java.awt.Color


val PRESET_GLOBAL = Global(
    fps = FPS(24, 1),
    widthPx = 1920,
    heightPx = 1080,
    background = Color.BLACK,
    unitVGapPx = 32f
)


val PRESET_PAGE_STYLE = PageStyle(
    name = "???",
    behavior = PageBehavior.SCROLL,
    afterwardSlugFrames = 24,
    cardDurationFrames = 96,
    cardFadeInFrames = 12,
    cardFadeOutFrames = 12,
    scrollMeltWithPrev = false,
    scrollMeltWithNext = false,
    scrollPxPerFrame = 3f
)


val PRESET_CONTENT_STYLE = ContentStyle(
    name = "???",
    spineOrientation = SpineOrientation.VERTICAL,
    alignWithAxis = AlignWithAxis.OVERALL_CENTER,
    vMarginPx = 0f,
    bodyLayout = BodyLayout.GRID,
    gridElemBoxConform = BodyElementBoxConform.WIDTH,
    gridElemHJustifyPerCol = persistentListOf(HJustify.CENTER),
    gridElemVJustify = VJustify.MIDDLE,
    gridRowGapPx = 0f,
    gridColGapPx = 32f,
    flowElemBoxConform = BodyElementBoxConform.NOTHING,
    flowLineHJustify = LineHJustify.CENTER,
    flowElemHJustify = HJustify.CENTER,
    flowElemVJustify = VJustify.MIDDLE,
    flowLineWidthPx = 1000f,
    flowLineGapPx = 0f,
    flowHGapPx = 32f,
    flowSeparator = "\u2022",
    paragraphsLineHJustify = LineHJustify.CENTER,
    paragraphsLineWidthPx = 600f,
    paragraphsParaGapPx = 8f,
    paragraphsLineGapPx = 0f,
    bodyLetterStyleName = "???",
    hasHead = false,
    headHJustify = HJustify.CENTER,
    headVJustify = VJustify.MIDDLE,
    headLetterStyleName = "???",
    headGapPx = 4f,
    hasTail = false,
    tailHJustify = HJustify.CENTER,
    tailVJustify = VJustify.MIDDLE,
    tailLetterStyleName = "???",
    tailGapPx = 4f
)


val PRESET_LETTER_STYLE = LetterStyle(
    name = "???",
    fontName = "Archivo Narrow Regular",
    heightPx = 32,
    tracking = 0f,
    superscript = Superscript.NONE,
    foreground = Color.WHITE,
    background = Color(0, 0, 0, 0),
    underline = false,
    strikethrough = false
)


val PLACEHOLDER_PAGE_STYLE = PRESET_PAGE_STYLE.copy(
    name = l10n("project.placeholder")
)


val PLACEHOLDER_CONTENT_STYLE = PRESET_CONTENT_STYLE.copy(
    name = l10n("project.placeholder")
)


val PLACEHOLDER_LETTER_STYLE = PRESET_LETTER_STYLE.copy(
    name = l10n("project.placeholder"),
    foreground = Color.BLACK,
    background = Color.ORANGE
)
