package com.projeto.epubreader.ui.theme

import android.content.Context
import com.projeto.epubreader.R

object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_DARK = "is_dark"

    fun isDark(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, true)
    }

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
    }

    fun getThemeRes(context: Context): Int {
        return if (isDark(context)) R.style.Theme_EpubReader_Dark
        else R.style.Theme_EpubReader_Light
    }
}