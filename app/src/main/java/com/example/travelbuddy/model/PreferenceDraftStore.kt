package com.example.travelbuddy.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceDraftStore {
    private val _draft = MutableStateFlow(PreferenceDraft())
    val draft: StateFlow<PreferenceDraft> = _draft.asStateFlow()

    fun update(transform: (PreferenceDraft) -> PreferenceDraft) {
        _draft.value = transform(_draft.value)
    }

    fun set(draft: PreferenceDraft) {
        _draft.value = draft
    }
}
