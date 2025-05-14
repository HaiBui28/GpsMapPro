package com.tapbi.spark.gpsmappro.ui.main

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import com.tapbi.spark.gpsmappro.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : BaseViewModel() {
    var addTextLiveEvent: MutableLiveData<String> = MutableLiveData()
    var completeImage: MutableLiveData<String> = MutableLiveData()
    var editImage: MutableLiveData<Uri> = MutableLiveData()
    var undoSticker: MutableLiveData<Int> = MutableLiveData()
}
