package ru.nsu.fit.android.drawalk.modules.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ru.nsu.fit.android.drawalk.databinding.FragmentExampleBinding

class ExampleFragment: IExampleFragment() {
    private lateinit var binding: FragmentExampleBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentExampleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun setExampleText(text: String) {
        binding.mainText.text = text
    }
}