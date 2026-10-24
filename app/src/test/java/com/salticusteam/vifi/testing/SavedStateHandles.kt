package com.salticusteam.vifi.testing

import androidx.lifecycle.SavedStateHandle

/**
 * A new handle holding only what this one saved, as the system hands it to a ViewModel recreated after a process
 * death (the in-memory state of the old ViewModel is gone).
 */
fun SavedStateHandle.afterProcessDeath(): SavedStateHandle =
    SavedStateHandle(keys().associateWith { key -> get<Any?>(key) })
