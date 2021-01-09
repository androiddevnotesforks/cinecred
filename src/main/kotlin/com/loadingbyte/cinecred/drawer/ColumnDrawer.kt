package com.loadingbyte.cinecred.drawer

import com.loadingbyte.cinecred.common.DeferredImage
import com.loadingbyte.cinecred.common.RichFont
import com.loadingbyte.cinecred.common.getStringWidth
import com.loadingbyte.cinecred.project.*
import com.loadingbyte.cinecred.project.AlignWithAxis.*
import kotlin.math.max
import kotlin.math.min


fun drawColumnImage(
    fonts: Map<FontSpec, RichFont>,
    column: Column,
    alignBodyColsGroupIds: Map<Block, Int>,
    alignHeadTailGroupIds: Map<Block, Int>
): Pair<DeferredImage, Float> {
    // This list will be filled shortly. We generate an image for each block's body.
    val bodyImages = mutableMapOf<Block, DeferredImage>()

    // Step 1:
    // Take the blocks whose bodies are laid out using the "grid body layout". Group blocks that share the same
    // content style and user-defined "body columns alignment group". The "body columns" will be aligned between
    // blocks from the same group.
    val blockGroupsWithGridBodyLayout = column.blocks
        .filter { block -> block.style.bodyLayout == BodyLayout.GRID }
        .groupBy { block -> Pair(block.style, alignBodyColsGroupIds[block]) }
        .values
    // Generate images for blocks whose bodies are laid out using the "grid body layout".
    for (blockGroup in blockGroupsWithGridBodyLayout) {
        // Generate an image for the body of each block in the group. The bodies are laid out together such that,
        // for example, a "left" justification means "left" w.r.t. to the column spanned up by the widest body from
        // the block group. As a consequence, all these images also share the same width.
        bodyImages.putAll(drawBodyImagesWithGridBodyLayout(fonts, blockGroup))
    }

    // Step 2:
    // Generate images for blocks whose bodies are laid out using the "flow body layout" or "paragraphs body layout".
    for (block in column.blocks)
        if (block.style.bodyLayout == BodyLayout.FLOW)
            bodyImages[block] = drawBodyImageWithFlowBodyLayout(fonts, block)
        else if (block.style.bodyLayout == BodyLayout.PARAGRAPHS)
            bodyImages[block] = drawBodyImageWithParagraphsBodyLayout(fonts, block)

    // We now add heads and tails to the body images and thereby generate an image for each block.
    // We also remember the x coordinate of the axis inside each generated image.
    val blockImagesWithAxisXs = mutableMapOf<Block, Pair<DeferredImage, Float>>()

    // Step 3:
    // We start with the blocks that have a horizontal spine. Here, heads/tails that either share a common edge
    // position or are part of blocks which are centered on the head/tail are combined into shared "head/tail columns".
    // Such a "column" is as wide as the largest head/tail it contains. This, for example, allows the user to justify
    // all heads "left" in a meaningful way.
    // First, partition the horizontal spine blocks into two partitions that will be processed separately.
    val (horSpineBlocks1, horSpineBlocks2) = column.blocks
        .filter { block -> block.style.spineOrientation == SpineOrientation.HORIZONTAL }
        .partition { block ->
            val c = block.style.alignWithAxis
            !(c == HEAD_GAP_CENTER || c == BODY_LEFT || c == BODY_RIGHT || c == TAIL_GAP_CENTER)
        }
    // Divide the first partition such that only blocks whose heads or tails should be aligned are in the same group.
    val headOrTailAlignBlockGroups1 = horSpineBlocks1
        .groupBy { block ->
            when (block.style.alignWithAxis) {
                HEAD_LEFT, HEAD_CENTER, HEAD_RIGHT, TAIL_LEFT, TAIL_CENTER, TAIL_RIGHT ->
                    Pair(block.style.alignWithAxis, alignHeadTailGroupIds[block])
                // The heads or tails of these blocks are never aligned. As such, we use the memory address of these
                // blocks as their group keys to make sure that each of them is always sorted into a singleton group.
                OVERALL_CENTER, BODY_CENTER -> System.identityHashCode(block)
                else -> throw IllegalStateException()  // Will never happen because we partitioned beforehand.
            }
        }.values
    // Now process the second partition.
    val headOrTailAlignBlockGroups2 = horSpineBlocks2
        // Divide into "left"-centered and "right"-centered blocks. Also divide by head/tail aligning group.
        .groupBy { block ->
            val c = block.style.alignWithAxis
            Pair(c == HEAD_GAP_CENTER || c == BODY_LEFT, alignHeadTailGroupIds[block])
        }.values
        // Further subdivide such that only blocks whose heads or tails share an edge are in the same group.
        .flatMap { blockGroup ->
            blockGroup.fuzzyGroupBy { block ->
                when (block.style.alignWithAxis) {
                    HEAD_GAP_CENTER -> block.style.headGapPx / 2f
                    BODY_LEFT -> block.style.headGapPx
                    BODY_RIGHT -> block.style.tailGapPx
                    TAIL_GAP_CENTER -> block.style.tailGapPx / 2f
                    else -> throw IllegalStateException()  // Will never happen because we partitioned beforehand.
                }
            }
        }
    // Finally, generate block images for all horizontal blocks. The images for grouped blocks are generated in unison.
    for (blockGroup in headOrTailAlignBlockGroups1 + headOrTailAlignBlockGroups2)
        blockImagesWithAxisXs.putAll(drawHorizontalSpineBlockImages(fonts, blockGroup, bodyImages))

    // Step 4: Now generate block images for the blocks which have a vertical spine.
    for (block in column.blocks)
        if (block.style.spineOrientation == SpineOrientation.VERTICAL)
            blockImagesWithAxisXs[block] = drawVerticalSpineBlockImage(fonts, block, bodyImages[block]!!)

    // Step 5:
    // Combine the block images for the blocks inside the column to a column image.
    val columnImage = DeferredImage()
    val axisXInColumnImage = column.blocks.minOf { block -> blockImagesWithAxisXs[block]!!.second }
    var y = 0f
    for (block in column.blocks) {
        y += block.style.vMarginPx
        val (blockImage, axisXInBlockImage) = blockImagesWithAxisXs[block]!!
        val x = axisXInColumnImage - axisXInBlockImage
        columnImage.drawDeferredImage(blockImage, x, y, 1f)
        y += blockImage.height + block.style.vMarginPx + block.vGapAfterPx
    }
    // Draw a guide that shows the column's axis.
    columnImage.drawLine(AXIS_GUIDE_COLOR, axisXInColumnImage, 0f, axisXInColumnImage, y, isGuide = true)

    return Pair(columnImage, axisXInColumnImage)
}


