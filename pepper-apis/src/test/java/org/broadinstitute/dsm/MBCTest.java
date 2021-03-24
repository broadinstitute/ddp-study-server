package org.broadinstitute.dsm;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.jruby.embed.ScriptingContainer;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class MBCTest {

    private static final Logger logger = LoggerFactory.getLogger(MBCTest.class);

    private static String TEXT = "O4f3XdPAJ1uHUmT7CuEMug==";

    private static Config cfg;

    @BeforeClass
    public static void doFirst() {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File(System.getenv("TEST_CONFIG_FILE"))));
    }

    @Test
    public void decryption() {
        ScriptingContainer container = new ScriptingContainer();
        //put path to encryptorGem gem here
        container.getLoadPaths().add(container.getClassLoader().getResource("encryptorGem").getPath());

        container.runScriptlet("require 'encryptor'");
        String script = "def decrypt(phyName, city, key)\n" +
                "values = Array.new(2)\n" +
                "values[0] = Encryptor.decrypt(:value => phyName.unpack('m')[0], :key => key)\n" +
                "values[1] = Encryptor.decrypt(:value => city.unpack('m')[0], :key => key)\n" +
                "values\n" +
                "end";
        Object receiver = container.runScriptlet(script);

        String method = "decrypt";
        Object[] args = new Object[3];
        args[0] = TEXT;
        args[1] = "iODt8JNLAu1L6ApWDtSWow==";
        args[2] = cfg.getString("mbc.encryption_key");
        String[] result = container.callMethod(receiver, method, args, String[].class);
        System.out.println(args[0]  + " decrypted = " + result[0]);
        System.out.println(args[1]  + " decrypted = " + result[1]);
    }

    @Test
    public void decryptionMethod() {
        String decrypt = decryptValue(TEXT);
        Assert.assertEquals("phys name", decrypt);
    }

    public String decryptValue(@NonNull String value) {
        ScriptingContainer container = new ScriptingContainer();
        //put path to encryptorGem gem here
        container.getLoadPaths().add(container.getClassLoader().getResource("encryptorGem").getPath());
        container.runScriptlet("require 'encryptor'");

        String script = "def decrypt(encryptedValue, key)\n" +
                "Encryptor.decrypt(:value => encryptedValue.unpack('m')[0], :key => key)\n" +
                "end";
        Object receiver = container.runScriptlet(script);

        String method = "decrypt";
        Object[] args = new Object[2];
        args[0] = value;
        args[1] = cfg.getString("mbc.encryption_key");
        return container.callMethod(receiver, method, args, String.class);
    }
}