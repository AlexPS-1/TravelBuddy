package com.example.travelbuddy.model.plan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlanStore {
    private val _blocks = MutableStateFlow<List<PlanBlock>>(emptyList())
    val blocks: StateFlow<List<PlanBlock>> = _blocks.asStateFlow()

    fun add(block: PlanBlock) {
        _blocks.value = _blocks.value + block
    }

    fun remove(blockId: String) {
        _blocks.value = _blocks.value.filterNot { it.id == blockId }
    }

    fun moveUp(blockId: String) {
        val list = _blocks.value.toMutableList()
        val idx = list.indexOfFirst { it.id == blockId }
        if (idx <= 0) return
        val tmp = list[idx - 1]
        list[idx - 1] = list[idx]
        list[idx] = tmp
        _blocks.value = list
    }

    fun moveDown(blockId: String) {
        val list = _blocks.value.toMutableList()
        val idx = list.indexOfFirst { it.id == blockId }
        if (idx < 0 || idx >= list.lastIndex) return
        val tmp = list[idx + 1]
        list[idx + 1] = list[idx]
        list[idx] = tmp
        _blocks.value = list
    }

    fun clear() {
        _blocks.value = emptyList()
    }
}
