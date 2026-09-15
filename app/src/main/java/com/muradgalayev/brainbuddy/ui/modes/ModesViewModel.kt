package com.muradgalayev.brainbuddy.ui.modes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.ModeManager
import com.muradgalayev.brainbuddy.data.repository.ModeRepository
import com.muradgalayev.brainbuddy.domain.model.AppMode
import com.muradgalayev.brainbuddy.domain.model.ModeSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.muradgalayev.brainbuddy.R

@HiltViewModel
class ModesViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val modeRepository: ModeRepository,
    private val modeManager: ModeManager,
) : ViewModel() {

    val modes: StateFlow<List<AppMode>> = modeManager.modes

    val activeMode: StateFlow<AppMode?> = modeManager.activeMode

    // whether schedules, an explicit off choice, or a manually chosen mode is in control
    val selection = modeManager.selection

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // the mode currently being edited, or a blank one when creating. held here rather than in the
    // screen so a rotation mid-edit doesn't discard a half-configured mode, since this form has a
    // lot in it and losing it would be maddening
    private val _draft = MutableStateFlow<AppMode?>(null)
    val draft: StateFlow<AppMode?> = _draft.asStateFlow()

    private val _draftLoadError = MutableStateFlow<String?>(null)
    val draftLoadError: StateFlow<String?> = _draftLoadError.asStateFlow()

    private var beginEditJob: Job? = null
    private var loadedDraftId: String? = null

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    fun selectMode(id: String?) = modeManager.selectMode(id)

    fun useAutomaticModes() = modeManager.useAutomaticModes()

    // loads an existing mode into the editor, or starts a new one when id is null
    fun beginEdit(id: String?) {
        if (_draft.value != null && loadedDraftId == id) return
        beginEditJob?.cancel()
        _draft.value = null
        _draftLoadError.value = null
        beginEditJob = viewModelScope.launch {
            val loaded = if (id == null) {
                AppMode(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    sortIndex = (modes.value.maxOfOrNull { it.sortIndex } ?: 0) + 1,
                )
            } else {
                modeRepository.getMode(id)
            }
            if (loaded == null) {
                loadedDraftId = id
                _draftLoadError.value =
                    context.getString(R.string.modes_err_unavailable)
            } else {
                loadedDraftId = id
                _draft.value = loaded
            }
        }
    }

    fun updateDraft(transform: (AppMode) -> AppMode) {
        _draft.value = _draft.value?.let(transform)
    }

    // returns false when the mode can't be saved, so the screen can stay put and say why
    fun saveDraft(onSaved: (Boolean) -> Unit) {
        if (_saving.value) return
        val draft = _draft.value ?: return onSaved(false)
        if (draft.name.isBlank()) {
            _message.value = context.getString(R.string.modes_err_name)
            return onSaved(false)
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                if (modeRepository.save(draft.copy(name = draft.name.trim()))) {
                    _draft.value = null
                    onSaved(true)
                } else {
                    _message.value = context.getString(R.string.modes_err_sign_in)
                    onSaved(false)
                }
            } catch (_: Exception) {
                _message.value = context.getString(R.string.modes_err_save)
                onSaved(false)
            } finally {
                _saving.value = false
            }
        }
    }

    fun discardDraft() {
        beginEditJob?.cancel()
        loadedDraftId = null
        _draft.value = null
        _draftLoadError.value = null
    }

    fun delete(id: String, onDone: (Boolean) -> Unit) {
        if (_deleting.value) return
        viewModelScope.launch {
            _deleting.value = true
            // capture before Room removes or hides the row. once that emission reaches ModeManager, a
            // now-missing manual id is normalized to Automatic, and checking afterwards would leave the
            // stale manual id durable in DataStore
            val wasManuallySelected =
                (selection.value as? ModeSelection.Manual)?.modeId == id
            try {
                val removed = modeRepository.delete(id)
                if (!removed) {
                    _message.value = context.getString(R.string.modes_err_builtin)
                } else if (wasManuallySelected) {
                    // the deleted manual id can't remain durable. hand schedules control back, since explicit No
                    // mode now means something different and would pause them
                    modeManager.useAutomaticModes()
                }
                onDone(removed)
            } catch (_: Exception) {
                _message.value = context.getString(R.string.modes_err_delete)
                onDone(false)
            } finally {
                _deleting.value = false
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
