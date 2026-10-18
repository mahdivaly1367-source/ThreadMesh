package com.threadmesh.app

import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import com.threadmesh.app.security.EventAuthenticator
import com.threadmesh.app.security.EventSignature
import com.threadmesh.app.security.EventSignatureCodec
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class EventSignatureTest {
    private class SoftwareAuthenticator : EventAuthenticator {
        private val pair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        override fun sign(event: EventEntity): EventSignature {
            val signer = Signature.getInstance(EventSignatureCodec.ALGORITHM)
            signer.initSign(pair.private)
            signer.update(EventSignatureCodec.canonicalBytes(event))
            val publicBytes = pair.public.encoded
            return EventSignature(
                algorithm = EventSignatureCodec.ALGORITHM,
                signerKeyId = EventSignatureCodec.keyId(publicBytes),
                publicKeyBase64 = Base64.getEncoder().encodeToString(publicBytes),
                signatureBase64 = Base64.getEncoder().encodeToString(signer.sign()),
                signedAt = System.currentTimeMillis()
            )
        }

        override fun verify(event: EventEntity, signature: EventSignature?): Boolean =
            EventSignatureCodec.verify(event, signature)
    }

    private val authenticator = SoftwareAuthenticator()

    private fun event(text: String = "متن امضاشده") = EventEntity(
        id = "evt-sec-1",
        localAuthorId = "author-1",
        displayName = "کاربر",
        createdAt = 1_800_000_000_000L,
        eventType = EventType.ROOT_THREAD,
        rootThreadId = "evt-sec-1",
        text = text,
        publishScope = PublishScope.MESH_PUBLIC
    )

    @Test
    fun validSignatureVerifies() {
        val event = event()
        assertTrue(authenticator.verify(event, authenticator.sign(event)))
    }

    @Test
    fun changedTextInvalidatesSignature() {
        val event = event()
        val signature = authenticator.sign(event)
        assertFalse(authenticator.verify(event(text = "متن دستکاری‌شده"), signature))
    }

    @Test
    fun missingSignatureIsRejected() {
        assertFalse(authenticator.verify(event(), null))
    }

    @Test
    fun wrongKeyIdIsRejected() {
        val event = event()
        val signature = authenticator.sign(event).copy(signerKeyId = "00")
        assertFalse(authenticator.verify(event, signature))
    }
}
