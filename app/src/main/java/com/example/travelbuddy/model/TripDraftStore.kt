package com.example.travelbuddy.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TripDraftStore {
    private val _draft = MutableStateFlow(TripDraft())
    val draft: StateFlow<TripDraft> = _draft.asStateFlow()

    fun update(transform: (TripDraft) -> TripDraft) {
        _draft.value = transform(_draft.value)
    }

    fun set(draft: TripDraft) {
        _draft.value = draft
    }
}
