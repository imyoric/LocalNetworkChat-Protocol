package ru.yoricya.localnetworkchat;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.MessageDigest;

public class CryptoUtils {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";

    public static byte[] encrypt(byte[] data, byte[] key) {
        try {
            Key secretKey = new SecretKeySpec(key, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static byte[] generateMd5(byte[] inputBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(inputBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decrypt(byte[] encryptedData, byte[] key) {
        try{
            Key secretKey = new SecretKeySpec(key, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(encryptedData);
        }catch (Exception e){
            return new byte[0];
        }
    }
}