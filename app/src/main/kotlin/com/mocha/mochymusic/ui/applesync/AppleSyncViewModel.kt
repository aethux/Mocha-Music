// AppleSyncViewModel.kt

package com.mocha.mochymusic.ui.applesync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mocha.mochymusic.applesync.AppleAudit
import com.mocha.mochymusic.applesync.AppleAuditReport
import com.mocha.mochymusic.applesync.AppleSyncException
import com.mocha.mochymusic.applesync.AppleTokens
import com.mocha.mochymusic.applesync.AppleWritePrefs
import com.mocha.mochymusic.applesync.db.AppleAuditEntity
import com.mocha.mochymusic.applesync.db.AppleSyncDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * Drives the read-only page: sign in, take the epoch, show the drift report.
 *
 * There is no write action here on purpose. The screen exists so the user can see what the sync sees,
 * and so the promise "nothing of yours changes" has a place to be checked rather than asserted.
 */
@HiltViewModel
class AppleSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: AppleTokens,
    private val audit: AppleAudit,
    private val dao: AppleSyncDao,
) : ViewModel() {

    sealed interface State {
        data object SignedOut : State
        data class SigningIn(val developer: Boolean = false, val user: Boolean = false) : State
        data object Ready : State
        data object Running : State
        data class Done(val report: AppleAuditReport) : State
        data class Failed(val message: String, val signedOut: Boolean) : State
    }

    var state by mutableStateOf<State>(State.SignedOut)
        private set

    /** The account's own name, so the page says who is signed in rather than "Apple ID". */
    var accountLabel by mutableStateOf<String?>(null)
        private set

    /** The switch that lets Mocha change anything on Apple. Off until the user says otherwise. */
    var writesEnabled by mutableStateOf(false)
        private set

    val auditLog: StateFlow<List<AppleAuditEntity>> =
        dao.recentAudit().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var pendingDeveloper: String? = null
    private var pendingUser: String? = null

    init {
        viewModelScope.launch {
            state = if (tokens.read() != null) State.Ready else State.SignedOut
            writesEnabled = AppleWritePrefs.enabled(context)
            // the account row's own label is empty on a captured session; the name Apple uses as
            // the owner of this account's playlists is the same person and is always there
            accountLabel = runCatching { dao.account().first()?.label?.takeIf { it.isNotBlank() } }.getOrNull()
                ?: runCatching {
                    com.mocha.mochymusic.applesync.PlaylistCollaborators.selfName(dao.playlistMetas())
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
        }
    }

    /**
     * The session page writes the tokens while this page sits in the back stack, and the view model
     * outlives it - so the state read once in [init] still says signed out when the person comes back.
     */
    fun refreshSession() {
        if (state is State.SigningIn || state is State.Running || state is State.Done) return
        viewModelScope.launch {
            state = if (tokens.read() != null) State.Ready else State.SignedOut
            accountLabel = runCatching { dao.account().first()?.label?.takeIf { it.isNotBlank() } }.getOrNull()
                ?: runCatching {
                    com.mocha.mochymusic.applesync.PlaylistCollaborators.selfName(dao.playlistMetas())
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
        }
    }

    fun setWrites(on: Boolean) {
        viewModelScope.launch {
            AppleWritePrefs.set(context, on)
            writesEnabled = on
        }
    }

    fun startSignIn() {
        pendingDeveloper = null
        pendingUser = null
        state = State.SigningIn()
    }

    fun cancelSignIn() {
        state = if (savedTokensPresent()) State.Ready else State.SignedOut
    }

    /**
     * The web player's own requests carry both tokens, and the same hook the importer uses reports
     * them here. Nothing is stored until both halves have arrived, so a half-captured session can
     * never be mistaken for a signed-in one.
     */
    fun onHeader(name: String, value: String, url: String) {
        when (name.trim().lowercase()) {
            "authorization" -> {
                val token = value.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
                if (token.startsWith("eyJ")) {
                    pendingDeveloper = token
                    reportPending()
                }
            }
            "media-user-token" -> if (value.trim().length >= 20) {
                pendingUser = value.trim()
                reportPending()
            }
        }
    }

    /** MusicKit in the page knows the pair even before it makes a request. */
    fun onAppleProbe(raw: String?) {
        if (raw.isNullOrBlank() || raw == "null") return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        json.optString("dev").takeIf { it.length > 20 && it != "null" }?.let { pendingDeveloper = it }
        json.optString("user").takeIf { it.length > 20 && it != "null" }?.let { pendingUser = it }
        reportPending()
    }

    private fun reportPending() {
        val developer = pendingDeveloper ?: return
        val user = pendingUser ?: return
        viewModelScope.launch {
            tokens.save(developer, user)
            state = State.Ready
        }
    }

    private fun savedTokensPresent(): Boolean = state is State.SigningIn && pendingUser != null

    fun runAudit() {
        if (state is State.Running) return
        state = State.Running
        viewModelScope.launch {
            state = try {
                State.Done(audit.run())
            } catch (e: AppleSyncException) {
                State.Failed(e.message ?: "The audit failed.", e.signedOut)
            } catch (e: Exception) {
                State.Failed(e.message ?: "The audit failed.", signedOut = false)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            tokens.clear()
            pendingDeveloper = null
            pendingUser = null
            state = State.SignedOut
        }
    }
}
