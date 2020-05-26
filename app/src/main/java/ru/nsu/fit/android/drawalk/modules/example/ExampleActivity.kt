package ru.nsu.fit.android.drawalk.modules.example

import android.os.Bundle
import androidx.fragment.app.Fragment
import ru.nsu.ccfit.molochev.android.rssample.modules.base.SingleFragmentActivity
import ru.nsu.fit.android.drawalk.databinding.ActivityExampleBinding

class ExampleActivity: SingleFragmentActivity() {
    private var counter = 0

    private lateinit var binding: ActivityExampleBinding

    override fun createFragment() = ExampleFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExampleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val view = fragment as IExampleFragment
        val presenter = ExamplePresenter(view)
        view.presenter = presenter

        binding.cancellable.setOnClickListener {
            presenter.calcWithCancel(counter++.toString())
        }
        binding.repeateble.setOnClickListener {
            presenter.calcWithoutCancel(counter++.toString())
        }
    }
}