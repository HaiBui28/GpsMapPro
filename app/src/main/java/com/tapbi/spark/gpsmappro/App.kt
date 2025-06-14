package com.tapbi.spark.gpsmappro

import android.app.Application
import android.util.Log
import androidx.multidex.BuildConfig
import com.tapbi.spark.gpsmappro.common.model.FolderPhotoModel
import com.tapbi.spark.gpsmappro.data.local.SharedPreferenceHelper
import com.tapbi.spark.gpsmappro.utils.MyDebugTree
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import timber.log.Timber

@HiltAndroidApp
class App : Application() {
    val foldersMap = ArrayList<FolderPhotoModel>()
    companion object {
        var instance: App? = null
            private set
        var statusBarHeight = 0
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        RxJavaPlugins.setErrorHandler { t: Throwable? ->
            Timber.w(
                t
            )
        }
        SharedPreferenceHelper.init(this)
        initLog()
    }


    private fun initLog() {
        Timber.plant(MyDebugTree())
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        LocaleUtils.applyLocale(this)
//    }
}