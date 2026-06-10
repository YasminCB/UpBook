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
    private val currentFont: String,
    private val currentFontSize: Int,
    private val onThemeSelected: (ReaderViewModel.ReaderTheme) -> Unit,
    private val onFontSelected: (String) -> Unit,
    private val onFontSizeSelected: (Int) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetThemeBinding
    private val fonts = listOf("RobotoSlab", "Inter", "Literata", "Merriweather", "OpenSans")

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
            ReaderViewModel.ReaderTheme.GREEN
        )

        val buttons = listOf(
            binding.btnThemeDefault,
            binding.btnThemeSepia,
            binding.btnThemeDark,
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

        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            fonts
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerFont.adapter = adapter
        binding.spinnerFont.setSelection(fonts.indexOf(currentFont).takeIf { it >= 0 } ?: 0)

        binding.spinnerFont.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                onFontSelected(fonts[position])
                updatePreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        binding.seekFontSize.progress = currentFontSize
        binding.seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                onFontSizeSelected(p)
                updatePreview()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        setupCustomColor()
    }

    private var bgRed = 255; private var bgGreen = 255; private var bgBlue = 255
    private var fgRed = 34;  private var fgGreen = 34;  private var fgBlue = 34

    private fun updatePreview() {
        val bg = String.format("#%02X%02X%02X", bgRed, bgGreen, bgBlue)
        val fg = String.format("#%02X%02X%02X", fgRed, fgGreen, fgBlue)
        binding.previewText.setBackgroundColor(android.graphics.Color.parseColor(bg))
        binding.previewText.setTextColor(android.graphics.Color.parseColor(fg))

        val selectedFont = fonts[binding.spinnerFont.selectedItemPosition]
        val typeface = try {
            when (selectedFont) {
                "Inter" -> android.graphics.Typeface.createFromAsset(requireContext().assets, "fonts/inter.ttf")
                "Literata" -> android.graphics.Typeface.createFromAsset(requireContext().assets, "fonts/literata.ttf")
                "Merriweather" -> android.graphics.Typeface.createFromAsset(requireContext().assets, "fonts/merriweather.ttf")
                "OpenSans" -> android.graphics.Typeface.createFromAsset(requireContext().assets, "fonts/opensans.ttf")
                "RobotoSlab" -> android.graphics.Typeface.createFromAsset(requireContext().assets, "fonts/robotoslab.ttf")
                else -> android.graphics.Typeface.DEFAULT
            }
        } catch (e: Exception) {
            android.graphics.Typeface.DEFAULT
        }
        binding.previewText.typeface = typeface
        binding.previewText.textSize = binding.seekFontSize.progress.toFloat()
    }

    private fun setupCustomColor() {
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