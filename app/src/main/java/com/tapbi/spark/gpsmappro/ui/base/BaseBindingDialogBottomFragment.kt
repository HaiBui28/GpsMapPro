package com.tapbi.spark.gpsmappro.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import com.tapbi.spark.gpsmappro.R
import com.tapbi.spark.gpsmappro.ui.main.MainViewModel

abstract class BaseBindingDialogBottomFragment<B : ViewDataBinding, VM : BaseViewModel> :
    BaseDialogBottomFragment() {
    lateinit var binding: B
    lateinit var viewModel: VM
    lateinit var mainViewModel: MainViewModel
    protected abstract fun getViewModel(): Class<VM>
    abstract val layoutId: Int
    protected abstract fun observerLiveData()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialog)
    }

    protected abstract fun onCreatedView(view: View?, savedInstanceState: Bundle?)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[getViewModel()]
        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        onCreatedView(view, savedInstanceState)
        observerLiveData()
        isCancelable = false
    }
}
