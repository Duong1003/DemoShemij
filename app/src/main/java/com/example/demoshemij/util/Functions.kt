package com.example.demoshemij.util

import com.stevdza_san.sprite.domain.ScreenCategory

internal fun Float.parseCategory(): ScreenCategory {
    return when (this) {
        in 0f..360f -> ScreenCategory.Small
        in 360f..600f -> ScreenCategory.Normal
        in 600f..800f -> ScreenCategory.Large
        else -> ScreenCategory.Tablet
    }
}