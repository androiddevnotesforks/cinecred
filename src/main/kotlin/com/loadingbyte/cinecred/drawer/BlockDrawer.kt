package com.loadingbyte.cinecred.drawer

import com.loadingbyte.cinecred.common.DeferredImage
import com.loadingbyte.cinecred.common.DeferredImage.Companion.GUIDES
import com.loadingbyte.cinecred.common.Y.Companion.plus
import com.loadingbyte.cinecred.common.Y.Companion.toElasticY
import com.loadingbyte.cinecred.common.Y.Companion.toY
import com.loadingbyte.cinecred.project.Block
import com.loadingbyte.cinecred.project.BlockOrientation.HORIZONTAL
import com.loadingbyte.cinecred.project.BlockOrientation.VERTICAL
import com.loadingbyte.cinecred.project.ContentStyle
import com.loadingbyte.cinecred.project.MatchExtent.ACROSS_BLOCKS
import com.loadingbyte.cinecred.project.PartitionId
import com.loadingbyte.cinecred.project.SpineAttachment.*
import com.loadingbyte.cinecred.project.VJustify


class DrawnBlock(val defImage: DeferredImage, val spineXInImage: Float)


fun drawBlocks(
    contentStyles: List<ContentStyle>,
    textCtx: TextContext,
    drawnBodies: Map<Block, DrawnBody>,
    blocks: List<Block>
): Map<Block, DrawnBlock> {
    val drawnBlocks = HashMap<Block, DrawnBlock>(2 * blocks.size)

    // Draw blocks which have horizontal orientation.
    drawHorizontalBlocks(
        drawnBlocks, contentStyles, textCtx, drawnBodies,
        blocks.filter { block -> block.style.blockOrientation == HORIZONTAL }
    )

    // Draw blocks which have vertical orientation.
    for (block in blocks)
        if (block.style.blockOrientation == VERTICAL)
            drawnBlocks[block] = drawVerticalBlock(textCtx, block, drawnBodies.getValue(block))

    return drawnBlocks
}