private const val EPS = 0.01f

// We cannot use regular groupBy for floats as floating point inaccuracy might cause them to differ ever so little
// even though logically, they are the same.
private inline fun <E> List<E>.fuzzyGroupBy(keySelector: (E) -> Float): List<List<E>> {
    val groups = mutableListOf<List<E>>()

    val ungrouped = toMutableList()
    while (ungrouped.isNotEmpty()) {
        val startElem = ungrouped.removeAt(0)
        val startElemKey = keySelector(startElem)
        var lower = startElemKey - EPS
        var upper = startElemKey + EPS

        val group = mutableListOf(startElem)
        var groupHasGrown = true
        while (groupHasGrown) {
            groupHasGrown = false
            val iter = ungrouped.iterator()
            while (iter.hasNext()) {
                val elem = iter.next()
                val elemKey = keySelector(elem)
                if (elemKey in lower..upper) {
                    group.add(elem)
                    iter.remove()
                    lower = min(lower, elemKey - EPS)
                    upper = max(upper, elemKey + EPS)
                    groupHasGrown = true
                }
            }
        }

        groups.add(group)
    }

    return groups
}


private fun drawHorizontalSpineBlockImages(
    fonts: Map<FontSpec, RichFont>,
    blocks: List<Block>,
    bodyImages: Map<Block, DeferredImage>,
): Map<Block, Pair<DeferredImage, Float>> {
    // This will be the return value.
    val blockImagesWithAxisXs = mutableMapOf<Block, Pair<DeferredImage, Float>>()

    // Step 1:
    // In the drawColumnImage() function, the blocks have been grouped such that in this function, either the heads or
    // tails or nothing should be contained in a merged single-width "column". For this, depending on what should be
    // aligned with the axis, we first determine the width of the head sub-column or the tail sub-column or nothing by
    // taking the maximum width of all head/tail strings coming from all blocks from the block group.
    val headSharedWidth = when (blocks[0].style.alignWithAxis) {
        HEAD_LEFT, HEAD_CENTER, HEAD_RIGHT, HEAD_GAP_CENTER, BODY_LEFT ->
            blocks.maxOf { block ->
                if (block.style.spineOrientation == SpineOrientation.VERTICAL || block.head == null) 0f
                else fonts[block.style.headFontSpec]!!.awt.getStringWidth(block.head)
            }
        else -> null
    }
    val tailSharedWidth = when (blocks[0].style.alignWithAxis) {
        BODY_RIGHT, TAIL_GAP_CENTER, TAIL_LEFT, TAIL_CENTER, TAIL_RIGHT ->
            blocks.maxOf { block ->
                if (block.style.spineOrientation == SpineOrientation.VERTICAL || block.tail == null) 0f
                else fonts[block.style.tailFontSpec]!!.awt.getStringWidth(block.tail)
            }
        else -> null
    }

    // Step 2:
    // Draw a deferred image for each block.
    for (block in blocks) {
        val bodyImage = bodyImages[block]!!
        val headFont = fonts[block.style.headFontSpec]!!
        val tailFont = fonts[block.style.tailFontSpec]!!

        val headWidth = headSharedWidth ?: block.head?.let { headFont.awt.getStringWidth(it) } ?: 0f
        val tailWidth = tailSharedWidth ?: block.tail?.let { tailFont.awt.getStringWidth(it) } ?: 0f

        val headStartX = 0f
        val headEndX = headStartX + headWidth
        val bodyStartX = headEndX + (if (headWidth == 0f) 0f else block.style.headGapPx)
        val bodyEndX = bodyStartX + bodyImage.width
        val tailStartX = bodyEndX + (if (tailWidth == 0f) 0f else block.style.tailGapPx)
        val tailEndX = tailStartX + tailWidth

        // Draw the block image.
        val blockImage = DeferredImage()
        var y = 0f
        // Draw the block's head.
        if (block.head != null) {
            blockImage.drawJustifiedString(
                headFont, block.style.bodyFontSpec, block.head, block.style.headHJustify, block.style.headVJustify,
                0f, y, headWidth, bodyImage.height
            )
            // Draw a guide that shows the edges of the head space.
            blockImage.drawRect(HEAD_TAIL_GUIDE_COLOR, 0f, y, headWidth, bodyImage.height, isGuide = true)
        }
        // Draw the block's body.
        blockImage.drawDeferredImage(bodyImage, bodyStartX, y, 1f)
        if (block.style.spineOrientation == SpineOrientation.VERTICAL)
            y += bodyImage.height
        // Draw the block's tail.
        if (block.tail != null) {
            blockImage.drawJustifiedString(
                tailFont, block.style.bodyFontSpec, block.tail, block.style.tailHJustify, block.style.tailVJustify,
                tailStartX, y, tailWidth, bodyImage.height
            )
            // Draw a guide that shows the edges of the tail space.
            blockImage.drawRect(HEAD_TAIL_GUIDE_COLOR, tailStartX, y, tailWidth, bodyImage.height, isGuide = true)
        }

        // Find the x coordinate of the axis in the generated image for the current block.
        val axisXInImage = when (block.style.alignWithAxis) {
            OVERALL_CENTER -> (headStartX + tailEndX) / 2f
            HEAD_LEFT -> headStartX
            HEAD_CENTER -> (headStartX + headEndX) / 2f
            HEAD_RIGHT -> headEndX
            HEAD_GAP_CENTER -> (headEndX + bodyStartX) / 2f
            BODY_LEFT -> bodyStartX
            BODY_CENTER -> (bodyStartX + bodyEndX) / 2f
            BODY_RIGHT -> bodyEndX
            TAIL_GAP_CENTER -> (bodyEndX + tailStartX) / 2f
            TAIL_LEFT -> tailStartX
            TAIL_CENTER -> (tailStartX + tailEndX) / 2f
            TAIL_RIGHT -> tailEndX
        }

        blockImagesWithAxisXs[block] = Pair(blockImage, axisXInImage)
    }

    return blockImagesWithAxisXs
}


