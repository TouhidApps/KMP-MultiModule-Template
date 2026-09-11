package com.touhidapps.kmp.feature.alertmaintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touhidapps.network.ResultNet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MaintenanceViewModel(
    private val maintenanceService: MaintenanceService,
) : ViewModel() {

    private val _status = MutableStateFlow<MaintenanceStatus>(MaintenanceStatus.Operational)
    val status: StateFlow<MaintenanceStatus> = _status.asStateFlow()

    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // A failed check leaves the app usable, same as force-update.
            _status.value = when (val result = maintenanceService.status()) {
                is ResultNet.Success -> result.data
                is ResultNet.Error -> MaintenanceStatus.Operational
            }
        }
    }

    /** Only honoured for a non-blocking window. */
    fun dismiss() {
        _dismissed.value = true
    }
}
