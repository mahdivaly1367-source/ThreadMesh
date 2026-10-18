package com.threadmesh.app.security

import com.threadmesh.app.data.model.EventEntity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * A signature proves control of a cryptographic key, not the real-world identity of its owner.
 * Binding keys to people or accounts requires a separate trust mechanism.
 */
data class EventSignature(
    val algorithm: String,
    val signerKeyId: String,
    val publicKeyBase64: String,
    val signatureBase64: String,
    val signedAt: Long
)

interface EventAuthenticator {
    /** Stable author identity derived from this installation's public signing key. */
    fun localIdentityId(): String
    fun signPayload(payload: ByteArray): EventSignature
    fun verifyPayload(payload: ByteArray, signature: EventSignature?): Boolean
    fun sign(event: EventEntity): EventSignature
    fun verify(event: EventEntity, signature: EventSignature?): Boolean
}

/** Canonical immutable-event encoding and ECDSA verification shared by production and tests. */
object EventSignatureCodec {
    const val ALGORITHM = "SHA256withECDSA"
    private const val FORMAT_VERSION = 1
    private const val MAX_PUBLIC_KEY_BYTES = 1024
    private const val MAX_SIGNATURE_BYTES = 256

    fun canonicalBytes(event: EventEntity): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            writeField(out, event.id)
            writeField(out, event.localAuthorId)
            writeField(out, event.displayName)
            out.writeLong(event.createdAt)
            writeField(out, event.eventType.name)
            writeField(out, event.rootThreadId)
            writeField(out, event.parentEventId)
            writeField(out, event.targetEventId)
            writeField(out, event.text)
            writeField(out, event.publishScope.name)
        }
        return bytes.toByteArray()
    }

    private fun writeField(out: DataOutputStream, value: String?) {
        if (value == null) {
            out.writeInt(-1)
            return
        }
        val encoded = value.toByteArray(Charsets.UTF_8)
        out.writeInt(encoded.size)
        out.write(encoded)
    }

    fun keyId(publicKeyEncoded: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded)
            .joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }

    fun verify(event: EventEntity, signature: EventSignature?): Boolean {
        if (signature == null || event.localAuthorId != signature.signerKeyId) return false
        return verifyPayload(canonicalBytes(event), signature)
    }

    fun verifyPayload(payload: ByteArray, signature: EventSignature?): Boolean {
        if (signature == null || signature.algorithm != ALGORITHM) return false
        return try {
            if (signature.publicKeyBase64.length > 2048 || signature.signatureBase64.length > 1024) return false
            val publicBytes = Base64.getDecoder().decode(signature.publicKeyBase64)
            val signatureBytes = Base64.getDecoder().decode(signature.signatureBase64)
            if (publicBytes.isEmpty() || publicBytes.size > MAX_PUBLIC_KEY_BYTES) return false
            if (signatureBytes.isEmpty() || signatureBytes.size > MAX_SIGNATURE_BYTES) return false
            if (keyId(publicBytes) != signature.signerKeyId) return false

            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicBytes))
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey)
                update(payload)
                verify(signatureBytes)
            }
        } catch (_: Exception) {
            false
        }
    }
}
