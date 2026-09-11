package com.touhidapps.more.presentation

import com.touhidapps.common.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MoreViewModel : BaseViewModel(), MoreContract {
    private val _state = MutableStateFlow(MoreState())
    override val state: StateFlow<MoreState> = _state.asStateFlow()
}
