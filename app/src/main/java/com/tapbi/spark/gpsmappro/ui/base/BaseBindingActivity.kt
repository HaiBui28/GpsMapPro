package com.tapbi.spark.gpsmappro.ui.base

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider

abstract class BaseBindingActivity<B : ViewDataBinding?, VM : BaseViewModel> :
    BaseActivity() {
    var binding: B? = null
    var viewModel: VM? = null
    abstract val layoutId: Int

    abstract fun getViewModel(): Class<VM>
    abstract fun setupView(savedInstanceState: Bundle?)
    abstract fun setupData()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, layoutId)
        viewModel = ViewModelProvider(this)[getViewModel()]
        setupView(savedInstanceState)
        setupData()
    }

}