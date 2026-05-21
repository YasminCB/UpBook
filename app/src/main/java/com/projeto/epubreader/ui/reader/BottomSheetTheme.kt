package com.projeto.epubreader.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.projeto.epubreader.databinding.BottomSheetThemeBinding

class ThemeBottomSheet(
    private val currentTheme: ReaderViewModel.ReaderTheme,
    private val onThemeSelected: (ReaderViewModel.ReaderTheme) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetThemeBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetThemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val presets = listOf(
            ReaderViewModel.ReaderTheme.DEFAULT,
            ReaderViewModel.ReaderTheme.SEPIA,
            ReaderViewModel.ReaderTheme.DARK,
            ReaderViewModel.ReaderTheme.AMOLED,
            ReaderViewModel.ReaderTheme.GREEN
        )

        // Botões de preset
        val buttons = listOf(
            binding.btnThemeDefault,
            binding.btnThemeSepia,
            binding.btnThemeDark,
            binding.btnThemeAmoled,
            binding.btnThemeGreen
        )

        buttons.forEachIndexed { i, btn ->
            btn.text = presets[i].name
            btn.setBackgroundColor(android.graphics.Color.parseColor(presets[i].backgroundColor))
            btn.setTextColor(android.graphics.Color.parseColor(presets[i].textColor))
            btn.setOnClickListener {
                onThemeSelected(presets[i])
                dismiss()
            }
        }

        // SeekBars RGB para cor personalizada de fundo
        setupCustomColor()
    }

    private var bgRed = 255; private var bgGreen = 255; private var bgBlue = 255
    private var fgRed = 34;  private var fgGreen = 34;  private var fgBlue = 34

    private fun setupCustomColor() {
        fun updatePreview() {
            val bg = String.format("#%02X%02X%02X", bgRed, bgGreen, bgBlue)
            val fg = String.format("#%02X%02X%02X", fgRed, fgGreen, fgBlue)
            binding.previewText.setBackgroundColor(android.graphics.Color.parseColor(bg))
            binding.previewText.setTextColor(android.graphics.Color.parseColor(fg))
        }

        fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { onChange(p); updatePreview() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }

        binding.seekBgR.setOnSeekBarChangeListener(seekListener { bgRed = it })
        binding.seekBgG.setOnSeekBarChangeListener(seekListener { bgGreen = it })
        binding.seekBgB.setOnSeekBarChangeListener(seekListener { bgBlue = it })
        binding.seekFgR.setOnSeekBarChangeListener(seekListener { fgRed = it })
        binding.seekFgG.setOnSeekBarChangeListener(seekListener { fgGreen = it })
        binding.seekFgB.setOnSeekBarChangeListener(seekListener { fgBlue = it })

        binding.btnApplyCustom.setOnClickListener {
            val bg = String.format("#%02X%02X%02X", bgRed, bgGreen, bgBlue)
            val fg = String.format("#%02X%02X%02X", fgRed, fgGreen, fgBlue)
            onThemeSelected(ReaderViewModel.ReaderTheme(bg, fg, "Personalizado"))
            dismiss()
        }

        updatePreview()
    }
}