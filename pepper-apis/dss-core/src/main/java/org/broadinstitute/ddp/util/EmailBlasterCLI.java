package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_FIRST_NAME;

import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI for sending an email template from sendgrid
 * to a list of guids.
 */
public class EmailBlasterCLI {

    private static final Logger LOG = LoggerFactory.getLogger(EmailBlasterCLI.class);

    private static final String USAGE = "EmailBlaster [-h, --help] [OPTIONS] send a sendgrid email blast to a list of participants";
    private static final int DISPLAY_WIDTH = 80;
    private final String sendgridApiKey;

    public EmailBlasterCLI(String sendgridApiKey) {
        this.sendgridApiKey = sendgridApiKey;
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("h", "help", false, "print this help message");
        options.addOption("g", "guids-file", true, "path to file of user guids");
        options.addOption("e", "sender-email", true, "sender email");
        options.addOption("f", "sender-name", true, "name of sender");
        options.addOption("s", "study", true, "study guid");
        options.addOption("t", "template-id", true, "sendgrid template id");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        HelpFormatter formatter = new HelpFormatter();
        if (cmd.hasOption("help")) {
            formatter.printHelp(DISPLAY_WIDTH, USAGE, "", options, "");
            return;
        }

        Config cfg = ConfigFactory.load();
        String sendgridApiKey = cfg.getString(ConfigFile.SENDGRID_API_KEY);
        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, 1,
                        cfg.getString(ConfigFile.DB_URL)));

        String studyGuid = cmd.getOptionValue("s");
        String fromName = cmd.getOptionValue("f");
        String fromEmail = cmd.getOptionValue("e");
        String templateId = cmd.getOptionValue("t");
        File guidsFile = new File(cmd.getOptionValue("g"));

        List<String> guids = IOUtils.readLines(new FileReader(guidsFile));
        new EmailBlasterCLI(sendgridApiKey).sendEmail(fromName, fromEmail, templateId, studyGuid, guids);
        System.exit(0);
    }

    public void sendEmail(String fromName, String fromEmail, String sendgridTemplateId, String studyGuid,
                          Collection<String> recipientGuids) {

        final Set<String> auth0UserIds = new TreeSet<>();
        final Map<String, Map<String, String>> personalizationByAuth0Id = new HashMap<>();

        TransactionWrapper.useTxn(handle -> {
            JdbiUmbrellaStudy jdbiStudy = handle.attach(JdbiUmbrellaStudy.class);
            JdbiAuth0Tenant tenantDao = handle.attach(JdbiAuth0Tenant.class);
            JdbiUser userDao = handle.attach(JdbiUser.class);
            StudyDto studyDto = jdbiStudy.findByStudyGuid(studyGuid);

            Auth0TenantDto tenantDto = tenantDao.findByStudyGuid(studyDto.getGuid());
            Auth0ManagementClient mgmtClient = Auth0Util.getManagementClientForDomain(handle, tenantDto.getDomain());
            Auth0Util auth0Util = new Auth0Util(tenantDto.getDomain());

            for (String recipientGuid : recipientGuids) {
                UserDto userDto = userDao.findByUserGuid(recipientGuid);
                UserProfile userProfile = handle.attach(UserProfileDao.class).findProfileByUserGuid(userDto.getUserGuid()).get();
                // todo add other template vars
                String userAuth = userDto.getAuth0UserId().orElse(null);
                if (StringUtils.isNotBlank(userAuth)) {
                    auth0UserIds.add(userAuth);
                    personalizationByAuth0Id.put(userAuth, new HashMap<>());
                    personalizationByAuth0Id.get(userAuth).put(DDP_PARTICIPANT_FIRST_NAME, userProfile.getFirstName());
                }
            }

            Map<String, String> userEmailsById = auth0Util.getAuth0UsersByAuth0UserIds(auth0UserIds, mgmtClient.getToken());
            LOG.info("Found {} emails", userEmailsById.size());
            try {
                for (Map.Entry<String, String> emailByAuth0Id : userEmailsById.entrySet()) {
                    String recipient = emailByAuth0Id.getKey();
                    String auth0Id = emailByAuth0Id.getValue();
                    LOG.info("Sending to " + recipient);
                    Map<String, String> templateSubstitutions = personalizationByAuth0Id.get(auth0Id);
                    SendGridMailUtil.sendEmailMessage(fromName, fromEmail, null, recipient, null, sendgridTemplateId,
                            templateSubstitutions, sendgridApiKey);
                    LOG.info("Sent to " + recipient);
                }
            } catch (DDPException e) {
                LOG.error("Troubling sending email", e);
            }
        });
    }
}
