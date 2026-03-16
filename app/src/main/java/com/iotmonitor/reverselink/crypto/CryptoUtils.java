package com.iotmonitor.reverselink.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    public static KeyPair generateECDHKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        return kpg.generateKeyPair();
    }

    public static byte[] deriveSharedSecret(PrivateKey privateKey, String base64PublicKey) throws Exception {
        byte[] publicKeyBytes = Base64.getDecoder().decode(base64PublicKey);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey pubKey = keyFactory.generatePublic(x509KeySpec);

        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(pubKey, true);
        
        byte[] secret = keyAgreement.generateSecret();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return sha256.digest(secret);
    }

    public static String encrypt(String plainText, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        byte[] cipherText = cipher.doFinal(plainText.getBytes());

        byte[] cipherTextWithIv = new byte[GCM_IV_LENGTH + cipherText.length];
        System.arraycopy(iv, 0, cipherTextWithIv, 0, GCM_IV_LENGTH);
        System.arraycopy(cipherText, 0, cipherTextWithIv, GCM_IV_LENGTH, cipherText.length);

        return Base64.getEncoder().encodeToString(cipherTextWithIv);
    }

    public static String decrypt(String cipherTextWithIvBase64, byte[] key) throws Exception {
        byte[] cipherTextWithIv = Base64.getDecoder().decode(cipherTextWithIvBase64);
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(cipherTextWithIv, 0, iv, 0, GCM_IV_LENGTH);

        int cipherTextLen = cipherTextWithIv.length - GCM_IV_LENGTH;
        byte[] cipherText = new byte[cipherTextLen];
        System.arraycopy(cipherTextWithIv, GCM_IV_LENGTH, cipherText, 0, cipherTextLen);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        byte[] plainTextBytes = cipher.doFinal(cipherText);
        
        return new String(plainTextBytes);
    }
}
