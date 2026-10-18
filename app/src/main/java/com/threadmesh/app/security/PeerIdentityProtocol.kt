package com.threadmesh.app.security

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom

/** Identity messages used only to bind a Nearby endpoint to a signing-key fingerprint. */
sealed interface PeerIdentityMessage {
    val senderNodeId: String
    val signature: EventSignature

    data class Challenge(
        override val senderNodeId: String,
        val nonce: String,
        override val signature: EventSignature
    ) : PeerIdentityMessage {
        fun toJson(): String = PeerIdentityProtocol.toJson(
            PeerIdentityProtocol.CHALLENGE_TYPE, senderNodeId, nonce, signature
        )
    }

    data class Response(
        override val senderNodeId: String,
        val challengeNonce: String,
        override val signature: EventSignature
    ) : PeerIdentityMessage {
        fun toJson(): String = PeerIdentityProtocol.toJson(
            PeerIdentityProtocol.RESPONSE_TYPE, senderNodeId, challengeNonce, signature
        )
    }
}

/**
 * A fresh random challenge must be signed by the remote private key. The verifier binds the
 * resulting key fingerprint to the specific live endpoint ID. This proves key possession, not a
 * human name; it does not pin keys across sessions or prevent an active relay/wormhole attack.
 */
object PeerIdentityProtocol {
    const val CHALLENGE_TYPE = "MESH_IDENTITY_CHALLENGE"
    const val RESPONSE_TYPE = "MESH_IDENTITY_RESPONSE"
    const val CHALLENGE_TIMEOUT_MILLIS = 120_000L
    private const val VERSION = 1
    private const val NONCE_BYTES = 32
    private val secureRandom = SecureRandom()
    private val hexPattern = Regex("^[0-9a-f]{64}$")
    private val keyIdPattern = Regex("^[0-9a-f]{64}$")

    fun createChallenge(authenticator: EventAuthenticator): PeerIdentityMessage.Challenge {
        val nonce = randomNonce()
        val senderId = authenticator.localIdentityId()
        val signature = authenticator.signPayload(canonicalBytes(CHALLENGE_TYPE, senderId, nonce))
        return PeerIdentityMessage.Challenge(senderId, nonce, signature)
    }

    fun createResponse(
        authenticator: EventAuthenticator,
        challengeNonce: String
    ): PeerIdentityMessage.Response {
        require(isValidNonce(challengeNonce)) { "Invalid peer challenge nonce" }
        val senderId = authenticator.localIdentityId()
        val signature = authenticator.signPayload(canonicalBytes(RESPONSE_TYPE, senderId, challengeNonce))
        return PeerIdentityMessage.Response(senderId, challengeNonce, signature)
    }

    fun verify(message: PeerIdentityMessage, authenticator: EventAuthenticator): Boolean {
        if (!keyIdPattern.matches(message.senderNodeId)) return false
        if (message.senderNodeId != message.signature.signerKeyId) return false
        val nonce = when (message) {
            is PeerIdentityMessage.Challenge -> message.nonce
            is PeerIdentityMessage.Response -> message.challengeNonce
        }
        if (!isValidNonce(nonce)) return false
        return authenticator.verifyPayload(
            canonicalBytes(messageType(message), message.senderNodeId, nonce),
            message.signature
        )
    }

    fun parse(rawJson: String): PeerIdentityMessage? {
        return try {
            val json = JSONObject(rawJson)
            if (json.optInt("version", -1) != VERSION) return null
            val type = json.optString("type", "")
            if (type != CHALLENGE_TYPE && type != RESPONSE_TYPE) return null
            val sender = json.optString("senderNodeId", "")
            val nonce = if (type == CHALLENGE_TYPE) {
                json.optString("nonce", "")
            } else {
                json.optString("challengeNonce", "")
            }
            val signatureJson = json.optJSONObject("signature") ?: return null
            val signature = EventSignature(
                algorithm = signatureJson.optString("algorithm", ""),
                signerKeyId = signatureJson.optString("signerKeyId", ""),
                publicKeyBase64 = signatureJson.optString("publicKeyBase64", ""),
                signatureBase64 = signatureJson.optString("signatureBase64", ""),
                signedAt = signatureJson.optLong("signedAt", 0L)
            )
            if (!keyIdPattern.matches(sender) || !isValidNonce(nonce)) return null
            if (signature.publicKeyBase64.length > 2048 || signature.signatureBase64.length > 1024) return null
            if (type == CHALLENGE_TYPE) {
                PeerIdentityMessage.Challenge(sender, nonce, signature)
            } else {
                PeerIdentityMessage.Response(sender, nonce, signature)
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun toJson(type: String, senderNodeId: String, nonce: String, signature: EventSignature): String {
        val signatureJson = JSONObject().apply {
            put("algorithm", signature.algorithm)
            put("signerKeyId", signature.signerKeyId)
            put("publicKeyBase64", signature.publicKeyBase64)
            put("signatureBase64", signature.signatureBase64)
            put("signedAt", signature.signedAt)
        }
        return JSONObject().apply {
            put("type", type)
            put("version", VERSION)
            put("senderNodeId", senderNodeId)
            put(if (type == CHALLENGE_TYPE) "nonce" else "challengeNonce", nonce)
            put("signature", signatureJson)
        }.toString()
    }

    private fun messageType(message: PeerIdentityMessage): String = when (message) {
        is PeerIdentityMessage.Challenge -> CHALLENGE_TYPE
        is PeerIdentityMessage.Response -> RESPONSE_TYPE
    }

    private fun isValidNonce(nonce: String): Boolean = hexPattern.matches(nonce)

    private fun randomNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun canonicalBytes(type: String, senderNodeId: String, nonce: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(VERSION)
            writeField(out, "ThreadMesh.PeerIdentity.$type")
            writeField(out, senderNodeId)
            writeField(out, nonce)
        }
        return buffer.toByteArray()
    }

    private fun writeField(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }
}
