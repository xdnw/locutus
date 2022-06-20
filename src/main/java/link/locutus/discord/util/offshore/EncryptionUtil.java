package link.locutus.discord.util.offshore;

import com.google.api.client.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class EncryptionUtil {
    public enum Algorithm {
        DEFAULT("AES/CFB/PKCS5PADDING"),
        LEGACY("AES/ECB/PKCS5PADDING");

        public final String value;

        Algorithm(String value) {
            this.value = value;
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
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(input);
    }

    public static byte[] decrypt2(byte[] input, byte[] key) throws Exception {
        return decrypt2(input, key, Algorithm.DEFAULT);
    }
    public static byte[] decrypt2(byte[] input, byte[] key, Algorithm algorithm) throws Exception
    {
        Cipher cipher = Cipher.getInstance(algorithm.value);
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(input);
    }
}
