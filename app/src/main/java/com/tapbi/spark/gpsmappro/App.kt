package com.tapbi.spark.gpsmappro

import android.app.Application
import androidx.multidex.BuildConfig
import com.tapbi.spark.gpsmappro.utils.MyDebugTree
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import timber.log.Timber

@HiltAndroidApp
class App : Application() {
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
        initLog()
    }


    private fun initLog() {
        if (BuildConfig.DEBUG) {
            Timber.plant(MyDebugTree())
        }
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        LocaleUtils.applyLocale(this)
//    }
}