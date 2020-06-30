package org.broadinstitute.ddp.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.typesafe.config.Config;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigManager;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class AesUtil {

    private static final String AES_ECB_PKCS_5_PADDING_CIPHER = "AES/ECB/PKCS5Padding";
    private static final String AES_CBC_PKCS_5_PADDING_CIPHER = "AES/CBC/PKCS5Padding";
    private static final String UTF_8_ENCODING = "UTF-8";
    private static final String MESSAGE_DIGEST_ALGORITHM = "SHA-256";
    private static final String ENCRYPTION_TYPE = "AES";
    private static final int IV_SIZE = 16;
    private static final int KEY_SIZE = 16;
    private static final String SELECT_CLIENT_TENANT_AND_SIGNING_SECRET_QUERY =
            "    SELECT "
            + "      auth0_client_id, auth0_tenant_id, auth0_signing_secret"
            + "  FROM"
            + "      client";
    private static final String UPDATE_SIGNING_SECRET_QUERY =
            "  UPDATE client "
            + "SET auth0_signing_secret = :auth0_signing_secret "
            + "WHERE auth0_client_id = :auth0_client_id "
            + "AND auth0_tenant_id = :auth0_tenant_id";

    private static final String USAGE = "AesUtil [-h, --help] [OPTIONS]";

    public static String encrypt(String strToEncrypt, String key) {
        try {
            byte[] clean = strToEncrypt.getBytes();

            // Generating IV.
            byte[] iv = new byte[IV_SIZE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            // Hashing key.
            MessageDigest digest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);
            digest.update(key.getBytes(UTF_8_ENCODING));
            byte[] keyBytes = new byte[KEY_SIZE];
            System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ENCRYPTION_TYPE);

            // Encrypt.
            Cipher cipher = Cipher.getInstance(AES_CBC_PKCS_5_PADDING_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] encrypted = cipher.doFinal(clean);

            // Combine IV and encrypted part.
            byte[] encryptedIVAndText = new byte[IV_SIZE + encrypted.length];
            System.arraycopy(iv, 0, encryptedIVAndText, 0, IV_SIZE);
            System.arraycopy(encrypted, 0, encryptedIVAndText, IV_SIZE, encrypted.length);


            return Base64.getEncoder().encodeToString(encryptedIVAndText);
        } catch (Exception e) {
            throw new DDPException("Error while encrypting", e);
        }
    }

    private static String decryptECB(String strToDecrypt, String secret) {
        try {
            // Hashing key.
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(secret.getBytes(UTF_8_ENCODING));
            byte[] keyBytes = new byte[16];
            System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ENCRYPTION_TYPE);
            Cipher cipher = Cipher.getInstance(AES_ECB_PKCS_5_PADDING_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
        } catch (Exception e) {
            throw new DDPException("Error while decrypting", e);
        }
    }

    public static String decrypt(String strToDecrypt, String secret) {
        try {

            byte[] unencodedBytes = Base64.getDecoder().decode(strToDecrypt);

            // Extract IV.
            byte[] iv = new byte[IV_SIZE];
            System.arraycopy(unencodedBytes, 0, iv, 0, iv.length);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            // Extract encrypted part.
            int encryptedSize = unencodedBytes.length - IV_SIZE;
            byte[] encryptedBytes = new byte[encryptedSize];
            System.arraycopy(unencodedBytes, IV_SIZE, encryptedBytes, 0, encryptedSize);

            // Hash key.
            byte[] keyBytes = new byte[KEY_SIZE];
            MessageDigest md = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);
            md.update(secret.getBytes());
            System.arraycopy(md.digest(), 0, keyBytes, 0, keyBytes.length);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ENCRYPTION_TYPE);

            // Decrypt.
            Cipher cipherDecrypt = Cipher.getInstance(AES_CBC_PKCS_5_PADDING_CIPHER);
            cipherDecrypt.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] decrypted = cipherDecrypt.doFinal(encryptedBytes);

            return new String(decrypted);
        } catch (Exception e) {
            throw new DDPException("Error while decrypting", e);
        }
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();

        options.addOption("d", false, "decrypt");
        options.addOption("e", false, "encrypt");
        options.addOption("encall", false, "encrypt all unencrypted secrets in database");
        options.addOption("convertall", false,
                "decrypt all encrypted secrets in database using ECB, and recrypt using CBC");
        options.addOption("s", null, true, "string to encrypt/decrypt");
        options.addOption("h", "help", false, "print this help");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            var formatter = new HelpFormatter();
            formatter.printHelp(80, USAGE, "", options, "");
            return;
        }

        Config cfg = ConfigManager.getInstance().getConfig();
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);

        String encryptionSecret = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);

        if ((cmd.hasOption('d') && cmd.hasOption('e'))
                || (!cmd.hasOption('d') && !cmd.hasOption('e') && !cmd.hasOption("encall") && !cmd.hasOption("convertall"))) {
            throw new RuntimeException("Please choose encrypt \'-e\' or decrypt \'-d\'");
        }

        if (cmd.hasOption('d')) {
            System.out.println(AesUtil.decrypt(cmd.getOptionValue('s'), encryptionSecret));
            return;
        }

        if (cmd.hasOption('e')) {
            System.out.println(AesUtil.encrypt(cmd.getOptionValue('s'), encryptionSecret));
            return;
        }

        initializeDb(cfg);

        if (cmd.hasOption("encall")) {
            System.out.print("You are encrypting all existing auth0 signing secrets, would you like to continue (Y/N) ?");
            String input = System.console().readLine();
            if (!input.contains("Y") && !input.contains("y")) {
                return;
            }
            TransactionWrapper.withTxn(handle -> {
                List<ClientInfo> clientList = handle.createQuery(SELECT_CLIENT_TENANT_AND_SIGNING_SECRET_QUERY)
                        .registerRowMapper(ConstructorMapper.factory(ClientInfo.class))
                        .mapTo(ClientInfo.class)
                        .list();
                for (ClientInfo clientInfo : clientList) {
                    System.out.println("auth0ClientId = " + clientInfo.getAuth0ClientId());
                    System.out.println("auth0TenantId = " + clientInfo.getAuth0TenantId());
                    if (clientInfo.getAuth0SigningSecret().length() == 64) {
                        System.out.println(
                                String.format(
                                        "Found unencrypted auth0 secret for client %s and tenant %d",
                                        clientInfo.getAuth0ClientId(),
                                        clientInfo.getAuth0TenantId()
                                )
                        );
                        String auth0EncrypedSigningSecret = AesUtil.encrypt(clientInfo.getAuth0SigningSecret(), encryptionSecret);
                        int numRows = handle.createUpdate(UPDATE_SIGNING_SECRET_QUERY)
                                .bind("auth0_signing_secret", auth0EncrypedSigningSecret)
                                .bind("auth0_client_id", clientInfo.getAuth0ClientId())
                                .bind("auth0_tenant_id", clientInfo.getAuth0TenantId())
                                .execute();
                        if (numRows != 1) {
                            throw new RuntimeException(
                                    "We expected to only update one row for client_id" + clientInfo.getAuth0ClientId()
                            );
                        }
                    }
                }
                return null;
            });
            return;
        }

        if (cmd.hasOption("convertall")) {
            System.out.print("You are converting all existing auth0 signing secrets from ECB cipher type to CBC, "
                    + "would you like to continue (Y/N)?");
            String input = System.console().readLine();
            if (!input.contains("Y") && !input.contains("y")) {
                return;
            }
            TransactionWrapper.withTxn(handle -> {
                List<ClientInfo> clientList = handle.createQuery(SELECT_CLIENT_TENANT_AND_SIGNING_SECRET_QUERY)
                        .registerRowMapper(ConstructorMapper.factory(ClientInfo.class))
                        .mapTo(ClientInfo.class)
                        .list();
                for (ClientInfo clientInfo : clientList) {
                    String auth0Secret = clientInfo.getAuth0SigningSecret();
                    boolean couldNotDecrypt = false;
                    String decryptedSecret = null;
                    try {
                        decryptedSecret = AesUtil.decryptECB(auth0Secret, encryptionSecret);
                    } catch (DDPException e) {
                        couldNotDecrypt = true;
                    }
                    if (!couldNotDecrypt) {
                        String encryptedSecret = AesUtil.encrypt(decryptedSecret, encryptionSecret);
                        int numRows = handle.createUpdate(UPDATE_SIGNING_SECRET_QUERY)
                                .bind("auth0_signing_secret", encryptedSecret)
                                .bind("auth0_client_id", clientInfo.getAuth0ClientId())
                                .bind("auth0_tenant_id", clientInfo.getAuth0TenantId())
                                .execute();
                        if (numRows > 1) {
                            throw new RuntimeException(
                                    "We expected to only update one row for client_id" + clientInfo.getAuth0ClientId()
                            );
                        }
                    }
                }

                return null;
            });
        }
    }

    private static void initializeDb(Config cfg) {
        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String dbUrl = cfg.getString(ConfigFile.DB_URL);

        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));
    }

    public static class ClientInfo {
        private String auth0ClientId;
        private long auth0TenantId;
        private String auth0SigningSecret;

        @JdbiConstructor
        public ClientInfo(
                @ColumnName("auth0_client_id") String auth0ClientId,
                @ColumnName("auth0_tenant_id") long auth0TenantId,
                @ColumnName("auth0_signing_secret") String auth0SigningSecret
        ) {
            this.auth0ClientId = auth0ClientId;
            this.auth0TenantId = auth0TenantId;
            this.auth0SigningSecret = auth0SigningSecret;
        }

        public String getAuth0ClientId() {
            return auth0ClientId;
        }

        public long getAuth0TenantId() {
            return auth0TenantId;
        }

        public String getAuth0SigningSecret() {
            return auth0SigningSecret;
        }
    }
}
