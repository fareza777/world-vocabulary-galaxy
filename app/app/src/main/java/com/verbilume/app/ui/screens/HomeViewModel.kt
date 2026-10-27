package com.verbilume.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.LearningStats
import com.verbilume.app.data.TopicProgress
import com.verbilume.app.data.WordDetail
import com.verbilume.app.data.WordSummary
import com.verbilume.app.ui.friendlyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val stats: LearningStats? = null,
    val wordOfDay: WordSummary? = null,
    val wordOfDayDetail: WordDetail? = null,
    val picks: List<WordSummary> = emptyList(),
    val topics: List<TopicProgress> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(private val graph: AppGraph) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(graph.settings.settings, graph.learning.dataVersion) { settings, _ -> settings }
                .collect { settings -> load(settings) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            load(graph.settings.settings.first())
        }
    }

    private suspend fun load(settings: AppSettings) {
        val current = _state.value
        if (current.stats == null) _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            val stats = graph.learning.stats(settings)
            val topics = graph.learning.topicProgress()
            val day = LocalDate.now().toString()
            val extras = withContext(Dispatchers.IO) {
                val wotd = graph.content.wordOfTheDay(day, settings.level, settings.effectiveTopics)
                val detail = wotd?.let { graph.content.detail(it.id) }
                val picks = graph.content.newWordBatch(
                    settings.level,
                    settings.effectiveTopics,
                    day.hashCode().toLong() + 431L,
                    6,
                )
                Triple(wotd, detail, picks)
            }
            _state.value = HomeUiState(
                loading = false,
                stats = stats,
                wordOfDay = extras.first,
                wordOfDayDetail = extras.second,
                picks = extras.third,
                topics = topics,
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                loading = false,
                error = friendlyError(error, "Something went wrong while opening the atlas."),
            )
        }
    }
}
