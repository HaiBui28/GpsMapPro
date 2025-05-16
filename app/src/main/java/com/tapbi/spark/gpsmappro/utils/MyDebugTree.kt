package com.tapbi.spark.gpsmappro.utils

import timber.log.Timber

class MyDebugTree : Timber.DebugTree() {
    private var fileName: String =""
    override fun createStackElementTag(element: StackTraceElement): String {
        fileName=element.fileName
        return String.format(
            "(%s:%s)#%s",
            element.fileName,
            element.lineNumber,
            element.methodName
        )
    }
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, fileName, "$tag $message", t)
    }
}