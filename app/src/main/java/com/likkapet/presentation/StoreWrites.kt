package com.likkapet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launches a stats-store write that must finish even if the screen closes right after the tap
 * (e.g. "Desactivar" then Back): viewModelScope alone would cancel it with the ViewModel.
 */
fun ViewModel.persist(write: suspend () -> Unit): Job = viewModelScope.launch { withContext(NonCancellable) { write() } }
