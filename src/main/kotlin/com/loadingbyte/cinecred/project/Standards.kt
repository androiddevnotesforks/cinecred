package com.loadingbyte.cinecred.project

import kotlinx.collections.immutable.persistentListOf
import java.awt.Color


val STANDARD_GLOBAL = Global(
    fps = FPS(24, 1),
    widthPx = 1920,
    heightPx = 1080,
    background = Color.BLACK,
    unitVGapPx = 32f
)


val STANDARD_PAGE_STYLE = PageStyle(
    name = "???",
    behavior = PageBehavior.SCROLL,
    meltWithPrev = false,
    meltWithNext = false,
    afterwardSlugFrames = 24,
    cardDurationFrames = 96,
    cardFadeInFrames = 12,
    cardFadeOutFrames = 12,
    scrollPxPerFrame = 3f
)


val STANDARD_CONTENT_STYLE = ContentStyle(
    name = "???",
    spineOrientation = SpineOrientation.VERTICAL,
    alignWithAxis = AlignWithAxis.OVERALL_CENTER,
    vMarginPx = 0f,
    bodyLayout = BodyLayout.GRID,
    bodyLayoutLineGapPx = 0f,
    bodyLayoutElemConform = BodyElementConform.NOTHING,
    bodyLayoutElemVJustify = VJustify.MIDDLE,
    bodyLayoutHorizontalGapPx = 32f,
    bodyLayoutColsHJustify = persistentListOf(HJustify.CENTER),
    bodyLayoutLineHJustify = LineHJustify.CENTER,
    bodyLayoutBodyWidthPx = 1000f,
    bodyLayoutElemHJustify = HJustify.CENTER,
    bodyLayoutSeparator = "\u2022",
    bodyLayoutParagraphGapPx = 8f,
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


val STANDARD_LETTER_STYLE = LetterStyle(
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
