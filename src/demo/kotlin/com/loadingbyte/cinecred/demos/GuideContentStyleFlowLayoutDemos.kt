package com.loadingbyte.cinecred.demos

import com.loadingbyte.cinecred.common.l10n
import com.loadingbyte.cinecred.demo.PageDemo
import com.loadingbyte.cinecred.demo.StyleSettingsDemo
import com.loadingbyte.cinecred.demo.parseCreditsCS
import com.loadingbyte.cinecred.project.*
import kotlinx.collections.immutable.persistentListOf


private const val DIR = "guide/content-style/flow-layout"

val GUIDE_CONTENT_STYLE_FLOW_LAYOUT_DEMOS
    get() = listOf(
        GuideContentStyleFlowLayoutExampleDemo,
        GuideContentStyleFlowLayoutDirectionDemo,
        GuideContentStyleFlowLayoutJustifyLinesDemo,
        GuideContentStyleFlowLayoutSquareCellsDemo,
        GuideContentStyleFlowLayoutForceCellWidthAndHeightDemo,
        GuideContentStyleFlowLayoutMatchCellWidthAndHeightDemo,
        GuideContentStyleFlowLayoutCellHJustifyAndVJustifyDemo,
        GuideContentStyleFlowLayoutLineWidthDemo,
        GuideContentStyleFlowLayoutLineGapAndHGapDemo,
        GuideContentStyleFlowLayoutSeparatorDemo
    )


object GuideContentStyleFlowLayoutExampleDemo : PageDemo("$DIR/example", Format.PNG, pageGuides = true) {
    override fun credits() = listOf(FLOW_SPREADSHEET.parseCreditsCS(bulletsCS.copy(name = "Demo")))
}


object GuideContentStyleFlowLayoutDirectionDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/direction", Format.STEP_GIF,
    listOf(ContentStyle::flowDirection.st()), pageGuides = true
) {
    override fun styles() = buildList<ContentStyle> {
        for (direction in FlowDirection.entries)
            this += bulletsCS.copy(name = "Demo", flowDirection = direction)
    }

    override fun credits(style: ContentStyle) = """
@Body,@Content Style
111,Demo
222,
333,
444,
        """.parseCreditsCS(style)
}


object GuideContentStyleFlowLayoutJustifyLinesDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/line-hjustify", Format.STEP_GIF,
    listOf(ContentStyle::flowLineHJustify.st()), pageGuides = true
) {
    override fun styles() = buildList<ContentStyle> {
        for (justify in LineHJustify.entries)
            this += bulletsCS.copy(name = "Demo", flowLineHJustify = justify)
    }

    override fun credits(style: ContentStyle) = FLOW_SPREADSHEET.parseCreditsCS(style)
}


object GuideContentStyleFlowLayoutSquareCellsDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/square-cells", Format.STEP_GIF,
    listOf(ContentStyle::flowSquareCells.st()), pageGuides = true
) {
    override fun styles() = buildList<ContentStyle> {
        this += bulletsCS.copy(name = "Demo")
        this += last().copy(flowSquareCells = true)
    }

    override fun credits(style: ContentStyle) = """
@Body,@Content Style
Ada,Demo
Ben,
Claire,
Dan,
Eva,
        """.parseCreditsCS(style)
}


object GuideContentStyleFlowLayoutForceCellWidthAndHeightDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/force-cell-width-and-height", Format.STEP_GIF,
    listOf(ContentStyle::flowForceCellWidthPx.st(), ContentStyle::flowForceCellHeightPx.st()), pageGuides = true
) {
    override fun styles() = buildList<ContentStyle> {
        this += bulletsCS.copy(name = "Demo")
        this += last().copy(flowForceCellWidthPx = Opt(true, 150.0))
        this += last().copy(flowForceCellHeightPx = Opt(true, 70.0))
    }

    override fun credits(style: ContentStyle) = """
@Body,@Content Style
Ada,Demo
Ben,
Claire,
Dan,
Eva,
        """.parseCreditsCS(style)
}


