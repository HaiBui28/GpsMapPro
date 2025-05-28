package com.tapbi.spark.gpsmappro.ui.main.camera4

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class MyGLSurfaceView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs) {


    private val renderer: MyRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = MyRenderer(this)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setOnSurfaceTextureReadyListener(l: MyRenderer.OnSurfaceTextureReadyListener) {
        renderer.setOnSurfaceTextureReadyListener(l)
    }

    fun getRenderer(): MyRenderer = renderer
}