package org.broadinstitute.ddp.util;

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
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_BASE_WEB_URL;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PROXY_FIRST_NAME;

/**
 * CLI for sending an email template from sendgrid
 * to a list of guids.
 * limitations/expectations:
 * As of now only english, legacy templates are supported
 * self/pediatric guids should be separated into different runs
 * For pediatric participants, the participant (pediatric) guid should be provided. Code will look up proxy/parent and will get the email.
 * Not all template substitutions are supported. Check the substitutions in the template and add them to the code if needed.
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
        options.addOption("a", "activity-code", true, "activity code");
        options.addOption("sub", "subject", true, "email subject");
        options.addOption("p", "pediatric", false, "pediatric participants");

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
        String subject = cmd.getOptionValue("sub");
        String activityCode = cmd.getOptionValue("a");
        File guidsFile = new File(cmd.getOptionValue("g"));
        boolean isPediatric = cmd.hasOption("p");
        LOG.debug("passed subject: " + subject);

        List<String> guids = null;
        try {
            guids = IOUtils.readLines(new FileReader(guidsFile));
        } catch (IOException e) {
            LOG.error("Could not read " + guidsFile.getAbsolutePath(), e);
            System.exit(-1);
        }
        new EmailBlasterCLI(sendgridApiKey).sendEmail(fromName, fromEmail, templateId, studyGuid, subject,
                activityCode, guids, isPediatric);
        System.exit(0);
    }

    public void sendEmail(String fromName, String fromEmail, String sendgridTemplateId, String studyGuid,
                          String subject, String activityCode, Collection<String> recipientGuids, boolean isPediatric) {

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

            List<String> noAuthUsers = new ArrayList<>();
            for (String recipientGuid : recipientGuids) {
                LOG.info("Processing recipient " + recipientGuid);
                UserDto userDto = userDao.findByUserGuid(recipientGuid);
                if (userDto == null) {
                    LOG.error("Could not find user with guid " + recipientGuid);
                    continue;
                }

                UserDto proxyUserDto = null;
                UserProfile userProfile = handle.attach(UserProfileDao.class).findProfileByUserGuid(userDto.getUserGuid()).get();
                // todo add other template vars needed by the email template. Just add ALL possible substitutions!!
                String userAuth = userDto.getAuth0UserId().orElse(null);
                if (isPediatric) {
                    //find the parent/proxy
                    List<Governance> governances;
                    LOG.debug("participantId: {} .. studyID:{}", userDto.getUserId(), studyDto.getId());
                    try (Stream<Governance> governanceStream = handle.attach(UserGovernanceDao.class)
                            .findActiveGovernancesByParticipantAndStudyIds(userDto.getUserId(), studyDto.getId())) {
                        governances = governanceStream.collect(Collectors.toList());
                    }
                    String proxyGuid = governances.get(0).getProxyUserGuid();
                    LOG.info("Proxy user guid: " + proxyGuid);
                    proxyUserDto = userDao.findByUserGuid(proxyGuid);
                    userAuth = proxyUserDto.getAuth0UserId().orElse(null);
                    LOG.info("Proxy user auth0 id: " + userAuth);
                    userProfile = handle.attach(UserProfileDao.class).findProfileByUserGuid(proxyUserDto.getUserGuid()).get();
                    LOG.debug("Proxy user firstName: " + userProfile.getFirstName());
                }

                if (StringUtils.isNotBlank(userAuth)) {
                    auth0UserIds.add(userAuth);
                    personalizationByAuth0Id.put(userAuth, new HashMap<>());
                    personalizationByAuth0Id.get(userAuth).put(DDP_PARTICIPANT_FIRST_NAME, userProfile.getFirstName());
                    personalizationByAuth0Id.get(userAuth).put(DDP_BASE_WEB_URL, studyDto.getWebBaseUrl());
                    personalizationByAuth0Id.get(userAuth).put(DDP_PARTICIPANT_GUID, recipientGuid);

                    if (activityCode != null) {
                        //load activity instance
                        String instanceGuid = null;
                        ActivityInstanceDao activityInstanceDao = new ActivityInstanceDao();
                        Optional<String> instanceGuidOpt = activityInstanceDao.getGuidOfLatestInstanceForUserAndActivity(
                                handle, userDto.getUserGuid(), activityCode, studyDto.getId());
                        if (instanceGuidOpt.isPresent()) {
                            instanceGuid = instanceGuidOpt.get();
                        } else {
                            LOG.error("No instance GUID found for user {} and activity {}", userDto.getUserGuid(), activityCode);
                            continue;
                        }
                        LOG.info("Found instance guid: " + instanceGuid);
                        personalizationByAuth0Id.get(userAuth).put(DDP_ACTIVITY_INSTANCE_GUID, instanceGuid);
                    }
                    if (isPediatric) {
                        personalizationByAuth0Id.get(userAuth).put(DDP_PROXY_FIRST_NAME, userProfile.getFirstName());
                    }

                } else {
                    noAuthUsers.add(userDto.getUserGuid());
                }
            }

            Map<String, String> userEmailsById = auth0Util.getAuth0UsersByAuth0UserIds(auth0UserIds, mgmtClient.getToken());
            LOG.info("Found {} emails", userEmailsById.size());
            try {
                for (Map.Entry<String, String> emailByAuth0Id : userEmailsById.entrySet()) {
                    String recipient = emailByAuth0Id.getValue();
                    String auth0Id = emailByAuth0Id.getKey();
                    LOG.info("Sending to " + recipient);
                    Map<String, String> templateSubstitutions = personalizationByAuth0Id.get(auth0Id);
                    SendGridMailUtil.sendEmailMessage(fromName, fromEmail, null, recipient, subject, sendgridTemplateId,
                            templateSubstitutions, sendgridApiKey);
                    LOG.info("Sent to " + recipient);
                }
            } catch (DDPException e) {
                LOG.error("Troubling sending email", e);
            }
            LOG.info("No auth0 user ids for: " + noAuthUsers);
        });
    }
}
