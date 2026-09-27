package com.kairosera.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The night "stage" used for brand moments that are always dark, whatever the app theme:
 * onboarding, the splash and the daily quote. Deep navy, warm cream text, a sunrise gold accent
 * and a soft blue for secondary accents. Screens read these names, never raw hex.
 */
object Stage {
    val night0 = Color(0xFF0D1729) // page
    val night1 = Color(0xFF111C31) // raised page areas
    val night2 = Color(0xFF16233D) // cards
    val night3 = Color(0xFF1E2E4F) // selected cards, inputs in focus
    val line = Color(0xFF2A3A5C)

    val cream = Color(0xFFF4EFE5)
    val muted = Color(0xFFAAB4C7)

    val gold = Color(0xFFF3C96A)
    val goldDeep = Color(0xFFE3AA4E)
    val goldMuted = Color(0xFF8C7646)
    val onGold = Color(0xFF1A2233)

    val blue = Color(0xFF6D8EDB)
    val blueMuted = Color(0xFF2D3B5C)

    /** Accent per focus area and feature, softened for dark surfaces. */
    val plan = gold
    val learn = Color(0xFFB2A4F2)
    val track = blue
    val reflect = Color(0xFF86C9A0)
    val health = Color(0xFFEBA46A)
    val read = Color(0xFFE8919F)
    val goals = Color(0xFF93A7F2)

    /** Notification preview (drawn as the system would show it, light). */
    val sheet = Color(0xFFF2EEE6)
    val sheetInk = Color(0xFF1A2233)
    val sheetMuted = Color(0xFF5B6170)
    val done = Color(0xFF2F7A55)
    val snooze = Color(0xFF3A5A9B)

    val goldBrush: Brush get() = Brush.horizontalGradient(listOf(gold, goldDeep))
}
