package com.loadingbyte.cinecred.delivery

import com.loadingbyte.cinecred.drawer.DeferredImage
import com.loadingbyte.cinecred.project.PageBehavior
import com.loadingbyte.cinecred.project.Project
import kotlin.math.floor
import kotlin.math.round


fun getDurationFrames(project: Project, pageDefImages: List<DeferredImage>) =
    project.pages.zip(pageDefImages).sumBy { (page, pageDefImage) ->
        when (page.style.behavior) {
            PageBehavior.CARD ->
                page.style.cardDurationFrames + page.style.cardFadeInFrames + page.style.cardFadeOutFrames
            PageBehavior.SCROLL ->
                floor((project.styling.global.heightPx + round(pageDefImage.height)) / page.style.scrollPxPerFrame).toInt()
        }
    } + project.pages.dropLast(1).sumBy { page -> page.style.afterwardSlugFrames } + 1
