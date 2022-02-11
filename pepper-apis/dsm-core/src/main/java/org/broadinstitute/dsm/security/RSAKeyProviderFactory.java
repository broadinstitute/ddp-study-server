package org.broadinstitute.dsm.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.interfaces.RSAKeyProvider;
/**
 * A factory class creating specific RSAKeyProvider instances.
 */
public class RSAKeyProviderFactory {
    /**
     * A static factory method creating an instance with just a private key defined.
     */
    public static RSAKeyProvider createRSAKeyProviderWithPrivateKeyOnly(JwkProvider provider) {
        return new RSAKeyProvider() {
            @Override
            public RSAPublicKey getPublicKeyById(String kid) {
                //Received 'kid' value might be null if it wasn't defined in the Token's header
                RSAPublicKey publicKey;
                try {
                    publicKey = (RSAPublicKey) provider.get(kid).getPublicKey();
                }
                catch (JwkException e) {
                    throw new RuntimeException("Error reading jwk", e);
                }
                return publicKey;
            }

            @Override
            public RSAPrivateKey getPrivateKey() {
                return null;
            }

            @Override
            public String getPrivateKeyId() {
                return null;
            }
        };
    }
}