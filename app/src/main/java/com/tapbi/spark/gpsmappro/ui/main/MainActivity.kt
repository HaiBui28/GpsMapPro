package com.tapbi.spark.gpsmappro.ui.main

import android.os.Bundle
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.Navigation.findNavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.tapbi.spark.gpsmappro.App
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingActivity
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.ActivityMainBinding
import com.tapbi.spark.gpsmappro.utils.MediaUtil
import com.tapbi.spark.gpsmappro.utils.Utils

class MainActivity : BaseBindingActivity<ActivityMainBinding, MainViewModel>() {
    var navController: NavController? = null
    private var graph: NavGraph? = null
    override val layoutId: Int
        get() = R.layout.activity_main

    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override fun setupView(savedInstanceState: Bundle?) {
        App.statusBarHeight = Utils.getStatusBarHeight(this)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
        navController  = navHostFragment?.navController

//        if (graph == null) {
//            graph = navController?.navInflater?.inflate(R.navigation.nav_home)
//        }
//        graph?.setStartDestination(R.id.homeFragment)
//        navController?.graph = graph!!
//        MediaUtil.getDevicePhotosByFolder(this)
    }

    fun navigate(id: Int) {
        findNavController(R.id.nav_host_fragment).navigate(id)
    }

    override fun setupData() {

    }

    companion object {
        const val ACCESS_FINE_LOCATION_REQUEST_CODE = 100
    }

}