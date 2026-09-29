package dev.behradhz.meowzix.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val queries: LibraryQueryRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    val results = query
        .map(String::trim)
        .distinctUntilChanged()
        .debounce { value -> if (value.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { value ->
            flow {
                emit(if (value.isBlank()) emptyList() else queries.search(value))
            }.catch { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList<Track>(),
        )

    fun setQuery(value: String) {
        query.value = value
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 120L
    }
}
