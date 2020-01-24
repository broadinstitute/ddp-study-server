package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.util.GuidUtils.UPPER_ALPHA_NUMERIC;

import java.util.List;

import com.auth0.client.auth.AuthAPI;
import com.auth0.json.auth.CreatedUser;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.JWT;
import com.auth0.net.TokenRequest;
import com.google.gson.JsonObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Lets you create a new auth0 user, an associated pepper user, and grab
 * a token that you can use in API calls for that user.
 */
public class UserAdminCLI {

    private static final String USAGE = UserAdminCLI.class.getSimpleName() + " [OPTIONS]";

    private static final Logger LOG = LoggerFactory.getLogger(UserAdminCLI.class);
    public static final String STUDY_OPTION = "s";
    public static final String EMAIL_OPTION = "e";
    public static final String PASSWORD_OPTION = "p";
    public static final String CLIENT_OPTION = "c";

    public static void main(String[] args) {
        Options options = new Options();

        options.addRequiredOption(STUDY_OPTION, "study", true, "study guid (note caps sensitivity");
        options.addRequiredOption(EMAIL_OPTION, "email", true, "email for the account");
        options.addRequiredOption(PASSWORD_OPTION, "password", true, "password for the account");
        options.addRequiredOption(CLIENT_OPTION, "client", true, "auth client id");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(80, USAGE, "", options, "Also requires a -Dconfig.file");
            System.exit(-1);
        }

        String studyGuid = cmd.getOptionValue(STUDY_OPTION);
        String userEmail = cmd.getOptionValue(EMAIL_OPTION);
        String password = cmd.getOptionValue(PASSWORD_OPTION);
        String auth0ClientId = cmd.getOptionValue(CLIENT_OPTION);

        Config cfg = ConfigFactory.load();
        String encryptionSecret = cfg.getConfig(ConfigFile.AUTH0).getString(ConfigFile.ENCRYPTION_SECRET);
        String dbUrl = cfg.getString(ConfigFile.DB_URL);

        String defaultTimeZoneName = cfg.getString(ConfigFile.DEFAULT_TIMEZONE);

        TransactionWrapper.init(defaultTimeZoneName,
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1, dbUrl));
        try {
            TransactionWrapper.useTxn(handle -> {
                Auth0MgmtTokenHelper mgmtTokenHelper = Auth0Util.getManagementTokenHelperForStudy(handle, studyGuid);

                ClientDto clientDto = handle.attach(JdbiClient.class).findByAuth0ClientId(auth0ClientId).get();

                Auth0Util auth0Util = new Auth0Util(mgmtTokenHelper.getDomain());
                String mgmtToken = mgmtTokenHelper.getManagementApiToken();

                JdbiUser jdbiUser = handle.attach(JdbiUser.class);

                AuthAPI auth = new AuthAPI(mgmtTokenHelper.getDomain(), auth0ClientId, clientDto.getAuth0DecryptedSecret(encryptionSecret));

                List<User> auth0Users = auth0Util.getAuth0UsersByEmail(userEmail, mgmtToken);
                String hruid;
                String userGuid;

                if (auth0Users.isEmpty()) {
                    // if no user with this email exists, create it and insert a corresponding  row in the user table
                    CreatedUser newUser = auth.signUp(userEmail, password, Auth0Util.USERNAME_PASSWORD_AUTH0_CONN_NAME).execute();
                    LOG.info("Created new auth0 user {}", newUser.getEmail());
                    hruid = GuidUtils.randomUserHruid();
                    userGuid = GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20);

                    String auth0UserId = "auth0|" + newUser.getUserId(); // this is safe as long as we use Username-Password connection
                    jdbiUser.insert(auth0UserId, userGuid, clientDto.getId(), hruid);

                    auth0Util.setDDPUserGuidForAuth0User(userGuid, auth0UserId, auth0ClientId, mgmtToken);
                    LOG.info("Created new pepper user {} with guid {} and hruid {}", newUser.getEmail(), userGuid, hruid);
                } else {
                    // user exists, so pull out some details about them
                    UserDto existingUser = jdbiUser.findByAuth0UserId(auth0Users.iterator().next().getId(), clientDto.getAuth0TenantId());
                    userGuid = existingUser.getUserGuid();
                    hruid = existingUser.getUserHruid();
                    LOG.info("User {} already exists with guid {} and hruid {}", userEmail, userGuid, hruid);
                }

                // since our auth0 rule pings the backend, add the study_guid param
                TokenRequest loginRequest = ((TokenRequest)auth.login(userEmail, password));
                loginRequest.addParameter("study_guid", studyGuid);
                TokenHolder token = loginRequest.execute();

                // these fields are needed by angular code to consider the user logged in on the frontend
                JsonObject sessionToken = new JsonObject();
                sessionToken.addProperty("accessToken", token.getAccessToken());
                sessionToken.addProperty("expiresAt", JWT.decode(token.getIdToken()).getExpiresAt().getTime());
                sessionToken.addProperty("idToken", token.getIdToken());
                sessionToken.addProperty("locale", "en");
                sessionToken.addProperty("participantGuid", userGuid);
                sessionToken.addProperty("userGuid", userGuid);

                LOG.info("Here is the value for 'session_key' in browser local storage:");
                System.out.println(sessionToken);

                LOG.info("Here is the value for 'token' in browser local storage or for direct API calls:");
                System.out.println(token.getIdToken());

            });
        } catch (Exception e) {
            LOG.error("Trouble setting up user", e);
        }
    }
}
