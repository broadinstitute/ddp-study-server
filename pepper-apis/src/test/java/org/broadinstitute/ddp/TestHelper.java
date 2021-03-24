package org.broadinstitute.ddp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ConfigProperties;
import org.broadinstitute.ddp.datstat.AuthSingleton;

import javax.servlet.http.Cookie;
import java.io.File;
import java.io.IOException;
import java.util.Map;


import static org.junit.Assert.assertEquals;

/**
 * Created by ebaker on 4/29/16.
 */
public class TestHelper
{
    private static final String SQL_DELETE_CONTACTS = "DELETE FROM CONTACT WHERE CONT_EMAIL LIKE ?";
    public static final String TEST_EMAIL_SUFFIX = "@unittestonly.com";
    public static final String DDP_TEST_CONFIG_FILE = "config/ddp_unit_test.properties";
    public static final String ANGIO_TEST_CONFIG_FILE = "config/angio_unit_test.properties";
    public static final String JWT_FAKE_EMAIL = "fake@email.com";
    public static final String JWT_FAKE_FIRSTNAME = "fake";
    public static final String JWT_FAKE_LASTNAME = "stuff";

    private String jwtToken;
    private Cookie csrfCookie;

    public TestHelper(String jwtToken, Cookie csrfCookie) {
        this.jwtToken = jwtToken;
        this.csrfCookie = csrfCookie;
    }
    public static Config loadDDPTestProperties() throws IOException
    {
        return ConfigFactory.parseFile(new File(DDP_TEST_CONFIG_FILE), ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON));
    }

    public static Config resetAuthSingletonAndGetDDPProperties() throws Exception
    {
        Config cfg = TestHelper.loadDDPTestProperties();
        AuthSingleton.getInstance(true, cfg.getString(ConfigProperties.DATSTAT_KEY),
                cfg.getString(ConfigProperties.DATSTAT_SECRET),
                cfg.getString(ConfigProperties.DATSTAT_USERNAME),
                cfg.getString(ConfigProperties.DATSTAT_PASSWORD),
                cfg.getString(ConfigProperties.DATSTAT_URL));
        return cfg;
    }



}
