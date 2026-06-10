package com.projeto.epubreader.ui.reader

import android.content.Context

class ReaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

    fun saveTheme(backgroundColor: String, textColor: String, name: String) {
        prefs.edit()
            .putString("bg_color", backgroundColor)
            .putString("text_color", textColor)
            .putString("theme_name", name)
            .apply()
    }

    fun loadTheme(): Triple<String, String, String> {
        return Triple(
            prefs.getString("bg_color", "#FFFFFF") ?: "#FFFFFF",
            prefs.getString("text_color", "#222222") ?: "#222222",
            prefs.getString("theme_name", "Padrão") ?: "Padrão"
        )
    }

    fun saveFont(fontName: String) {
        prefs.edit().putString("font_name", fontName).apply()
    }

    fun loadFont(): String {
        return prefs.getString("font_name", "RobotoSlab") ?: "RobotoSlab"
    }

    fun saveFontSize(size: Int) {
        prefs.edit().putInt("font_size", size).apply()
    }

    fun loadFontSize(): Int {
        return prefs.getInt("font_size", 18)
    }
}