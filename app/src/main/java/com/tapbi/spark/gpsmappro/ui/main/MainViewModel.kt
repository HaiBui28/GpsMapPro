package com.tapbi.spark.gpsmappro.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tapbi.spark.gpsmappro.common.model.FolderPhotoModel
import com.tapbi.spark.gpsmappro.ui.base.BaseViewModel
import com.tapbi.spark.gpsmappro.utils.MediaUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : BaseViewModel() {
    var addTextLiveEvent: MutableLiveData<String> = MutableLiveData()
    var completeImage: MutableLiveData<String> = MutableLiveData()
    var editImage: MutableLiveData<Uri> = MutableLiveData()
    var undoSticker: MutableLiveData<Int> = MutableLiveData()
    var listLocationPhoto: MutableLiveData<List<FolderPhotoModel>> = MutableLiveData()
    fun getListLocationPhoto(context: Context){
        viewModelScope.launch(Dispatchers.IO) {
            listLocationPhoto.postValue(MediaUtil.getDevicePhotosByFolder(context))
        }
    }
}
