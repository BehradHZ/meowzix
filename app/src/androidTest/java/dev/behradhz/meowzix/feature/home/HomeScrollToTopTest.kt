package dev.behradhz.meowzix.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScrollToTopTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeReselectionScrollsRetainedListToHeader() {
        var request by mutableIntStateOf(0)
        var firstVisibleIndex = -1
        compose.setContent {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = 20)
            HomeScrollToTopEffect(state, request)
            LazyColumn(state = state, modifier = Modifier.height(280.dp)) {
                items((0..39).toList()) {
                    Box(Modifier.fillMaxWidth().height(64.dp))
                }
            }
            firstVisibleIndex = state.firstVisibleItemIndex
        }

        compose.runOnIdle { assertEquals(20, firstVisibleIndex) }
        compose.runOnIdle { request++ }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, firstVisibleIndex) }
    }
}
