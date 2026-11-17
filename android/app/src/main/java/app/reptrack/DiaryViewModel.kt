package app.reptrack

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiaryRepository(application)
    private val _snapshot = MutableStateFlow(DiarySnapshot())
    val snapshot: StateFlow<DiarySnapshot> = _snapshot

    init {
        refresh()
    }

    fun refresh() = mutate { }
    fun start(planId: String?) = mutate { repository.start(planId) }
    fun updateSet(id: String, weight: String? = null, reps: String? = null) = mutate { repository.updateSet(id, weight, reps) }
    fun toggleSet(set: TrainingSet) = mutate { repository.toggleSet(set) }
    fun setKind(id: String, kind: SetKind) = mutate { repository.setKind(id, kind) }
    fun addSet(exerciseId: String) = mutate { repository.addSet(exerciseId) }
    fun removeSet(id: String) = mutate { repository.removeSet(id) }
    fun skipRest(id: String) = mutate { repository.skipRest(id) }
    fun pauseRest(id: String) = mutate { repository.pauseRest(id) }
    fun resumeRest(id: String) = mutate { repository.resumeRest(id) }
    fun skipExercise(id: String, skipped: Boolean) = mutate { repository.skipExercise(id, skipped) }
    fun replaceExercise(id: String, replacement: PlanExercise) = mutate { repository.replaceExercise(id, replacement) }
    fun finish(id: String) = mutate { repository.finish(id) }
    fun cancel(id: String) = mutate { repository.cancel(id) }
    fun updateComment(id: String, comment: String) = mutate { repository.updateComment(id, comment) }
    fun renamePlan(id: String, name: String) = mutate { repository.renamePlan(id, name) }
    fun movePlan(id: String, offset: Int) = mutate { repository.movePlan(id, offset) }
    fun createPlan(name: String) = mutate { repository.createPlan(name) }
    fun createStarterPlans() = mutate { repository.createStarterPlans() }
    fun duplicatePlan(id: String) = mutate { repository.duplicatePlan(id) }
    fun deletePlan(id: String) = mutate { repository.deletePlan(id) }
    fun setPlanInCycle(id: String, included: Boolean) = mutate { repository.setPlanInCycle(id, included) }
    fun addExercise(planId: String, name: String) = mutate { repository.addExercise(planId, name) }
    fun updateExercise(exercise: PlanExercise) = mutate { repository.updateExercise(exercise) }
    fun moveExercise(id: String, planId: String, offset: Int) = mutate { repository.moveExercise(id, planId, offset) }
    fun deleteExercise(id: String) = mutate { repository.deleteExercise(id) }
    fun setWeightUnit(unit: WeightUnit) = mutate { repository.setWeightUnit(unit) }
    fun setTheme(theme: DiaryTheme) = mutate { repository.setTheme(theme) }
    fun setRestTimerEnabled(enabled: Boolean) = mutate { repository.setRestTimerEnabled(enabled) }
    fun setUserRole(role: UserRole) = mutate { repository.setUserRole(role) }
    fun createTraineeProfile(name: String) = mutate { repository.createTraineeProfile(name) }
    fun createDemoProfile() = mutate { repository.createDemoProfile() }
    fun createDemoReportsProfile() = mutate { repository.createDemoReportsProfile() }
    fun renameProfile(id: String, name: String) = mutate { repository.renameProfile(id, name) }
    fun setActiveProfile(id: String, onComplete: (String?) -> Unit = {}) = transferMutation(onComplete) { repository.setActiveProfile(id) }
    fun assignPlanToProfile(planId: String, profileId: String) = mutate { repository.assignPlanToProfile(planId, profileId) }
    fun stageAssignmentUpdate(id: String) = mutate { repository.stageAssignmentUpdate(id) }
    fun acceptAssignmentUpdate(id: String) = mutate { repository.acceptAssignmentUpdate(id) }
    fun rejectAssignmentUpdate(id: String) = mutate { repository.rejectAssignmentUpdate(id) }
    internal fun confirmPairing(id: String, onComplete: (String?) -> Unit = {}) =
        transferMutation(onComplete) { repository.confirmPairing(id) }
    internal fun revokeLink(id: String, onComplete: (String?) -> Unit = {}) = transferMutation(onComplete) { repository.revokeLink(id) }
    fun restoreEncryptedBackup(container: ByteArray, password: String, onComplete: (String?) -> Unit = {}) =
        transferMutation(onComplete) { repository.restoreEncryptedBackup(container, password) }
    fun importPlans(json: ByteArray, onComplete: (String?) -> Unit = {}) = transferMutation(onComplete) { repository.importPlans(json) }

    fun createEncryptedBackup(password: String, onReady: (ByteArray) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.createEncryptedBackup(password) } }
            result.onSuccess(onReady).onFailure { error ->
                _snapshot.value =
                    _snapshot.value.copy(error = error.message ?: "Не удалось создать резервную копию")
            }
        }
    }

    fun exportPlans(onReady: (ByteArray) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.exportPlans() } }
            result.onSuccess(onReady).onFailure { error ->
                _snapshot.value =
                    _snapshot.value.copy(error = error.message ?: "Не удалось экспортировать планы")
            }
        }
    }

    internal fun createPairingInvitation(profileId: String, onReady: (ByteArray) -> Unit) = transferOutput(onReady) {
        repository.createPairingInvitation(profileId)
    }
    internal fun respondToPairingInvite(bytes: ByteArray, profileId: String, onReady: (PairingResponse) -> Unit) =
        transferOutput(onReady) { repository.respondToPairingInvite(bytes, profileId) }
    internal fun processPairingResponse(bytes: ByteArray, onReady: (PairingPending) -> Unit) =
        transferOutput(onReady) { repository.processPairingResponse(bytes) }
    internal fun createResultPackage(linkId: String, sessionIds: Set<String>, onReady: (ByteArray) -> Unit) =
        transferOutput(onReady) { repository.createResultPackage(linkId, sessionIds) }
    internal fun createTelegramRouteFile(linkId: String, onReady: (ByteArray) -> Unit) =
        transferOutput(onReady) { repository.createTelegramRouteFile(linkId) }
    internal fun finishAndCreateTelegramDelivery(linkId: String, sessionId: String, onComplete: (ByteArray?, String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.finishAndCreateTelegramDelivery(linkId, sessionId) } }
            _snapshot.value = withContext(Dispatchers.IO) { repository.load() }
            result.onSuccess { onComplete(it, null) }
                .onFailure { error ->
                    val message = error.message ?: "Не удалось подготовить отчёт"
                    _snapshot.value = _snapshot.value.copy(error = message)
                    onComplete(null, message)
                }
        }
    }
    internal fun createTelegramDelivery(linkId: String, sessionId: String, onComplete: (ByteArray?, String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.createTelegramDelivery(linkId, sessionId) } }
            result.onSuccess { onComplete(it, null) }.onFailure { onComplete(null, it.message ?: "Не удалось подготовить отчёт") }
        }
    }
    internal fun createProgramPackage(linkId: String, planId: String, onReady: (ByteArray) -> Unit) =
        transferOutput(onReady) { repository.createProgramPackage(linkId, planId) }
    internal fun previewExchange(bytes: ByteArray, onReady: (ExchangePreview) -> Unit) =
        transferOutput(onReady) { repository.previewExchange(bytes) }
    internal fun acceptExchange(bytes: ByteArray, onComplete: (ExchangeImportResult?, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.acceptExchange(bytes) to repository.load() } }
            result.onSuccess { (output, snapshot) ->
                _snapshot.value = snapshot
                onComplete(output, null)
            }
                .onFailure { error ->
                    val message = error.message ?: "Не удалось принять пакет"
                    _snapshot.value = _snapshot.value.copy(error = message)
                    onComplete(null, message)
                }
        }
    }
    internal fun rejectExchange(bytes: ByteArray, onComplete: (String?) -> Unit = {}) =
        transferMutation(onComplete) { repository.rejectExchange(bytes) }

    private fun <T> transferOutput(onReady: (T) -> Unit, action: () -> T) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action() to repository.load() } }
            result.onSuccess { (output, snapshot) ->
                _snapshot.value = snapshot
                onReady(output)
            }
                .onFailure { error -> _snapshot.value = _snapshot.value.copy(error = error.message ?: "Не удалось обработать файл обмена") }
        }
    }

    private fun transferMutation(onComplete: (String?) -> Unit, action: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    action()
                    repository.load()
                }
            }
            result.onSuccess {
                _snapshot.value = it
                onComplete(null)
            }
                .onFailure { error ->
                    val message = error.message ?: "Не удалось импортировать данные"
                    _snapshot.value = _snapshot.value.copy(error = message)
                    onComplete(message)
                }
        }
    }

    private fun mutate(action: () -> Unit) {
        viewModelScope.launch {
            _snapshot.value = withContext(Dispatchers.IO) {
                runCatching {
                    action()
                    repository.load()
                }.getOrElse {
                    _snapshot.value.copy(
                        loading = false,
                        error =
                        it.message ?: "Не удалось сохранить данные",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
