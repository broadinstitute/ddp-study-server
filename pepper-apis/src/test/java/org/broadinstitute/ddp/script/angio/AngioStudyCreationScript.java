package org.broadinstitute.ddp.script.angio;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.typesafe.config.Config;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiFormTypeActivityInstanceStatusType;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiSendgridConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrella;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.jdbi.v3.core.Handle;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To construct the Angio study, run the following:
 * 1. this script (and the user insertion if in non-prod environment)
 * 2. {@link AngioClientSetupScript}
 * 3. {@link AngioAboutYouActivityCreationScript}
 * 4. {@link AngioConsentActivityCreationScript}
 * 5. {@link AngioReleaseActivityCreationScript}
 * 6. {@link AngioLovedOneActivityCreationScript}
 * 7. {@link AngioFollowupConsentCreationScript}
 * 8. {@link AngioWorkflowConfigScript}
 * 9. {@link AngioEmailConfigurationScript}
 * 10. {@link AngioPdfConfigurationScript}
 * When running this script, be sure to set a few environment vars:
 * {@link #BASE_WEB_URL}
 * {@link #DDP_FROM_EMAIL}
 * {@link #DDP_FROM_NAME}
 * {@link #DDP_SENDGRID_KEY}
 */
@Ignore
public class AngioStudyCreationScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AngioStudyCreationScript.class);

    public static final String CMI_UMBRELLA_NAME = "CMI";
    public static final String ANGIO_STUDY_GUID = "ANGIO";
    public static final String ANGIO_STUDY_NAME = "ascproject";
    public static final String ANGIO_USER_GUID = "PEPPERANGIOADMINUSER";
    public static final String ANGIO_STUDY_EMAIL = "info@ascproject.org";
    // test resources folder location
    public static final String ANGIO_ICONS_PATH = "angio/";

    // Use this predefined time to accommodate migration of existing activity data.
    public static final long ACTIVITY_TIMESTAMP_ANCHOR = LocalDate.of(2016, 1, 1).atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond() * 1000L;

    /**
     * Base url used in sendgrid emails
     */
    private static final String BASE_WEB_URL = "ddp.baseWebUrl";
    /**
     * The from name in sendgrid emails
     */
    public static final String DDP_FROM_NAME = "ddp.fromName";
    /**
     * The from email in sendgrid emails
     */
    public static final String DDP_FROM_EMAIL = "ddp.fromEmail";
    /**
     * The default email salutation in sendgrid emails
     */
    public static final String DDP_DEFAULT_SALUTATION = "ddp.defaultSalutation";
    /**
     * The sendgrid api key to use when sending emails
     */
    public static final String DDP_SENDGRID_KEY = "ddp.sendgridKey";

    public static final String CMI_AUTH0_DOMAIN = "ddp.cmiAuth0Domain";
    public static final String CMI_AUTH_MGMT_API_CLIENT_ID = "ddp.cmiMgmtClientId";
    public static final String CMI_AUTH_MGMT_API_SECRET = "ddp.cmiMgmtSecret";

    public static final String READONLY_CONTACT_INFO_HTML = "If you would like to make any changes, please reach out to the study team "
            + "at <a href=\"mailto:info@ascproject.org\" class=\"Footer-contactLink\">info@ascproject.org</a>, or call us "
            + "at <a href=\"tel:857-500-6264\" class=\"Footer-contactLink\">857-500-6264</a>.";

    @Test
    @Ignore
    public void run() throws Exception {
        createAngioStudy();
        insertPepperAngioUser();
        insertActivityStatusIcons();
    }

    private void createAngioStudy() {
        String baseWebUrl = System.getProperty(BASE_WEB_URL);
        String auth0Domain = System.getProperty(CMI_AUTH0_DOMAIN);
        String mgmtApiClient = System.getProperty(CMI_AUTH_MGMT_API_CLIENT_ID);
        String mgmtApiSecret = System.getProperty(CMI_AUTH_MGMT_API_SECRET);

        if (StringUtils.isEmpty(auth0Domain)) {
            throw new RuntimeException("Please set the auth0 tenant for CMI using -D" + CMI_AUTH0_DOMAIN);
        }
        if (StringUtils.isBlank(baseWebUrl)) {
            throw new RuntimeException("Please set the base web url for angio using -D param " + BASE_WEB_URL);
        }
        if (StringUtils.isEmpty(mgmtApiClient)) {
            throw new RuntimeException("Please set the auth0 mgmt API client for CMI using -D"
                                               + CMI_AUTH_MGMT_API_CLIENT_ID);
        }
        if (StringUtils.isEmpty(mgmtApiSecret)) {
            throw new RuntimeException("Please set the auth0 mgmt API secret for CMI using -D"
                                               + CMI_AUTH_MGMT_API_SECRET);
        }
        TransactionWrapper.useTxn(handle -> {
            JdbiUmbrella jdbiUmbrella = handle.attach(JdbiUmbrella.class);
            JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
            JdbiAuth0Tenant jdbiAuth0Tenant = handle.attach(JdbiAuth0Tenant.class);

            String encryptedSecret = AesUtil.encrypt(mgmtApiSecret, EncryptionKey.getEncryptionKey());
            Auth0TenantDto auth0TenantDto = jdbiAuth0Tenant.insertIfNotExists(auth0Domain,
                    mgmtApiClient,
                    encryptedSecret);

            Long umbrellaId = jdbiUmbrella.findIdByName(CMI_UMBRELLA_NAME).orElse(null);
            if (umbrellaId == null) {
                umbrellaId = jdbiUmbrella.insert(CMI_UMBRELLA_NAME, CMI_UMBRELLA_NAME.toLowerCase());
                LOG.info("Created umbrella with id {}, name {}, and guid {}",
                        umbrellaId, CMI_UMBRELLA_NAME, CMI_UMBRELLA_NAME.toLowerCase());
            } else {
                LOG.info("Using existing umbrella {} with id {} and guid {}",
                        CMI_UMBRELLA_NAME, umbrellaId, CMI_UMBRELLA_NAME.toLowerCase());
            }

            // OLC data for Angio
            OLCPrecision studyPrecision = OLCPrecision.MOST;
            boolean shareParticipantLocation = true;

            Long studyId = jdbiUmbrellaStudy.getIdByGuid(ANGIO_STUDY_GUID).orElse(null);
            if (studyId == null) {
                studyId = jdbiUmbrellaStudy.insert(ANGIO_STUDY_NAME, ANGIO_STUDY_GUID, umbrellaId, baseWebUrl,
                        auth0TenantDto.getId(), studyPrecision, shareParticipantLocation, ANGIO_STUDY_EMAIL);
                LOG.info("Created angio study {} with id {} and name {}", ANGIO_STUDY_GUID, studyId, ANGIO_STUDY_NAME);
            } else {
                LOG.info("Using existing angio study {} with id {}", ANGIO_STUDY_GUID, studyId);
            }

            insertSendgridConfiguration(handle);
        });
    }

    /**
     * Inserts sendgrid notification as a separate test for existing databases.
     * This should only be run to incrementally patch older existing databases.
     * For initialization from scratch, sendgrid_configuration will be called
     * by {@link #createAngioStudy}
     */
    private void insertSendgridConfiguration() {
        TransactionWrapper.useTxn(handle -> {
            insertSendgridConfiguration(handle);
        });
    }

    private void insertSendgridConfiguration(Handle handle) {
        JdbiSendgridConfiguration sendgridConfig = handle.attach(JdbiSendgridConfiguration.class);
        String fromName = System.getProperty(DDP_FROM_NAME);
        String fromEmail = System.getProperty(DDP_FROM_EMAIL);
        String apiKey = System.getProperty(DDP_SENDGRID_KEY);
        String defaultSalutation = System.getProperty(DDP_DEFAULT_SALUTATION);

        if (StringUtils.isBlank(fromName)) {
            throw new RuntimeException("Please set the name from which emails should be sent via -D" + DDP_FROM_NAME);
        }

        if (StringUtils.isBlank(fromEmail)) {
            throw new RuntimeException("Please set the email from which emails should be sent via -D" + DDP_FROM_EMAIL);
        }

        if (StringUtils.isBlank(apiKey)) {
            throw new RuntimeException("Please set the sendgrid key via -D" + DDP_SENDGRID_KEY);
        }

        if (StringUtils.isBlank(defaultSalutation)) {
            throw new RuntimeException("Please set the default salutation for emails via -D" + DDP_DEFAULT_SALUTATION);
        }

        long studyId = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(ANGIO_STUDY_GUID).getId();

        sendgridConfig.insert(studyId, apiKey, fromName, fromEmail, defaultSalutation);

    }

    private void insertPepperAngioUser() {
        TransactionWrapper.useTxn(handle -> {
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            JdbiClientUmbrellaStudy jdbiClientUmbrellaStudy = handle.attach(JdbiClientUmbrellaStudy.class);
            ClientDao clientDao = handle.attach(ClientDao.class);

            Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
            String auth0domain = auth0Config.getString(ConfigFile.DOMAIN);
            String auth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
            String clientSecret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
            String clientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
            String encryptionSecret = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);

            long studyId = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(ANGIO_STUDY_GUID).get();

            Long clientId = clientDao.getClientIdByAuth0ClientAndDomain(auth0ClientId, auth0domain);
            if (clientId == null) {
                long auth0TenantId = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0domain).getId();
                clientId = clientDao.registerClient(clientName, auth0ClientId, clientSecret,
                                                    Collections.singletonList(ANGIO_STUDY_GUID), encryptionSecret,
                                                    auth0TenantId);
                LOG.info("Created client with id {}", clientId);
            } else {
                LOG.info("Using existing client with id {}", clientId);
                List<String> studyGuids = jdbiClientUmbrellaStudy.findPermittedStudyGuidsByAuth0ClientId(auth0ClientId);
                if (!studyGuids.contains(ANGIO_STUDY_GUID)) {
                    jdbiClientUmbrellaStudy.insert(clientId, studyId);
                    LOG.info("Granted permission to angio study for client id {}", clientId);
                }
            }

            UserDto user = jdbiUser.findByUserGuid(ANGIO_USER_GUID);
            if (user == null) {
                long id = jdbiUser.insert(null, ANGIO_USER_GUID, clientId, null);
                LOG.info("Created angio user {} with id {}", ANGIO_USER_GUID, id);
            } else {
                LOG.info("Angio user already exist with id {}", user.getUserId());
            }
        });
    }

    public static Template generateQuestionPrompt(String prefix, String text) {
        String var = "prompt_" + prefix;
        Template prompt = Template.html("$" + var + "");
        prompt.addVariable(TemplateVariable.single(var, "en", text));
        return prompt;
    }

    public static Template generateTextTemplate(String prefix, String text) {
        Template tmpl = Template.text("$" + prefix);
        tmpl.addVariable(TemplateVariable.single(prefix, "en", text));
        return tmpl;
    }

    public static Template generateHtmlTemplate(String prefix, String text) {
        Template tmpl = Template.html("$" + prefix);
        tmpl.addVariable(TemplateVariable.single(prefix, "en", text));
        return tmpl;
    }

    public static List<PicklistOptionDef> generateOptions(String prefix, String[][] mappings) {
        List<PicklistOptionDef> options = new ArrayList<>();
        for (String[] mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            String stableId = mapping[0];
            String labelText = mapping[1];
            String labelVar = prefix + "_" + stableId.toLowerCase();
            Template labelTmpl = generateTextTemplate(labelVar, labelText);
            options.add(new PicklistOptionDef(stableId, labelTmpl));
        }
        return options;
    }

    public static List<PicklistOptionDef> generateOptionsWithDetails(String prefix, String[][] mappings) {
        List<PicklistOptionDef> options = new ArrayList<>();
        for (String[] mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            String stableId = mapping[0];
            String labelText = mapping[1];
            String detailText = mapping[2];
            String labelVar = prefix + "_" + stableId.toLowerCase();
            Template labelTmpl = generateTextTemplate(labelVar, labelText);
            if (detailText != null) {
                String detailVar = labelVar + "_details";
                Template detailTmpl = generateTextTemplate(detailVar, detailText);
                options.add(new PicklistOptionDef(stableId, labelTmpl, detailTmpl));
            } else {
                options.add(new PicklistOptionDef(stableId, labelTmpl));
            }
        }
        return options;
    }

    public static List<PicklistOptionDef> generateYNDKOptions(String prefix) {
        return generateOptions(prefix, new String[][] {
                {"YES", "Yes"},
                {"NO", "No"},
                {"DK", "I don't know"}});
    }

    public static List<PicklistOptionDef> generateBodyLocationOptions(String prefix, boolean includeNoEvidence,
                                                                      boolean includeDontKnow) {
        String[][] mappings = new String[][] {
                {"HEADFACENECK", "Head/Face/Neck (not scalp)", null},
                {"SCALP", "Scalp", null},
                {"BREAST", "Breast", null},
                {"HEART", "Heart", null},
                {"LIVER", "Liver", null},
                {"SPLEEN", "Spleen", null},
                {"LUNG", "Lung", null},
                {"BRAIN", "Brain", null},
                {"LYMPH", "Lymph Nodes", null},
                {"BONELIMB", "Bone/Limb", "Please provide details"},
                {"ABDOMINAL", "Abdominal Area", "Please provide details"},
                {"NED", "No Evidence of Disease (NED)", null},
                {"OTHER", "Other", "Please provide details"},
                {"DK", "I don't know", null}};
        if (!includeNoEvidence) {
            mappings[11] = null;
        }
        if (!includeDontKnow) {
            mappings[13] = null;
        }
        return generateOptionsWithDetails(prefix, mappings);
    }

    private void insertActivityStatusIcons() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(ANGIO_USER_GUID);
            long revId = handle.attach(JdbiRevision.class).insertStart(ACTIVITY_TIMESTAMP_ANCHOR, adminUser
                    .getUserId(), "initial setup");
            JdbiFormTypeActivityInstanceStatusType dao = handle.attach(JdbiFormTypeActivityInstanceStatusType.class);

            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(ANGIO_STUDY_GUID);

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            byte[] completedIconBytes = IOUtils.toByteArray(classLoader.getResourceAsStream(ANGIO_ICONS_PATH
                                                                                             +  "AngioComplete.svg"));
            dao.insert(studyDto.getId(), FormType.GENERAL, InstanceStatusType.COMPLETE, completedIconBytes, revId);
            dao.insert(studyDto.getId(), FormType.CONSENT, InstanceStatusType.COMPLETE, completedIconBytes, revId);
            dao.insert(studyDto.getId(), FormType.PREQUALIFIER, InstanceStatusType.COMPLETE, completedIconBytes, revId);


            byte[] createdIconBytes = IOUtils.toByteArray(classLoader.getResourceAsStream(ANGIO_ICONS_PATH
                                                                                                    +  "AngioCreated"
                                                                                                    + ".svg"));
            dao.insert(studyDto.getId(), FormType.GENERAL, InstanceStatusType.CREATED, createdIconBytes, revId);
            dao.insert(studyDto.getId(), FormType.CONSENT, InstanceStatusType.CREATED, createdIconBytes, revId);
            dao.insert(studyDto.getId(), FormType.PREQUALIFIER, InstanceStatusType.CREATED, createdIconBytes, revId);

            byte[] inProgressIconBytes = IOUtils.toByteArray(classLoader.getResourceAsStream(ANGIO_ICONS_PATH
                                                                                                  +  "AngioInProgress"
                                                                                                  + ".svg"));
            dao.insert(studyDto.getId(), FormType.GENERAL, InstanceStatusType.IN_PROGRESS, inProgressIconBytes, revId);
            dao.insert(studyDto.getId(), FormType.CONSENT, InstanceStatusType.IN_PROGRESS, inProgressIconBytes, revId);
            dao.insert(studyDto.getId(), FormType.PREQUALIFIER, InstanceStatusType.IN_PROGRESS, inProgressIconBytes, revId);
        });
    }
}
