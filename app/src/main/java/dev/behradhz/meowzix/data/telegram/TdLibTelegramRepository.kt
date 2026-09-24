package dev.behradhz.meowzix.data.telegram

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.BuildConfig
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.PlaylistDao
import dev.behradhz.meowzix.data.db.PlaylistEntity
import dev.behradhz.meowzix.data.db.PlaylistTrackEntity
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TelegramSelectedSourceEntity
import dev.behradhz.meowzix.data.db.TelegramTrackSourceEntity
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.repository.IncomingTrackIdentity
import dev.behradhz.meowzix.data.repository.TrackMatchCandidate
import dev.behradhz.meowzix.data.repository.UnifiedTrackMatcher
import dev.behradhz.meowzix.domain.telegram.TelegramAuthState
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramMusicSourceState
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import dev.behradhz.meowzix.domain.telegram.TelegramSyncResult
import dev.behradhz.meowzix.domain.telegram.telegramPlaylistId
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@Singleton
class TdLibTelegramRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MeowzixDatabase,
    private val libraryDao: LibraryDao,
    private val telegramDao: TelegramDao,
    private val playlistDao: PlaylistDao,
) : TelegramRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _authState = MutableStateFlow(TelegramAuthState())
    override val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()
    private val _musicSourceState = MutableStateFlow(TelegramMusicSourceState())
    override val musicSourceState: StateFlow<TelegramMusicSourceState> = _musicSourceState.asStateFlow()
    private var client: TdLibClientAdapter? = null
    private var restartAfterClose = false
    private var currentUserId: Long? = null

    init {
        startClient()
    }

    override fun submitPhoneNumber(phoneNumber: String) = submit(
        value = phoneNumber,
        emptyMessage = "Enter a phone number including its country code.",
    ) { TdApi.SetAuthenticationPhoneNumber(it, TdApi.PhoneNumberAuthenticationSettings()) }

    override fun submitCode(code: String) = submit(code, "Enter the authentication code.") {
        TdApi.CheckAuthenticationCode(it)
    }

    override fun submitPassword(password: String) {
        if (password.isEmpty()) {
            showError("Enter your two-step verification password.")
            return
        }
        submitRequest { TdApi.CheckAuthenticationPassword(password) }
    }

    override fun submitEmailAddress(emailAddress: String) = submit(emailAddress, "Enter an email address.") {
        TdApi.SetAuthenticationEmailAddress(it)
    }

    override fun submitEmailCode(code: String) = submit(code, "Enter the email authentication code.") {
        TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(it))
    }

    override fun register(firstName: String, lastName: String) {
        if (firstName.isBlank()) {
            showError("Enter your first name.")
            return
        }
        submitRequest { TdApi.RegisterUser(firstName.trim(), lastName.trim(), false) }
    }

    override fun logout() {
        restartAfterClose = true
        submitRequest { TdApi.LogOut() }
    }

    override fun clearError() {
        _authState.update { it.copy(errorMessage = null) }
    }

    override fun clearMusicSourceError() {
        _musicSourceState.update { it.copy(errorMessage = null) }
    }

    override fun refreshSelectableChats() {
        val activeClient = client ?: return showMusicSourceError("Telegram is not ready yet.")
        val userId = currentUserId ?: return showMusicSourceError("Telegram account information is still loading.")
        if (_musicSourceState.value.isLoadingChats) return
        _musicSourceState.update { it.copy(isLoadingChats = true, errorMessage = null) }
        scope.launch {
            runCatching {
                val selectedIds = telegramDao.selectedSources(userId.toString()).mapTo(mutableSetOf()) { it.chatId }
                val chatIds = activeClient.send(TdApi.GetChats(null, 100)).chatIds.toMutableSet()
                chatIds += selectedIds
                runCatching { activeClient.send(TdApi.CreatePrivateChat(userId, false)) }
                    .getOrNull()?.let { chatIds += it.id }
                val loadedChats = chatIds.mapNotNull { chatId ->
                    runCatching { activeClient.send(TdApi.GetChat(chatId)) }.getOrNull()
                }
                val summaries = loadedChats.map { chat ->
                    chat.toSummary(
                        currentUserId = userId,
                        selected = chat.id in selectedIds,
                        profilePhotoRef = localChatPhotoRef(chat),
                    )
                }.sortedWith(compareByDescending<TelegramChatSummary> { it.kind == TelegramChatKind.SAVED_MESSAGES }.thenBy { it.title.lowercase(Locale.ROOT) })
                LoadedSelectableChats(loadedChats, summaries)
            }.onSuccess { loaded ->
                _musicSourceState.update { it.copy(chats = loaded.summaries, isLoadingChats = false) }
                // Avatars are cosmetic. They must never block Telegram chat discovery.
                scope.launch {
                    loaded.rawChats.forEach { chat ->
                        if (localChatPhotoRef(chat) != null) return@forEach
                        val ref = resolveChatPhotoRef(activeClient, chat) ?: return@forEach
                        _musicSourceState.update { state ->
                            state.copy(
                                chats = state.chats.map { row ->
                                    if (row.chatId == chat.id) row.copy(profilePhotoRef = ref) else row
                                },
                            )
                        }
                    }
                }
            }.onFailure { error ->
                _musicSourceState.update { it.copy(isLoadingChats = false, errorMessage = safeMessage(error)) }
            }
        }
    }

    override fun setMusicSourceSelected(chatId: Long, selected: Boolean) {
        val accountId = currentUserId?.toString() ?: return showMusicSourceError("Telegram account information is still loading.")
        val chat = _musicSourceState.value.chats.firstOrNull { it.chatId == chatId }
            ?: return showMusicSourceError("That Telegram chat is no longer available.")
        scope.launch {
            runCatching {
                if (selected) {
                    val existing = telegramDao.selectedSource(accountId, chatId)
                    telegramDao.upsertSelectedSource(
                        TelegramSelectedSourceEntity(
                            accountId = accountId,
                            chatId = chatId,
                            title = chat.title,
                            kind = chat.kind.name,
                            newestMessageId = existing?.newestMessageId,
                            initialScanComplete = existing?.initialScanComplete ?: false,
                            lastSyncedAtEpochMs = existing?.lastSyncedAtEpochMs,
                        ),
                    )
                    ensureChatPlaylist(accountId, chatId, chat.title)
                } else {
                    // Selection controls future syncing only. Imported tracks and their remote source
                    // stay in the library and the generated chat playlist is intentionally retained.
                    telegramDao.deleteSelectedSource(accountId, chatId)
                }
            }.onSuccess {
                reloadPersistedSelection(accountId)
                if (selected) syncOneSource(accountId, chatId)
            }.onFailure { showMusicSourceError(safeMessage(it)) }
        }
    }

    override fun syncSelectedSources() {
        val accountId = currentUserId?.toString() ?: return showMusicSourceError("Telegram account information is still loading.")
        if (_musicSourceState.value.isSyncing) return
        scope.launch {
            val selected = telegramDao.selectedSources(accountId)
            if (selected.isEmpty()) {
                showMusicSourceError("Choose at least one Telegram music source first.")
                return@launch
            }
            _musicSourceState.update { it.copy(isSyncing = true, errorMessage = null) }
            val total = MutableSyncResult()
            runCatching {
                selected.forEach { source -> total.add(syncSource(accountId, source)) }
            }.onSuccess {
                _musicSourceState.update { it.copy(isSyncing = false, lastSyncResult = total.toDomain()) }
            }.onFailure { error ->
                _musicSourceState.update { it.copy(isSyncing = false, errorMessage = safeMessage(error)) }
            }
        }
    }

    private fun startClient() {
        val apiId = BuildConfig.TELEGRAM_API_ID.toIntOrNull()?.takeIf { it > 0 }
        if (apiId == null || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _authState.value = TelegramAuthState(step = TelegramAuthStep.ConfigurationRequired)
            return
        }
        _authState.value = TelegramAuthState(step = TelegramAuthStep.Initializing)
        scope.launch {
            runCatching { TdLibClientAdapter() }
                .onSuccess { adapter ->
                    client = adapter
                    launch { adapter.updates.collect(::handleUpdate) }
                    launch { adapter.failures.collect { showError(safeMessage(it)) } }
                }
                .onFailure { showError("TDLib could not start: ${safeMessage(it)}") }
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)
            is TdApi.UpdateNewMessage -> handleNewMessage(update.message)
            is TdApi.UpdateMessageContent -> handleMessageContentChanged(update.chatId, update.messageId)
            is TdApi.UpdateDeleteMessages -> handleDeletedMessages(update)
        }
    }

    private fun handleAuthorizationState(authorizationState: TdApi.AuthorizationState) {
        if (authorizationState is TdApi.AuthorizationStateWaitTdlibParameters) {
            submitParameters()
            return
        }
        val step = authorizationState.toDomainStep()
        _authState.value = TelegramAuthState(step = step)
        when (step) {
            TelegramAuthStep.Ready -> scope.launch { initializeReadyAccount() }
            TelegramAuthStep.Closed -> {
                currentUserId = null
                _musicSourceState.value = TelegramMusicSourceState()
                if (restartAfterClose) {
                    restartAfterClose = false
                    client = null
                    startClient()
                }
            }
            else -> Unit
        }
    }

    private suspend fun initializeReadyAccount() {
        val activeClient = client ?: return
        runCatching { activeClient.send(TdApi.GetMe()) }
            .onSuccess { me ->
                currentUserId = me.id
                val accountId = me.id.toString()
                reloadPersistedSelection(accountId)
                // Reconcile incrementally whenever the TDLib session becomes ready. This catches
                // messages that arrived while the process was stopped or before account state was
                // fully restored, without rebuilding the chat history.
                if (telegramDao.selectedSources(accountId).isNotEmpty()) {
                    syncSelectedSources()
                }
                refreshSelectableChats()
            }
            .onFailure { showMusicSourceError(safeMessage(it)) }
    }

    private suspend fun reloadPersistedSelection(accountId: String) {
        val selected = telegramDao.selectedSources(accountId).mapTo(mutableSetOf()) { it.chatId }
        _musicSourceState.update { state ->
            state.copy(
                accountId = accountId,
                selectedChatIds = selected,
                chats = state.chats.map { it.copy(selected = it.chatId in selected) },
            )
        }
    }

    private fun handleNewMessage(message: TdApi.Message) {
        val accountId = currentUserId?.toString() ?: return
        scope.launch {
            // Room is authoritative for source selection. Avoid dropping a live TDLib update just
            // because the in-memory StateFlow has not caught up yet during startup/reconnect.
            val selected = telegramDao.selectedSource(accountId, message.chatId) ?: return@launch
            val imported = persistTelegramMessage(accountId, message)
            val newest = maxOf(selected.newestMessageId ?: 0L, message.id)
            telegramDao.updateSyncCheckpoint(accountId, message.chatId, newest, selected.initialScanComplete, Instant.now().toEpochMilli())
            if (imported != null) {
                syncChatPlaylist(accountId, message.chatId, selected.title)
                reloadPersistedSelection(accountId)
            }
        }
    }

    private fun handleMessageContentChanged(chatId: Long, messageId: Long) {
        val accountId = currentUserId?.toString() ?: return
        val activeClient = client ?: return
        scope.launch {
            val selected = telegramDao.selectedSource(accountId, chatId) ?: return@launch
            runCatching { activeClient.send(TdApi.GetMessage(chatId, messageId)) }
                .onSuccess {
                    persistTelegramMessage(accountId, it)
                    syncChatPlaylist(accountId, chatId, selected.title)
                }
        }
    }

    private fun handleDeletedMessages(update: TdApi.UpdateDeleteMessages) {
        if (update.fromCache && !update.isPermanent) return
        val accountId = currentUserId?.toString() ?: return
        scope.launch {
            val selected = telegramDao.selectedSource(accountId, update.chatId) ?: return@launch
            val ids = telegramDao.trackSourceIdsForMessages(accountId, update.chatId, update.messageIds.toList())
            if (ids.isNotEmpty()) libraryDao.updateAvailability(ids, SourceAvailability.MISSING, Instant.now().toEpochMilli())
            syncChatPlaylist(accountId, update.chatId, selected.title)
        }
    }

    private suspend fun syncOneSource(accountId: String, chatId: Long) {
        if (_musicSourceState.value.isSyncing) return
        _musicSourceState.update { it.copy(isSyncing = true, errorMessage = null) }
        runCatching {
            val selected = telegramDao.selectedSource(accountId, chatId) ?: return@runCatching MutableSyncResult()
            syncSource(accountId, selected)
        }.onSuccess { result ->
            _musicSourceState.update { it.copy(isSyncing = false, lastSyncResult = result.toDomain()) }
        }.onFailure { error ->
            _musicSourceState.update { it.copy(isSyncing = false, errorMessage = safeMessage(error)) }
        }
    }

    private suspend fun syncSource(accountId: String, selected: TelegramSelectedSourceEntity): MutableSyncResult {
        val activeClient = client ?: error("Telegram is not ready yet.")
        val result = MutableSyncResult(chatsSynced = 1)
        val checkpoint = selected.newestMessageId.takeIf { selected.initialScanComplete }
        val seenEligibleIds = mutableSetOf<Long>()
        var fromMessageId = 0L
        var newestScanned = selected.newestMessageId ?: 0L
        var reachedCheckpoint = false

        while (!reachedCheckpoint) {
            val page = activeClient.send(TdApi.GetChatHistory(selected.chatId, fromMessageId, 0, 100, false))
            if (page.messages.isEmpty()) break
            var lastMessageId = fromMessageId
            for (message in page.messages) {
                result.messagesScanned++
                newestScanned = maxOf(newestScanned, message.id)
                if (checkpoint != null && message.id <= checkpoint) {
                    reachedCheckpoint = true
                    break
                }
                val persisted = persistTelegramMessage(accountId, message)
                if (persisted != null) {
                    seenEligibleIds += message.id
                    if (persisted) result.tracksImported++ else result.tracksUpdated++
                }
                lastMessageId = message.id
            }
            if (reachedCheckpoint || lastMessageId == 0L || lastMessageId == fromMessageId) break
            fromMessageId = lastMessageId
        }

        if (!selected.initialScanComplete) {
            val staleIds = telegramDao.telegramSourcesForChat(accountId, selected.chatId)
                .filter { it.messageId !in seenEligibleIds }
                .map { it.trackSourceId }
            if (staleIds.isNotEmpty()) {
                libraryDao.updateAvailability(staleIds, SourceAvailability.MISSING, Instant.now().toEpochMilli())
                result.sourcesMarkedMissing += staleIds.size
            }
        }

        telegramDao.updateSyncCheckpoint(
            accountId = accountId,
            chatId = selected.chatId,
            newestMessageId = newestScanned.takeIf { it > 0L },
            initialScanComplete = true,
            syncedAt = Instant.now().toEpochMilli(),
        )
        syncChatPlaylist(accountId, selected.chatId, selected.title)
        return result
    }

    /** Returns true for a new Track, false for an existing message update, and null for non-music. */
    private suspend fun persistTelegramMessage(accountId: String, message: TdApi.Message): Boolean? {
        val candidate = message.toAudioCandidate() ?: return null
        val now = Instant.now().toEpochMilli()
        return database.withTransaction {
            val existingTelegram = telegramDao.telegramSourceForMessage(accountId, message.chatId, message.id)
            val existingSource = existingTelegram?.let { libraryDao.sourceById(it.trackSourceId) }
            val existingTrack = existingSource?.let { libraryDao.trackById(it.trackId) }
            val created = existingSource == null
            val normalizedTitle = TextNormalizer.normalize(candidate.title) ?: "unknown track"
            val normalizedArtist = TextNormalizer.normalize(candidate.artist)
            val trackId = existingTrack?.id ?: existingSource?.trackId ?: findMatchingTrack(
                IncomingTrackIdentity(
                    normalizedTitle = normalizedTitle,
                    normalizedArtist = normalizedArtist,
                    durationMs = candidate.durationMs,
                ),
            ) ?: UUID.randomUUID().toString()
            val sourceId = existingSource?.id ?: UUID.randomUUID().toString()
            val canonicalTrack = existingTrack ?: libraryDao.trackById(trackId)

            libraryDao.upsertTrack(
                TrackEntity(
                    id = trackId,
                    title = candidate.title,
                    normalizedTitle = normalizedTitle,
                    artist = candidate.artist,
                    normalizedArtist = TextNormalizer.normalize(candidate.artist),
                    album = canonicalTrack?.album,
                    durationMs = candidate.durationMs.takeIf { it > 0L } ?: canonicalTrack?.durationMs ?: 0L,
                    trackNumber = canonicalTrack?.trackNumber,
                    year = canonicalTrack?.year,
                    artworkRef = canonicalTrack?.artworkRef,
                    favorite = canonicalTrack?.favorite ?: false,
                    hidden = canonicalTrack?.hidden ?: false,
                    createdAtEpochMs = canonicalTrack?.createdAtEpochMs ?: now,
                    updatedAtEpochMs = now,
                ),
            )
            libraryDao.upsertSource(
                TrackSourceEntity(
                    id = sourceId,
                    trackId = trackId,
                    type = TrackSourceType.TELEGRAM_REMOTE,
                    availability = SourceAvailability.REMOTE_ONLY,
                    contentUri = null,
                    localPath = null,
                    mimeType = candidate.mimeType,
                    fileSizeBytes = candidate.fileSizeBytes,
                    contentHashSha256 = existingSource?.contentHashSha256,
                    trainingEligible = existingSource?.trainingEligible ?: true,
                    createdAtEpochMs = existingSource?.createdAtEpochMs ?: now,
                    lastVerifiedAtEpochMs = now,
                ),
            )
            telegramDao.upsertTelegramTrackSource(
                TelegramTrackSourceEntity(
                    trackSourceId = sourceId,
                    accountId = accountId,
                    chatId = message.chatId,
                    messageId = message.id,
                    tdFileId = candidate.fileId,
                    tdPersistentFileId = candidate.persistentFileId,
                    fileName = candidate.fileName,
                    telegramTitle = candidate.title,
                    telegramPerformer = candidate.artist,
                    remoteRevisionKey = null,
                ),
            )
            created
        }
    }

    override suspend fun trackIdsForChat(chatId: Long): List<UUID> {
        val accountId = currentUserId?.toString() ?: _musicSourceState.value.accountId ?: return emptyList()
        return telegramDao.activeTrackIdsForChat(accountId, chatId)
            .mapNotNull { trackId -> runCatching { UUID.fromString(trackId) }.getOrNull() }
    }

    private suspend fun ensureChatPlaylist(accountId: String, chatId: Long, title: String) {
        val now = Instant.now().toEpochMilli()
        playlistDao.upsertPlaylist(
            PlaylistEntity(
                id = telegramPlaylistId(accountId, chatId).toString(),
                title = title.ifBlank { "Telegram" },
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    private suspend fun syncChatPlaylist(accountId: String, chatId: Long, title: String) {
        ensureChatPlaylist(accountId, chatId, title)
        val playlistId = telegramPlaylistId(accountId, chatId).toString()
        val now = Instant.now().toEpochMilli()
        val trackIds = telegramDao.activeTrackIdsForChat(accountId, chatId)
        playlistDao.clearTracks(playlistId)
        if (trackIds.isNotEmpty()) {
            playlistDao.upsertTracks(
                trackIds.mapIndexed { index, trackId ->
                    PlaylistTrackEntity(playlistId, trackId, index, now)
                },
            )
        }
    }

    private fun localChatPhotoRef(chat: TdApi.Chat): String? {
        val photo = chat.photo?.small ?: return null
        if (!photo.local.isDownloadingCompleted || photo.local.path.isBlank()) return null
        return File(photo.local.path).takeIf(File::isFile)?.let { Uri.fromFile(it).toString() }
    }

    private suspend fun resolveChatPhotoRef(activeClient: TdLibClientAdapter, chat: TdApi.Chat): String? {
        localChatPhotoRef(chat)?.let { return it }
        val photo = chat.photo?.small ?: return null
        val resolved = runCatching {
            activeClient.send(TdApi.DownloadFile(photo.id, CHAT_PHOTO_PRIORITY, 0L, 0L, true))
        }.getOrNull() ?: return null
        val path = resolved.local.path.takeIf {
            resolved.local.isDownloadingCompleted && it.isNotBlank()
        } ?: return null
        return File(path).takeIf(File::isFile)?.let { Uri.fromFile(it).toString() }
    }

    private suspend fun findMatchingTrack(incoming: IncomingTrackIdentity): String? {
        val artist = incoming.normalizedArtist ?: return null
        val candidates = libraryDao.matchingTracks(incoming.normalizedTitle, artist).map { track ->
            TrackMatchCandidate(
                trackId = track.id,
                normalizedTitle = track.normalizedTitle,
                normalizedArtist = track.normalizedArtist,
                durationMs = track.durationMs,
            )
        }
        return UnifiedTrackMatcher.match(incoming, candidates)
    }

    private fun submitParameters() {
        val apiId = requireNotNull(BuildConfig.TELEGRAM_API_ID.toIntOrNull())
        val root = File(context.noBackupFilesDir, "tdlib").apply { mkdirs() }
        val databaseDirectory = File(root, "database").apply { mkdirs() }
        val filesDirectory = File(root, "files").apply { mkdirs() }
        submitRequest {
            TdApi.SetTdlibParameters(
                false,
                databaseDirectory.absolutePath,
                filesDirectory.absolutePath,
                byteArrayOf(),
                true,
                true,
                false,
                false,
                apiId,
                BuildConfig.TELEGRAM_API_HASH,
                Locale.getDefault().toLanguageTag(),
                Build.MODEL,
                Build.VERSION.RELEASE,
                BuildConfig.VERSION_NAME,
            )
        }
    }

    private fun <R : TdApi.Object> submit(value: String, emptyMessage: String, request: (String) -> TdApi.Function<R>) {
        if (value.isBlank()) {
            showError(emptyMessage)
            return
        }
        submitRequest { request(value.trim()) }
    }

    private fun <R : TdApi.Object> submitRequest(request: () -> TdApi.Function<R>) {
        val activeClient = client
        if (activeClient == null) {
            showError("Telegram is not ready yet.")
            return
        }
        val stepAtRequest = _authState.value.step
        _authState.update { it.copy(isSubmitting = true, errorMessage = null) }
        scope.launch {
            runCatching { activeClient.send(request()) }
                .onSuccess {
                    _authState.update { current ->
                        if (current.step == stepAtRequest) current.copy(isSubmitting = false) else current
                    }
                }
                .onFailure { error ->
                    _authState.update { current -> current.copy(isSubmitting = false, errorMessage = safeMessage(error)) }
                }
        }
    }

    private fun showError(message: String) {
        _authState.update { it.copy(isSubmitting = false, errorMessage = message) }
    }

    private fun showMusicSourceError(message: String) {
        _musicSourceState.update { it.copy(isLoadingChats = false, isSyncing = false, errorMessage = message) }
    }

    private fun safeMessage(error: Throwable): String = error.message?.takeIf(String::isNotBlank) ?: "Telegram request failed."

    private companion object {
        const val CHAT_PHOTO_PRIORITY = 2
    }
}

private data class LoadedSelectableChats(
    val rawChats: List<TdApi.Chat>,
    val summaries: List<TelegramChatSummary>,
)

private data class MutableSyncResult(
    var chatsSynced: Int = 0,
    var messagesScanned: Int = 0,
    var tracksImported: Int = 0,
    var tracksUpdated: Int = 0,
    var sourcesMarkedMissing: Int = 0,
) {
    fun add(other: MutableSyncResult) {
        chatsSynced += other.chatsSynced
        messagesScanned += other.messagesScanned
        tracksImported += other.tracksImported
        tracksUpdated += other.tracksUpdated
        sourcesMarkedMissing += other.sourcesMarkedMissing
    }

    fun toDomain() = TelegramSyncResult(chatsSynced, messagesScanned, tracksImported, tracksUpdated, sourcesMarkedMissing)
}

private fun TdApi.Chat.toSummary(
    currentUserId: Long,
    selected: Boolean,
    profilePhotoRef: String?,
): TelegramChatSummary {
    val kind = when (val chatType = type) {
        is TdApi.ChatTypePrivate -> if (chatType.userId == currentUserId) TelegramChatKind.SAVED_MESSAGES else TelegramChatKind.PRIVATE
        is TdApi.ChatTypeBasicGroup -> TelegramChatKind.BASIC_GROUP
        is TdApi.ChatTypeSupergroup -> TelegramChatKind.SUPERGROUP_OR_CHANNEL
        is TdApi.ChatTypeSecret -> TelegramChatKind.SECRET
        else -> TelegramChatKind.PRIVATE
    }
    return TelegramChatSummary(
        chatId = id,
        title = title.ifBlank { "Untitled chat" },
        kind = kind,
        selected = selected,
        profilePhotoRef = profilePhotoRef,
    )
}

internal fun TdApi.AuthorizationState.toDomainStep(): TelegramAuthStep = when (this) {
    is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramAuthStep.WaitPhoneNumber
    is TdApi.AuthorizationStateWaitCode -> TelegramAuthStep.WaitCode(phoneNumber = codeInfo.phoneNumber, timeoutSeconds = codeInfo.timeout)
    is TdApi.AuthorizationStateWaitPassword -> TelegramAuthStep.WaitPassword(
        hint = passwordHint,
        hasRecoveryEmail = hasRecoveryEmailAddress,
        recoveryEmailPattern = recoveryEmailAddressPattern,
    )
    is TdApi.AuthorizationStateWaitEmailAddress -> TelegramAuthStep.WaitEmailAddress
    is TdApi.AuthorizationStateWaitEmailCode -> TelegramAuthStep.WaitEmailCode(emailPattern = codeInfo.emailAddressPattern, codeLength = codeInfo.length)
    is TdApi.AuthorizationStateWaitRegistration -> TelegramAuthStep.WaitRegistration
    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> TelegramAuthStep.WaitOtherDeviceConfirmation(link)
    is TdApi.AuthorizationStateReady -> TelegramAuthStep.Ready
    is TdApi.AuthorizationStateLoggingOut -> TelegramAuthStep.LoggingOut
    is TdApi.AuthorizationStateClosing -> TelegramAuthStep.Closing
    is TdApi.AuthorizationStateClosed -> TelegramAuthStep.Closed
    is TdApi.AuthorizationStateWaitPremiumPurchase -> TelegramAuthStep.Unsupported("Telegram Premium is required to complete this login.")
    is TdApi.AuthorizationStateWaitTdlibParameters -> TelegramAuthStep.Initializing
    else -> TelegramAuthStep.Unsupported("This Telegram authorization step is not supported yet.")
}
