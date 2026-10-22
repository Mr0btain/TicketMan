package org.mr0btain.ticketman.ui.screens.login.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.ConnectionResult
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.UntrustedCertificate
import org.mr0btain.ticketman.components.network.isSecureServerAddress
import org.mr0btain.ticketman.components.network.trustAddressOf
import org.mr0btain.ticketman.components.network.user.LoginOutcome
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.components.network.isNotFound
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.server.request.AccountSetupRequest
import org.mr0btain.ticketman.server.request.InviteSetupRequest

/**
 * Who is logged in and how they got there: connecting to the server, password login and logout, account
 * requests and first login setup, and this device's station
 * @param onLoggedIn Loads what someone who just logged in should see, also run after changing servers
 * @param onLoggedOut Forgets anything loaded for whoever was logged in
 */
class SessionHandler(
    private val remoteHandler: RemoteHandler,
    private val localStorage: LocalStorage,
    private val scope: CoroutineScope,
    private val onLoggedIn: suspend () -> Unit,
    private val onLoggedOut: () -> Unit,
    /** The server moved this device to another address (plain HTTP redirected to HTTPS), for saving it */
    private val onServerMoved: (String) -> Unit = {}
) {
    private val _loginState: MutableStateFlow<LoginState> = MutableStateFlow(LoginState.LOGGED_OUT)
    val loginState = _loginState.asStateFlow()

    // Who is logged in, null when logged out
    private val _currentUser: MutableStateFlow<User?> = MutableStateFlow(null)
    val currentUser = _currentUser.asStateFlow()

    /** Whether the logged in user can change things, restricted users can only look. The server checks this too */
    val canEdit: StateFlow<Boolean> = _currentUser
        .map { user -> user?.canAccess(AccessLevel.NORMAL) == true }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Whether the logged in user can use the admin screen. The server checks this too, this just hides things */
    val isAdmin: StateFlow<Boolean> = _currentUser
        .map { user -> user?.canAccess(AccessLevel.ADMIN) == true }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _showLogin: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showLogin = _showLogin.asStateFlow()

    // Shown on the login form, like when an account is still waiting for approval
    private val _loginMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val loginMessage = _loginMessage.asStateFlow()

    // First login setup waiting to be finished, null when there isn't one
    private val _accountSetup: MutableStateFlow<AccountSetup?> = MutableStateFlow(null)
    val accountSetup = _accountSetup.asStateFlow()

    // Whether the server at the current url could be reached
    private val _serverConnected: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val serverConnected = _serverConnected.asStateFlow()

    private val _connectionProblem: MutableStateFlow<String?> = MutableStateFlow(null)

    /**
     * Why the last address tried didn't connect, to show next to it. Null when it connected, when nothing was tried,
     * and when its certificate is waiting to be trusted (the trust dialog asks instead)
     */
    val connectionProblem = _connectionProblem.asStateFlow()

    // Set when the server and this app are from different versions, see RemoteHandler.apiMismatch
    private val _apiMismatch: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Why this app and the server may not work together, null when they're the same version */
    val apiMismatch = _apiMismatch.asStateFlow()

    // The address connected to last, so trusting its certificate can try it again
    private val _serverUrl: MutableStateFlow<String> = MutableStateFlow("")

    /**
     * False when the server is reached over plain HTTP from another machine, where passwords and tokens can be read
     * on the network
     */
    val connectionSecure: StateFlow<Boolean> = _serverUrl
        .map { isSecureServerAddress(it) }
        .stateIn(scope, SharingStarted.Eagerly, true)

    /** Why the connection to the server isn't safe, while connected to one over plain HTTP. Null otherwise */
    val insecureWarning: StateFlow<String?> = combine(connectionSecure, _serverConnected) { secure, connected ->
        if (connected && !secure) INSECURE_MESSAGE else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // Certificates someone chose not to trust, so they aren't asked again until the app restarts
    private val dismissedCertificates: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    /**
     * The certificate the current server answered with, when this device doesn't trust it yet and nobody has
     * turned it down. Null otherwise
     */
    val untrustedCertificate: StateFlow<UntrustedCertificate?> = combine(
        remoteHandler.trust.refused, _serverUrl, dismissedCertificates
    ) { refused, url, dismissed ->
        refused?.takeIf { it.address == trustAddressOf(url) && it.fingerprint !in dismissed }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // This device's station record, null when it isn't one
    private val _station: MutableStateFlow<Station?> = MutableStateFlow(null)
    val station = _station.asStateFlow()

    /** Username saved from the last login, blank unless remember login is on */
    val savedUsername: String
        get() = localStorage.username

    /** Whether the logged in user can change things right now, for handlers that check before acting */
    fun canEditNow(): Boolean = _currentUser.value?.canAccess(AccessLevel.NORMAL) == true

    fun isAdminNow(): Boolean = _currentUser.value?.canAccess(AccessLevel.ADMIN) == true

    /** Update whether we should show the login popup or not */
    fun changeShowLogin(value: Boolean) {
        _showLogin.value = value
    }

    /** Moves to another server, then reloads what's shown from it */
    fun changeServer(url: String) {
        _loginState.value = LoginState.LOADING

        scope.launch {
            connect(url)
            onLoggedIn()
        }
    }

    /** Points every repository at [url] and logs in with any stored credentials */
    suspend fun connect(url: String) {
        _serverUrl.value = url
        val result = remoteHandler.setUrl(url)

        // Kept even when the certificate still needs trusting, since trusting connects to this address again
        val address = remoteHandler.address
        if (address.isNotBlank() && address != url) {
            _serverUrl.value = address
            onServerMoved(address)
        }

        _serverConnected.value = result == ConnectionResult.CONNECTED || result == ConnectionResult.LOGGED_IN
        _connectionProblem.value = when (result) {
            ConnectionResult.UNREACHABLE -> if (url.isBlank()) null else UNREACHABLE_MESSAGE
            ConnectionResult.WRONG_NAME -> remoteHandler.trust.wrongName.value?.message
            ConnectionResult.SERVER_NOT_ANSWERING -> SERVER_NOT_ANSWERING_MESSAGE
            ConnectionResult.UNTRUSTED, ConnectionResult.CONNECTED, ConnectionResult.LOGGED_IN -> null
        }
        _apiMismatch.value = if (_serverConnected.value) remoteHandler.apiMismatch else null

        if (result == ConnectionResult.LOGGED_IN) {
            _loginState.value = LoginState.LOGGED_IN
            _showLogin.value = false
            reloadCurrentUser()
        } else {
            _loginState.value = LoginState.LOGGED_OUT
            _currentUser.value = null
        }

        // Station status only needs a reachable server, not a login
        if (_serverConnected.value) refreshStation()
    }

    /** Trusts the certificate in [untrustedCertificate] for this server from now on, then connects again */
    fun trustServerCertificate() {
        val certificate = untrustedCertificate.value ?: return
        remoteHandler.trust.trust(certificate)
        changeServer(_serverUrl.value)
    }

    /** Leaves the server unconnected, and stops asking about this certificate until the app restarts */
    fun dismissServerCertificate() {
        val certificate = untrustedCertificate.value ?: return
        dismissedCertificates.value += certificate.fingerprint
    }

    /** Pulls the logged in user again, like after they were changed */
    suspend fun reloadCurrentUser() {
        val user = remoteHandler.userRepo.getMe()
        _currentUser.value = user

        // Approved but not set up yet, they have to finish before anything else
        if (user?.needsSetup == true) _accountSetup.value = AccountSetup.Approved(user)
    }

    /** Logs in with the given credentials. Credentials are only saved if remember login is on */
    fun login(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            _loginState.value = LoginState.FAILED
            return
        }

        _loginState.value = LoginState.LOADING

        scope.launch {
            _loginMessage.value = null

            when (val outcome = remoteHandler.login(user.trim(), pass)) {
                is LoginOutcome.LoggedIn -> {
                    _loginState.value = LoginState.LOGGED_IN
                    _showLogin.value = false
                    reloadCurrentUser()
                    if (!outcome.needsSetup) onLoggedIn()
                }

                LoginOutcome.PendingApproval -> {
                    _loginState.value = LoginState.LOGGED_OUT
                    _loginMessage.value = "Your account is waiting for an admin to approve it."
                }

                is LoginOutcome.InviteSetup -> {
                    _loginState.value = LoginState.LOGGED_OUT
                    _showLogin.value = false
                    _accountSetup.value = AccountSetup.Invite(outcome.username, outcome.setupToken)
                }

                is LoginOutcome.Failed -> {
                    _loginState.value = LoginState.FAILED
                    _loginMessage.value = if (outcome.error == NetworkError.UNAUTHORIZED) {
                        "Wrong username, password or setup code."
                    } else outcome.error.message
                }
            }
        }
    }

    /** Ends the session and clears out any loaded data */
    fun logout() {
        scope.launch {
            remoteHandler.logout()
            signedOut()
        }
    }

    /** Someone signed in another way, like a PIN at a kiosk */
    suspend fun signedIn() {
        _loginState.value = LoginState.LOGGED_IN
        reloadCurrentUser()
        onLoggedIn()
    }

    /** The session ended without a logout, like a kiosk locking */
    fun signedOut() {
        _loginState.value = LoginState.LOGGED_OUT
        _currentUser.value = null
        onLoggedOut()
    }

    /** The session couldn't be refreshed, ask for a login again */
    fun expired() {
        _loginState.value = LoginState.LOGGED_OUT
        _currentUser.value = null
        _showLogin.value = true
    }

    /**
     * Asks for an account from the login screen
     * @param onResult Gets null when it went through, otherwise what went wrong
     */
    fun requestAccount(request: AccountRequest, onResult: (String?) -> Unit) {
        scope.launch {
            when (val result = remoteHandler.userClient.requestAccount(request)) {
                is TmResult.Success -> {
                    _loginMessage.value = "Request sent. An admin has to approve it before you can log in."
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /**
     * Finishes whichever setup is waiting
     * @param password Required for invites, optional for approved requests
     * @param onResult Gets null when it went through, otherwise what went wrong
     */
    fun completeAccountSetup(name: String, email: String, phone: String, password: String, pin: String, onResult: (String?) -> Unit) {
        val setup = _accountSetup.value ?: return

        scope.launch {
            val result = when (setup) {
                is AccountSetup.Approved -> remoteHandler.userRepo.completeSetup(
                    AccountSetupRequest(name = name, email = email, phone = phone, password = password, pin = pin)
                )

                is AccountSetup.Invite -> remoteHandler.completeInviteSetup(
                    setup.username,
                    InviteSetupRequest(setup.setupToken, password = password, name = name, email = email, phone = phone, pin = pin)
                )
            }

            when (result) {
                is TmResult.Success -> {
                    _accountSetup.value = null
                    _loginState.value = LoginState.LOGGED_IN
                    _loginMessage.value = null
                    reloadCurrentUser()
                    onLoggedIn()
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Backs out of setup, logging out if they were logged in for it */
    fun cancelAccountSetup() {
        val setup = _accountSetup.value
        _accountSetup.value = null

        if (setup is AccountSetup.Approved) logout()
    }

    /**
     * Changes your own station PIN
     * @param onResult Gets null when it went through, otherwise what went wrong
     */
    fun changePin(currentPassword: String, pin: String, onResult: (String?) -> Unit) {
        scope.launch {
            when (val result = remoteHandler.userRepo.setOwnPin(currentPassword, pin)) {
                is TmResult.Success -> {
                    reloadCurrentUser()
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Asks the server about this device's station, forgetting the credentials if the server no longer has it */
    fun refreshStation() {
        val credentials = localStorage.stationCredentials ?: run {
            _station.value = null
            return
        }

        scope.launch {
            when (val status = remoteHandler.userClient.stationStatus(credentials)) {
                is TmResult.Success -> _station.value = status.data
                is TmResult.Error -> {
                    _station.value = null
                    // The server has no such station any more, so stop asking. Couldn't connect keeps them for next time
                    if (status.error.isNotFound) localStorage.stationCredentials = null
                }
            }
        }
    }

    /**
     * Registers this device as a station, which an admin then approves
     * @param onResult Gets null when it went through, otherwise what went wrong
     */
    fun registerStation(name: String, onResult: (String?) -> Unit) {
        scope.launch {
            when (val result = remoteHandler.adminRepo.registerStation(localStorage.deviceId, name.trim())) {
                is TmResult.Success -> {
                    localStorage.stationCredentials = result.data.credentials
                    _station.value = result.data.station
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    companion object {
        const val UNREACHABLE_MESSAGE = "Couldn't reach a server there. Check the address and that the server is running."
        const val INSECURE_MESSAGE = "This server is on plain HTTP, so passwords and customer details can be read by " +
            "anyone on the network. Ask your admin for its https:// address."
        const val SERVER_NOT_ANSWERING_MESSAGE = "The proxy is up but the TicketMan server isn't answering. Check the server is running."
    }
}
