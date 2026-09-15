package com.muradgalayev.brainbuddy.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.PlanRepository
import com.muradgalayev.brainbuddy.domain.model.Plan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    networkObserver: NetworkObserver,
) : ViewModel() {

    val plan: StateFlow<Plan> = planRepository.plan
    val isOnline = networkObserver.isOnline

    private val _joining = MutableStateFlow(false)
    val joining: StateFlow<Boolean> = _joining.asStateFlow()

    private val _joinFailed = MutableStateFlow(false)
    val joinFailed: StateFlow<Boolean> = _joinFailed.asStateFlow()

    // joining is a server write, so it waits on the answer: saying 'you're in' before the server
    // agreed would unlock features the next refresh takes away again
    fun joinBeta() {
        if (_joining.value || plan.value == Plan.Plus) return
        _joining.value = true
        _joinFailed.value = false
        viewModelScope.launch {
            val result = planRepository.joinBeta()
            _joinFailed.value = result.getOrNull() != Plan.Plus
            _joining.value = false
        }
    }
}
