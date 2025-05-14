package com.tapbi.spark.gpsmappro.ui.main

import android.os.Bundle
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingActivity
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.ActivityMainBinding

class MainActivity : BaseBindingActivity<ActivityMainBinding, MainViewModel>() {

    override val layoutId: Int
        get() = R.layout.activity_main

    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override fun setupView(savedInstanceState: Bundle?) {

    }

    override fun setupData() {

    }


}