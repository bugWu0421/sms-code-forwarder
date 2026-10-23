package com.wj.smsforwarder

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Webhook 密钥的静态加密：AndroidKeyStore 内生成 AES-256 主密钥（不可导出），
 * 密钥字段以 AES/GCM 加密后落 SharedPreferences，防止 root/备份场景下明文泄露。
 *
 * 全部防御式：任何 Keystore 异常都回退明文存储，绝不影响转发主链路。
 * 存储格式：enc:<Base64(iv)>:<Base64(密文)>；非该前缀视为旧版明文，读取后下次保存自动升级。
 */
object SecretCrypto {

    private const val KEY_ALIAS = "sms_forwarder_master"
    private const val PREFIX = "enc:"

    /** 返回 null 表示加密不可用，调用方应回退明文 */
    fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return plain
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ct = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            PREFIX + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                    ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    /** 输入非 enc: 前缀或解密失败时返回 null，调用方按明文处理 */
    fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return null
        return try {
            val parts = stored.removePrefix(PREFIX).split(':')
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
