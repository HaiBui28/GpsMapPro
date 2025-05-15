package com.tapbi.spark.gpsmappro.ui.main

import android.os.Bundle
import com.tapbi.spark.gpsmappro.App
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingActivity
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.ActivityMainBinding
import com.tapbi.spark.gpsmappro.utils.Utils

class MainActivity : BaseBindingActivity<ActivityMainBinding, MainViewModel>() {

    override val layoutId: Int
        get() = R.layout.activity_main

    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override fun setupView(savedInstanceState: Bundle?) {
        App.statusBarHeight = Utils.getStatusBarHeight(this)

    }

    override fun setupData() {

    }
    companion object {
        const val ACCESS_FINE_LOCATION_REQUEST_CODE = 100
    }

}