private fun drawHorizontalBlocks(
    out: MutableMap<Block, DrawnBlock>,
    cs: List<ContentStyle>,
    textCtx: TextContext,
    drawnBodies: Map<Block, DrawnBody>,
    blocks: List<Block>
) {
    // In this function, we only concern ourselves with blocks which have horizontal orientation.
    require(blocks.all { it.style.blockOrientation == HORIZONTAL })

    // Horizontal blocks are free to potentially harmonize their head and tail width. This, for example, could allow the
    // user to justify all heads "left" in a meaningful way. For both widths that can be harmonized (i.e., head and tail
    // width), find which styles should harmonize together.
    val matchHeadWidthPartitionIds = partitionToTransitiveClosures(cs, ContentStyle::headMatchWidthAcrossStyles) {
        blockOrientation == HORIZONTAL && headMatchWidth == ACROSS_BLOCKS
    }
    val matchTailWidthPartitionIds = partitionToTransitiveClosures(cs, ContentStyle::tailMatchWidthAcrossStyles) {
        blockOrientation == HORIZONTAL && tailMatchWidth == ACROSS_BLOCKS
    }

    // Determine the groups of blocks which should share the same head/tail width, and of course also find those widths.
    val sharedHeadWidths = matchWidth(blocks, matchHeadWidthPartitionIds, Block::matchHeadPartitionId) { group ->
        group.maxOf { block -> if (block.head == null) 0f else block.head.formatted(textCtx).width }
    }
    val sharedTailWidths = matchWidth(blocks, matchTailWidthPartitionIds, Block::matchTailPartitionId) { group ->
        group.maxOf { block -> if (block.tail == null) 0f else block.tail.formatted(textCtx).width }
    }

    // Draw a deferred image for each block.
    blocks.associateWithTo(out) { block ->
        val drawnBody = drawnBodies.getValue(block)
        val bodyImage = drawnBody.defImage

        val headWidth = sharedHeadWidths[block] ?: block.head?.run { formatted(textCtx).width } ?: 0f
        val tailWidth = sharedTailWidths[block] ?: block.tail?.run { formatted(textCtx).width } ?: 0f

        val headStartX = 0f
        val headEndX = headStartX + headWidth
        val bodyStartX = headEndX + (if (!block.style.hasHead) 0f else block.style.headGapPx)
        val bodyEndX = bodyStartX + bodyImage.width
        val tailStartX = bodyEndX + (if (!block.style.hasHead) 0f else block.style.tailGapPx)
        val tailEndX = tailStartX + tailWidth

        // Used later on for vertically justifying the head and tail.
        fun getReferenceHeight(vJustify: VJustify) =
            when (vJustify) {
                VJustify.TOP -> drawnBody.firstRowHeight.toY()
                VJustify.MIDDLE -> null
                VJustify.BOTTOM -> drawnBody.lastRowHeight.toY()
            }

        // Draw the block image.
        val blockImageHeight = bodyImage.height
        val blockImage = DeferredImage(width = tailEndX - headStartX, height = blockImageHeight)
        // Draw the block's head.
        if (block.head != null) {
            blockImage.drawJustifiedString(
                block.head.formatted(textCtx), block.style.headHJustify, block.style.headVJustify,
                headStartX, 0f.toY(), headWidth, blockImageHeight, getReferenceHeight(block.style.headVJustify)
            )
            // Draw a guide that shows the edges of the head space.
            blockImage.drawRect(
                HEAD_TAIL_GUIDE_COLOR, headStartX, 0f.toY(), headWidth, blockImageHeight, layer = GUIDES
            )
        }
        // Draw the block's body.
        blockImage.drawDeferredImage(bodyImage, bodyStartX, 0f.toY())
        // Draw the block's tail.
        if (block.tail != null) {
            blockImage.drawJustifiedString(
                block.tail.formatted(textCtx), block.style.tailHJustify, block.style.tailVJustify,
                tailStartX, 0f.toY(), tailWidth, blockImageHeight, getReferenceHeight(block.style.tailVJustify)
            )
            // Draw a guide that shows the edges of the tail space.
            blockImage.drawRect(
                HEAD_TAIL_GUIDE_COLOR, tailStartX, 0f.toY(), tailWidth, blockImageHeight, layer = GUIDES
            )
        }

        // Find the x coordinate of the spine in the generated image for the current block.
        val spineXInImage = when (block.style.spineAttachment) {
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

        DrawnBlock(blockImage, spineXInImage)
    }
}


private inline fun matchWidth(
    blocks: List<Block>,
    styleMatchPartitionIds: Map<ContentStyle, PartitionId>,
    blockMatchPartitionId: (Block) -> PartitionId,
    sharedGroupWidth: (List<Block>) -> Float
): Map<Block, Float> {
    val grouper = HashMap<Any, MutableList<Block>>()
    for (block in blocks) {
        // If the block's style is in some partition (!= null), matching the head/tail width across blocks is enabled
        // for that style. Hence, group the block according to both (a) the style's partition and (b) the global
        // head/tail match partition which arises from the "@Break Match" column in the credits table.
        val key = Pair(styleMatchPartitionIds[block.style] ?: continue, blockMatchPartitionId(block))
        grouper.computeIfAbsent(key) { ArrayList() }.add(block)
    }
    // Now that the grouping is done, record the shared extent of each group.
    val groupWidths = HashMap<Block, Float>(2 * blocks.size)
    for (group in grouper.values) {
        val width = sharedGroupWidth(group)
        group.associateWithTo(groupWidths) { width }
    }
    return groupWidths
}


private fun drawVerticalBlock(
    textCtx: TextContext,
    block: Block,
    drawnBody: DrawnBody
): DrawnBlock {
    // In this function, we only concern ourselves with blocks which have vertical orientation.
    require(block.style.blockOrientation == VERTICAL)

    val bodyImage = drawnBody.defImage

    // Draw the body image.
    val blockImageWidth = bodyImage.width
    val blockImage = DeferredImage(blockImageWidth)
    var y = 0f.toY()
    // Draw the block's head.
    if (block.head != null) {
        blockImage.drawJustifiedString(block.head.formatted(textCtx), block.style.headHJustify, 0f, y, blockImageWidth)
        // Draw guides that show the edges of the head space.
        val headHeight = block.head.height.toFloat()
        blockImage.drawLine(HEAD_TAIL_GUIDE_COLOR, 0f, y, 0f, y + headHeight, layer = GUIDES)
        blockImage.drawLine(HEAD_TAIL_GUIDE_COLOR, blockImageWidth, y, blockImageWidth, y + headHeight, layer = GUIDES)
        // Advance to the body.
        y += headHeight + block.style.headGapPx.toElasticY()
    }
    // Draw the block's body.
    blockImage.drawDeferredImage(bodyImage, 0f, y)
    y += bodyImage.height
    // Draw the block's tail.
    if (block.tail != null) {
        y += block.style.tailGapPx.toElasticY()
        blockImage.drawJustifiedString(block.tail.formatted(textCtx), block.style.tailHJustify, 0f, y, blockImageWidth)
        // Draw guides that show the edges of the tail space.
        val tailHeight = block.tail.height.toFloat()
        blockImage.drawLine(HEAD_TAIL_GUIDE_COLOR, 0f, y, 0f, y + tailHeight, layer = GUIDES)
        blockImage.drawLine(HEAD_TAIL_GUIDE_COLOR, blockImageWidth, y, blockImageWidth, y + tailHeight, layer = GUIDES)
        // Advance to below the tail.
        y += tailHeight
    }
    blockImage.height = y

    // Find the x coordinate of the spine in the generated image for the current block.
    val spineXInImage = when (block.style.spineAttachment) {
        HEAD_LEFT, BODY_LEFT, TAIL_LEFT -> 0f
        OVERALL_CENTER, HEAD_CENTER, HEAD_GAP_CENTER, BODY_CENTER, TAIL_GAP_CENTER, TAIL_CENTER -> bodyImage.width / 2f
        HEAD_RIGHT, BODY_RIGHT, TAIL_RIGHT -> bodyImage.width
    }

    return DrawnBlock(blockImage, spineXInImage)
}
