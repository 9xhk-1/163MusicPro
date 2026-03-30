package com.qinghe.music163pro.api;

import android.util.Base64;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * NetEase Cloud Music API encryption utilities.
 * Implements the weapi encryption scheme used by the official client.
 * Ported from NeteaseCloudMusicApiBackup util/crypto.js
 */
public class NeteaseApiCrypto {

    private static final String IV = "0102030405060708";
    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // RSA public key (PKCS#1 format, same as in crypto.js)
    private static final String PUBLIC_KEY_PEM =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ3" +
            "7BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK" +
            "9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK4" +
            "5wIDAQAB";

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * weapi encryption: double AES-CBC + RSA
     * Returns String[2] = { params, encSecKey }
     */
    public static String[] weapi(String jsonText) {
        // Generate random 16-char secret key from base62
        StringBuilder secretKeyBuilder = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            secretKeyBuilder.append(BASE62.charAt(secureRandom.nextInt(62)));
        }
        String secretKey = secretKeyBuilder.toString();

        // First AES: encrypt plaintext with preset key
        String firstEncrypt = aesEncryptCBC(jsonText, PRESET_KEY, IV);

        // Second AES: encrypt first result with random secret key
        String params = aesEncryptCBC(firstEncrypt, secretKey, IV);

        // RSA encrypt the reversed secret key
        String reversedKey = new StringBuilder(secretKey).reverse().toString();
        String encSecKey = rsaEncrypt(reversedKey);

        return new String[]{ params, encSecKey };
    }

    /**
     * AES-128-CBC encryption with PKCS5Padding, returns Base64
     */
    private static String aesEncryptCBC(String plaintext, String key, String iv) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes("UTF-8"));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * RSA encryption using the NetEase public key with NoPadding
     * Matches the node-forge encrypt(str, 'NONE') behavior
     */
    private static String rsaEncrypt(String plaintext) {
        try {
            byte[] keyBytes = Base64.decode(PUBLIC_KEY_PEM, Base64.NO_WRAP);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);

            // Manual RSA (no padding): m^e mod n
            // Convert plaintext bytes to BigInteger
            byte[] textBytes = plaintext.getBytes("UTF-8");
            // Pad to key size (128 bytes for 1024-bit RSA)
            int keySize = (publicKey.getModulus().bitLength() + 7) / 8;
            byte[] padded = new byte[keySize];
            // Right-align the text bytes (zero-padded on left)
            System.arraycopy(textBytes, 0, padded, keySize - textBytes.length, textBytes.length);

            BigInteger m = new BigInteger(1, padded);
            BigInteger e = publicKey.getPublicExponent();
            BigInteger n = publicKey.getModulus();
            BigInteger c = m.modPow(e, n);

            // Convert to hex string, padded to key size * 2
            String hex = c.toString(16);
            while (hex.length() < keySize * 2) {
                hex = "0" + hex;
            }
            return hex;
        } catch (Exception e) {
            throw new RuntimeException("RSA encryption failed", e);
        }
    }
}
