package com.muradgalayev.brainbuddy.ui.together

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.TogetherRepository
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NearbyPerson(val endpointId: String, val name: String)

data class NearbyAddState(
    val running: Boolean = false,
    val unavailable: Boolean = false,
    val people: List<NearbyPerson> = emptyList(),
    val relation: ConnectionRelation = ConnectionRelation.FRIEND,
    val sendingTo: NearbyPerson? = null,
    val sentTo: String? = null,
    val failedTo: String? = null,
)

// the radar behind Together's nearby add. every phone with it open both advertises and discovers,
// so two people only have to open the same screen. nothing about the connection is trusted: all it
// carries is the same single-use invite link a chat message would, and the receiver still sees who
// it's from and accepts it on the Together screen before anything is connected
@HiltViewModel
class NearbyAddViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: TogetherRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    // first name only: it's broadcast to anyone nearby with the radar open
    val visibleName: String =
        authRepository.getCurrentUserFullName()?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            ?: authRepository.getCurrentUserUsername()?.takeIf { it.isNotBlank() }
            ?: "Myndora"

    private val client = Nearby.getConnectionsClient(context)

    private val _state = MutableStateFlow(NearbyAddState())
    val state: StateFlow<NearbyAddState> = _state.asStateFlow()

    // endpoints we dialled, with the invite link to send once the connection is up
    private val outgoing = mutableMapOf<String, String>()

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val text = payload.asBytes()?.toString(Charsets.UTF_8) ?: return
            if (text.startsWith(PAYLOAD_PREFIX)) {
                // lands in the same slot a tapped link does, which takes the app to the confirmation
                repository.openPastedInvite(text.removePrefix(PAYLOAD_PREFIX))
            }
            client.disconnectFromEndpoint(endpointId)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (endpointId !in outgoing) return
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> finishSending(endpointId, sent = true)
                PayloadTransferUpdate.Status.FAILURE,
                PayloadTransferUpdate.Status.CANCELED -> finishSending(endpointId, sent = false)
            }
        }
    }

    private val connections = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // accepting only opens the pipe. what arrives is an invite the person still has to confirm
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val url = outgoing[endpointId] ?: return
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                client.sendPayload(endpointId, Payload.fromBytes((PAYLOAD_PREFIX + url).toByteArray(Charsets.UTF_8)))
                    .addOnFailureListener { finishSending(endpointId, sent = false) }
            } else {
                finishSending(endpointId, sent = false)
            }
        }

        override fun onDisconnected(endpointId: String) {
            // the receiver hangs up as soon as it has the link, so this only matters if it never arrived
            if (endpointId in outgoing) finishSending(endpointId, sent = false)
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _state.update { s ->
                if (s.people.any { it.endpointId == endpointId }) s
                else s.copy(people = s.people + NearbyPerson(endpointId, info.endpointName))
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _state.update { s -> s.copy(people = s.people.filterNot { it.endpointId == endpointId }) }
        }
    }

    fun start() {
        if (_state.value.running) return
        _state.update { it.copy(running = true, unavailable = false) }
        val strategy = Strategy.P2P_CLUSTER
        client.startAdvertising(
            visibleName,
            SERVICE_ID,
            connections,
            AdvertisingOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { onStartFailed(it, ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) }
        client.startDiscovery(
            SERVICE_ID,
            discovery,
            DiscoveryOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { onStartFailed(it, ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        outgoing.clear()
        _state.update { it.copy(running = false, people = emptyList(), sendingTo = null) }
    }

    fun setRelation(relation: ConnectionRelation) {
        _state.update { it.copy(relation = relation) }
    }

    fun invite(person: NearbyPerson) {
        if (_state.value.sendingTo != null) return
        _state.update { it.copy(sendingTo = person, sentTo = null, failedTo = null) }
        viewModelScope.launch {
            repository.createInviteLink(_state.value.relation)
                .onSuccess { link ->
                    outgoing[person.endpointId] = link.url
                    client.requestConnection(visibleName, person.endpointId, connections)
                        .addOnFailureListener { finishSending(person.endpointId, sent = false) }
                }
                .onFailure { finishSending(person.endpointId, sent = false, name = person.name) }
        }
    }

    private fun finishSending(endpointId: String, sent: Boolean, name: String? = null) {
        val wasSending = outgoing.remove(endpointId) != null || name != null
        if (!wasSending) return
        val who = name ?: _state.value.sendingTo?.name ?: return
        client.disconnectFromEndpoint(endpointId)
        _state.update {
            it.copy(
                sendingTo = null,
                sentTo = if (sent) who else null,
                failedTo = if (sent) null else who,
            )
        }
    }

    private fun onStartFailed(error: Exception, alreadyRunningCode: Int) {
        if ((error as? ApiException)?.statusCode == alreadyRunningCode) return
        _state.update { it.copy(unavailable = true) }
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val SERVICE_ID = "com.muradgalayev.brainbuddy.together.nearby"
        const val PAYLOAD_PREFIX = "myndora-invite:"
    }
}
