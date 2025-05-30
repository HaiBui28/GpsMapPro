package com.tapbi.spark.gpsmappro.ui.main

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.tapbi.spark.gpsmappro.App
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.databinding.ActivityMainBinding
import com.tapbi.spark.gpsmappro.ui.base.BaseBindingActivity
import com.tapbi.spark.gpsmappro.utils.Utils


class MainActivity : BaseBindingActivity<ActivityMainBinding, MainViewModel>() {
    var navController: NavController? = null
    private var graph: NavGraph? = null
    private val REQUEST_EXTERNAL_STORAGE: Int = 1
    private var PERMISSIONS_STORAGE: Array<String> = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    fun verifyStoragePermissions(activity: Activity?) {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                activity!!,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
            )
        }
    }

    override val layoutId: Int
        get() = R.layout.activity_main

    override fun getViewModel(): Class<MainViewModel> {
        return MainViewModel::class.java
    }

    override fun setupView(savedInstanceState: Bundle?) {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),11111)
        App.statusBarHeight = Utils.getStatusBarHeight(this)
        verifyStoragePermissions(this)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
        navController = navHostFragment?.navController

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