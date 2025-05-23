package com.tapbi.spark.gpsmappro.ui.main.home

import android.os.Bundle
import android.view.View
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.FragmentHomeBinding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingFragment
import com.tapbi.spark.gpsmappro.utils.MediaUtil

class HomeFragment : BaseBindingFragment<FragmentHomeBinding, HomeViewModel>() {
    override fun getViewModel(): Class<HomeViewModel> {
        return HomeViewModel::class.java
    }

    override val layoutId: Int
        get() = R.layout.fragment_home


    override fun onCreatedView(view: View?, savedInstanceState: Bundle?) {

    }

    override fun onPermissionGranted() {

    }

    override fun observerLiveData() {

    }

    override fun onBackPressed() {

    }


}