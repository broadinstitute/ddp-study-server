package org.broadinstitute.ddp.security;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

/**
 * Utility class that encrypts API keys.
 */
@Slf4j
public class KeySignerUtil {
    public static final String RSA = "RSA";
    public static final String RSA_CIPHER = "RSA/ECB/PKCS1Padding";

    static {
        int providerReturn = Security.addProvider(new BouncyCastleProvider());
        log.info("BouncyCastle provider added with " + providerReturn + " returned");
    }

    /**
     * Parses a pem object from the given pem formatted data (for example,
     * public key or private key file contents).
     */
    public static PemObject parsePemObject(String pemData) throws IOException {
        PemObject pemObject;
        try (PemReader pemReader = new PemReader(new StringReader(pemData))) {
            pemObject = pemReader.readPemObject();
            if (pemObject == null) {
                throw new RuntimeException("Could not parse pem object");
            }
        }
        return pemObject;
    }

    /**
     * Encrypts the given plaintext with the given public key
     * using RSA.  Returned String is base64 encoded.
     */
    public String encryptAndBase64(String plainText, String publicKeyPem) {
        String encryptedAndBase64Encoded;
        try {
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(parsePemObject(publicKeyPem).getContent());
            Cipher cipher = Cipher.getInstance(RSA_CIPHER);
            PublicKey publicKey = KeyFactory.getInstance(RSA).generatePublic(publicKeySpec);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            encryptedAndBase64Encoded = Base64.encodeBase64String(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
        return encryptedAndBase64Encoded;
    }

    /**
     * Decrypts the given base64-encoded ciphertext using RSA and the given
     * private key.
     */
    public byte[] decryptFromBase64(String base64CipherText, String privateKeyPem) {
        try {
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(parsePemObject(privateKeyPem).getContent());
            Cipher cipher = Cipher.getInstance(RSA_CIPHER);
            PrivateKey privateKey = KeyFactory.getInstance(RSA).generatePrivate(privateKeySpec);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(Base64.decodeBase64(base64CipherText));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
