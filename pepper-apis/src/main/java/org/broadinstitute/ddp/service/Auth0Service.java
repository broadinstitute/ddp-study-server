package org.broadinstitute.ddp.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.auth0.exception.APIException;
import com.auth0.json.mgmt.Connection;
import com.auth0.json.mgmt.users.User;
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.study.PasswordPolicy;
import org.broadinstitute.ddp.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Auth0Service {

    public static final int MAX_GENERATE_PASSWORD_TRIES = 10;
    public static final int DEFAULT_PASSWORD_LENGTH = 36;
    public static final char[] ALPHA_NUMERIC_SPECIALS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*".toCharArray();

    private static final Logger LOG = LoggerFactory.getLogger(Auth0Service.class);

    private final Auth0ManagementClient auth0Mgmt;
    private final String apiBaseUrl;

    public Auth0Service(Auth0ManagementClient auth0Mgmt) {
        this(auth0Mgmt, ConfigManager.getInstance().getConfig().getString(ConfigFile.API_BASE_URL));
    }

    public Auth0Service(Auth0ManagementClient auth0Mgmt, String apiBaseUrl) {
        this.auth0Mgmt = auth0Mgmt;
        this.apiBaseUrl = apiBaseUrl;
    }

    public Connection findClientDBConnection(String auth0ClientId) {
        var listResult = auth0Mgmt.listClientConnections(auth0ClientId);

        if (listResult.hasThrown() || listResult.hasError()) {
            Exception e = listResult.hasThrown() ? listResult.getThrown() : listResult.getError();
            throw new DDPException("Error listing client connections", e);
        }

        List<Connection> dbConnections = listResult.getBody().stream()
                .filter(conn -> conn.getStrategy().equals(Auth0ManagementClient.DB_CONNECTION_STRATEGY))
                .collect(Collectors.toList());
        if (dbConnections.isEmpty()) {
            LOG.error("Password policies are only set on database connections but none were found for client {}", auth0ClientId);
            return null;
        } else if (dbConnections.size() > 1) {
            LOG.error("More than one database connection found for client {}, will attempt to use default one", auth0ClientId);
            // Attempt to put the default one in front, if there is one.
            dbConnections.sort((conn1, conn2) -> {
                if (conn1.getName().equals(Auth0ManagementClient.DEFAULT_DB_CONN_NAME)) {
                    return -1;
                } else if (conn2.getName().equals(Auth0ManagementClient.DEFAULT_DB_CONN_NAME)) {
                    return 1;
                } else {
                    return 0;
                }
            });
        }

        return dbConnections.get(0);
    }

    public PasswordPolicy extractPasswordPolicy(Connection dbConnection) {
        Map<String, Object> options = dbConnection.getOptions();
        options = options != null ? options : new HashMap<>();

        Object value = options.get(Auth0ManagementClient.KEY_PASSWORD_COMPLEXITY_OPTIONS);
        Map<String, Object> passwordOptions = value != null ? (Map<String, Object>) value : new HashMap<>();

        value = options.get(Auth0ManagementClient.KEY_PASSWORD_POLICY);
        String policyName = value != null ? (String) value : null;
        value = passwordOptions.get(Auth0ManagementClient.KEY_MIN_LENGTH);
        Integer minLength = value != null ? (Integer) value : null;

        if (minLength != null && minLength > PasswordPolicy.MAX_PASSWORD_LENGTH) {
            throw new DDPException("Password minimum length exceeds the maximum of " + PasswordPolicy.MAX_PASSWORD_LENGTH);
        }

        PasswordPolicy.PolicyType type;
        try {
            // Somehow Auth0 returns `null` when it's supposed to be `none`.
            type = (policyName == null ? PasswordPolicy.PolicyType.NONE : PasswordPolicy.PolicyType.valueOf(policyName.toUpperCase()));
        } catch (Exception e) {
            throw new DDPException("Could not convert from password policy name '" + policyName + "'");
        }

        return PasswordPolicy.fromType(type, minLength);
    }

    public String generatePassword(PasswordPolicy policy) {
        int len = Math.max(policy.getMinLength(), DEFAULT_PASSWORD_LENGTH);
        int numTries = 0;
        while (numTries < MAX_GENERATE_PASSWORD_TRIES) {
            String pwd = NanoIdUtils.randomNanoId(
                    NanoIdUtils.DEFAULT_NUMBER_GENERATOR, ALPHA_NUMERIC_SPECIALS, len);
            if (policy.checkPassword(pwd)) {
                return pwd; // All good, use this one.
            }
            numTries++;
        }
        return null;
    }

    public String generatePasswordResetRedirectUrl(String auth0ClientId, String auth0Domain) {
        try {
            return new URIBuilder(apiBaseUrl)
                    .setPath(RouteConstants.API.POST_PASSWORD_RESET)
                    .addParameter(RouteConstants.QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                    .addParameter(RouteConstants.QueryParam.AUTH0_DOMAIN, auth0Domain)
                    .build()
                    .toString();
        } catch (Exception e) {
            throw new DDPException("Failed to generate password reset redirect URL", e);
        }
    }

    public UserWithPasswordTicket createUserWithPasswordTicket(Connection dbConnection, String email, String redirectUrl) {
        PasswordPolicy policy;
        try {
            policy = extractPasswordPolicy(dbConnection);
        } catch (Exception e) {
            throw new DDPException("Error extracting password policy from connection " + dbConnection.getName(), e);
        }

        // Create auth0 user
        String randomPassword = generatePassword(policy);
        var userResult = auth0Mgmt.createAuth0User(dbConnection.getName(), email, randomPassword);
        if (userResult.hasThrown()) {
            throw new DDPException("Error creating new auth0 account", userResult.getThrown());
        } else if (userResult.hasError()) {
            APIException err = userResult.getError();
            if (err.getStatusCode() == HttpStatus.SC_CONFLICT) {
                return null; // Email already exists.
            } else {
                throw new DDPException("Error creating new auth0 account", err);
            }
        } else {
            LOG.info("Created auth0 account for user with email {}", email);
        }

        // Create password reset ticket
        var auth0User = userResult.getBody();
        var ticketResult = auth0Mgmt.createPasswordResetTicket(auth0User.getId(), redirectUrl);
        if (ticketResult.hasThrown() || ticketResult.hasError()) {
            Exception e = ticketResult.hasThrown() ? ticketResult.getThrown() : ticketResult.getError();
            throw new DDPException("Error creating password reset ticket", e);
        } else {
            LOG.info("Created password reset ticket for user with email {}", email);
        }

        return new UserWithPasswordTicket(auth0User, ticketResult.getBody());
    }

    public static final class UserWithPasswordTicket {
        private final User user;
        private final String ticket;

        public UserWithPasswordTicket(User user, String ticket) {
            this.user = user;
            this.ticket = ticket;
        }

        public User getUser() {
            return user;
        }

        public String getTicket() {
            return ticket;
        }
    }
}
