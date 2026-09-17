package dev.behradhz.meowzix.feature.queue

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.playback.QueueRepository
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
) : ViewModel() {
    val state = queueRepository.queueState

    fun moveUp(index: Int) = queueRepository.move(index, index - 1)
    fun moveDown(index: Int) = queueRepository.move(index, index + 1)
    fun remove(index: Int) = queueRepository.removeAt(index)
    fun clear() = queueRepository.clear()
}