object GuideContentStyleFlowLayoutMatchCellWidthAndHeightDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/match-cell-width-and-height", Format.SLOW_STEP_GIF,
    listOf(
        ContentStyle::flowMatchCellWidth.st(), ContentStyle::flowMatchCellWidthAcrossStyles.st(),
        ContentStyle::flowMatchCellHeight.st(), ContentStyle::flowMatchCellHeightAcrossStyles.st()
    ), pageGuides = true
) {
    private val altStyleName get() = l10n("project.template.contentStyleBullets") + " 2"

    override fun styles() = buildList<ContentStyle> {
        this += bulletsCS.copy(name = "Demo", flowLineWidthPx = 500.0)
        this += last().copy(flowMatchCellWidth = MatchExtent.WITHIN_BLOCK)
        this += last().copy(flowMatchCellWidth = MatchExtent.ACROSS_BLOCKS)
        this += last().copy(flowMatchCellWidthAcrossStyles = persistentListOf(altStyleName))
        this += last().copy(flowMatchCellHeight = MatchExtent.WITHIN_BLOCK)
        this += last().copy(flowMatchCellHeight = MatchExtent.ACROSS_BLOCKS)
        this += last().copy(flowMatchCellHeightAcrossStyles = persistentListOf(altStyleName))
    }

    override fun credits(style: ContentStyle) = """
@Body,@Vertical Gap,@Content Style
{{Pic logo.svg x50}},,Demo
Emil Eater,,
Megan Muncher,,
Dylan Delicious,,
Tiffany Tasty,,
Ben Broccoli,,
,,
Tom,,
Samuel,,
,2,
Wanda,,$altStyleName
Philipp,,
        """.parseCreditsCS(
        style,
        bulletsCS.copy(
            name = altStyleName, bodyLetterStyleName = "Small", flowMatchCellWidth = MatchExtent.ACROSS_BLOCKS,
            flowMatchCellHeight = MatchExtent.ACROSS_BLOCKS, flowLineWidthPx = 500.0
        )
    )
}


object GuideContentStyleFlowLayoutCellHJustifyAndVJustifyDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/cell-hjustify-and-vjustify", Format.STEP_GIF,
    listOf(ContentStyle::flowCellHJustify.st(), ContentStyle::flowCellVJustify.st()), pageGuides = true
) {
    override fun styles() = buildList<ContentStyle> {
        this += bulletsCS.copy(
            name = "Demo", flowMatchCellWidth = MatchExtent.WITHIN_BLOCK,
            flowCellHJustify = HJustify.LEFT, flowCellVJustify = VJustify.TOP
        )
        this += last().copy(flowCellHJustify = HJustify.CENTER)
        this += last().copy(flowCellHJustify = HJustify.RIGHT)
        this += last().copy(flowCellVJustify = VJustify.MIDDLE)
        this += last().copy(flowCellVJustify = VJustify.BOTTOM)
    }

    override fun credits(style: ContentStyle) = """
@Body,@Content Style
Emil Eater,Demo
{{Pic logo.svg x100}},
Dylan Delicious,
        """.parseCreditsCS(style)
}


object GuideContentStyleFlowLayoutLineWidthDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/line-width", Format.STEP_GIF,
    listOf(ContentStyle::flowLineWidthPx.st()), pageGuides = true
) {
    override fun styles() = buildList<ContentStyle> {
        this += bulletsCS.copy(name = "Demo")
        this += last().copy(flowLineWidthPx = 400.0)
    }

    override fun credits(style: ContentStyle) = FLOW_SPREADSHEET.parseCreditsCS(style)
}


object GuideContentStyleFlowLayoutLineGapAndHGapDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/line-gap-and-hgap", Format.STEP_GIF,
    listOf(ContentStyle::flowLineGapPx.st(), ContentStyle::flowHGapPx.st()), pageGuides = true
) {
    override fun styles() = buildList<ContentStyle> {
        this += bulletsCS.copy(name = "Demo")
        this += last().copy(flowLineGapPx = 32.0)
        this += last().copy(flowLineGapPx = 64.0)
        this += last().copy(flowHGapPx = 64.0)
    }

    override fun credits(style: ContentStyle) = FLOW_SPREADSHEET.parseCreditsCS(style)
}


object GuideContentStyleFlowLayoutSeparatorDemo : StyleSettingsDemo<ContentStyle>(
    ContentStyle::class.java, "$DIR/separator", Format.STEP_GIF,
    listOf(ContentStyle::flowSeparator.st()), pageGuides = true
) {
    override fun styles() = buildList<ContentStyle> {
        this += bulletsCS.copy(name = "Demo", flowSeparator = "")
        this += last().copy(flowSeparator = bulletsCS.flowSeparator)
        this += last().copy(flowSeparator = "\u2013")
    }

    override fun credits(style: ContentStyle) = FLOW_SPREADSHEET.parseCreditsCS(style)
}


private val bulletsCS = PRESET_CONTENT_STYLE.copy(
    bodyLetterStyleName = "Name", bodyLayout = BodyLayout.FLOW, flowLineWidthPx = 600.0, flowHGapPx = 32.0
)

private const val FLOW_SPREADSHEET = """
@Body,@Content Style
Emil Eater,Demo
Megan Muncher,
Dylan Delicious,
Tiffany Tasty,
Samuel Saturated,
Thomas Thirsty,
Wanda Waters,
Philip “Pork” Chop,
Ben Broccoli,
"""
