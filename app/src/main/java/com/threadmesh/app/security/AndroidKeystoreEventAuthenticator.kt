package com.threadmesh.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.threadmesh.app.data.model.EventEntity
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** ECDSA P-256 signatures backed by Android Keystore; the private key is never exported. */
class AndroidKeystoreEventAuthenticator : EventAuthenticator {
    companion object {
        private const val KEY_ALIAS = "threadmesh.event-signing.ec.v1"
    }

    override fun sign(event: EventEntity): EventSignature {
        val pair = loadOrCreateKeyPair()
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

    private fun loadOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
            return KeyPair(keyStore.getCertificate(KEY_ALIAS).publicKey, privateKey)
        }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKeyPair()
    }
}