private fun drawVerticalSpineBlockImage(
    fonts: Map<FontSpec, RichFont>,
    block: Block,
    bodyImage: DeferredImage
): Pair<DeferredImage, Float> {
    val headFont = fonts[block.style.headFontSpec]!!
    val tailFont = fonts[block.style.tailFontSpec]!!

    // Will store the start and end x coordinates of the head resp. tail if it exists.
    var headXs = Pair(0f, 0f)
    var tailXs = Pair(0f, 0f)

    // Draw the body image.
    val blockImage = DeferredImage()
    var y = 0f
    // Draw the block's head.
    if (block.head != null) {
        headXs = blockImage.drawJustifiedString(headFont, block.head, block.style.headHJustify, 0f, y, bodyImage.width)
        // Draw guides that show the edges of the head space.
        val y2 = blockImage.height
        blockImage.drawLine(HEAD_TAIL_GUIDE_COLOR, 0f, 0f, 0f, y2, isGuide = true)
        blockImage.drawLine(HEAD_TAIL_GUIDE_COLOR, bodyImage.width, 0f, bodyImage.width, y2, isGuide = true)
        // Advance to the body.
        y += headFont.spec.heightPx + block.style.headGapPx
    }
    // Draw the block's body.
    blockImage.drawDeferredImage(bodyImage, 0f, y, 1f)
    y += bodyImage.height
    // Draw the block's tail.
    if (block.tail != null) {
        y += block.style.tailGapPx
        tailXs = blockImage.drawJustifiedString(tailFont, block.tail, block.style.tailHJustify, 0f, y, bodyImage.width)
        // Draw guides that show the edges of the tail space.
        val y2 = blockImage.height
        blockImage.drawLine(HEAD_TAIL_GUIDE_COLOR, 0f, y, 0f, y2, isGuide = true)
        blockImage.drawLine(HEAD_TAIL_GUIDE_COLOR, bodyImage.width, y, bodyImage.width, y2, isGuide = true)
    }

    // Find the x coordinate of the axis in the generated image for the current block.
    val axisXInImage = when (block.style.alignWithAxis) {
        BODY_LEFT -> 0f
        OVERALL_CENTER, HEAD_GAP_CENTER, BODY_CENTER, TAIL_GAP_CENTER -> bodyImage.width / 2f
        BODY_RIGHT -> bodyImage.width
        HEAD_LEFT -> headXs.first
        HEAD_CENTER -> (headXs.first + headXs.second) / 2f
        HEAD_RIGHT -> headXs.second
        TAIL_LEFT -> tailXs.first
        TAIL_CENTER -> (tailXs.first + tailXs.second) / 2f
        TAIL_RIGHT -> tailXs.second
    }

    return Pair(blockImage, axisXInImage)
}
