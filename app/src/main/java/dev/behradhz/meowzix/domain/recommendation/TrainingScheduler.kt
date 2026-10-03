package dev.behradhz.meowzix.domain.recommendation

interface TrainingScheduler {
    fun scheduleTraining(rebuild: Boolean = false)
}
