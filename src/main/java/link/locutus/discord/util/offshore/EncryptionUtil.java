package link.locutus.discord.util.offshore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class EncryptionUtil {
    public enum Algorithm {
        DEFAULT("AES/CFB/PKCS5PADDING") {
            @Override
            public void init(Cipher cipher, int decryptMode, SecretKeySpec secretKey) throws InvalidAlgorithmParameterException, InvalidKeyException {
                byte[] iv = new byte[] { 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF };
                IvParameterSpec ivSpec = new IvParameterSpec(iv);
                cipher.init(decryptMode, secretKey, ivSpec);
            }
        },
        LEGACY("AES/ECB/PKCS5PADDING");

        public final String value;

        Algorithm(String value) {
            this.value = value;
        }

        public void init(Cipher cipher, int decryptMode, SecretKeySpec secretKey) throws InvalidKeyException, InvalidAlgorithmParameterException {
            cipher.init(decryptMode, secretKey);
        }
    }
    public static byte[] generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256); // for example
        SecretKey secretKey = keyGen.generateKey();
        return secretKey.getEncoded();
    }

    public static byte[] encrypt2(byte[] input, byte[] key) throws Exception {
        return encrypt2(input, key, Algorithm.DEFAULT);
    }

    public static byte[] encrypt2(byte[] input, byte[] key, Algorithm algorithm) throws Exception
    {
        Cipher cipher = Cipher.getInstance(algorithm.value);
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        algorithm.init(cipher, Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(input);
    }

    public static byte[] decrypt2(byte[] input, byte[] key) throws Exception {
        return decrypt2(input, key, Algorithm.DEFAULT);
    }
    public static byte[] decrypt2(byte[] input, byte[] key, Algorithm algorithm) throws Exception
    {
        Cipher cipher = Cipher.getInstance(algorithm.value);
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        algorithm.init(cipher, Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(input);
    }
}
