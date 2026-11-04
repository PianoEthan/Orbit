package com.qx.orbit.bili.presentation.theme

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.wear.compose.material3.ColorScheme
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PageColorSchemeViewModelTest {
    private val factory = object : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            modelClass.cast(PageColorSchemeViewModel())!!
    }

    private fun model(store: ViewModelStore): PageColorSchemeViewModel =
        ViewModelProvider.create(store, factory)[PageColorSchemeViewModel::class]

    @Test
    fun returningToTheSameEntryRestoresTheSchemeSynchronously() {
        val store = ViewModelStore()
        val scheme = ColorScheme()
        val state = model(store).stateFor("cover-a")
        state.value = scheme

        val restored = model(store).stateFor("cover-a")

        assertSame(state, restored)
        assertSame(scheme, restored.value)
        store.clear()
    }

    @Test
    fun differentCoversAndNavigationEntriesDoNotShareColors() {
        val first = ViewModelStore()
        val second = ViewModelStore()
        model(first).stateFor("cover-a").value = ColorScheme()

        assertNull(model(first).stateFor("cover-b").value)
        assertNull(model(second).stateFor("cover-a").value)
        first.clear()
        second.clear()
    }

    @Test
    fun poppedEntryDoesNotLeakItsSchemeIntoANewEntry() {
        val store = ViewModelStore()
        val old = model(store)
        old.stateFor("cover-a").value = ColorScheme()
        store.clear()

        val fresh = model(store)

        assertNotSame(old, fresh)
        assertNull(fresh.stateFor("cover-a").value)
        store.clear()
    }
}
