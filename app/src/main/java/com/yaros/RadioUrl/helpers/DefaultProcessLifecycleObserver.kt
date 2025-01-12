package com.yaros.RadioUrl.helpers

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class DefaultProcessLifecycleObserver (
    private val onProcessCaseForeground: () -> Unit
): DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        onProcessCaseForeground()
    }
}