package org.broadinstitute.ddp.studybuilder;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiOLCPrecision;
import org.broadinstitute.ddp.db.dao.JdbiSendgridConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrella;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyI18n;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UmbrellaDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GuidUtils;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudyBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(StudyBuilder.class);

    private boolean doWorkflow = true;
    private boolean doEvents = true;
    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;

    public StudyBuilder(Path cfgPath, Config cfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.cfg = cfg;
        this.varsCfg = varsCfg;
    }

    public StudyBuilder doWorkflow(boolean doWorkflow) {
        this.doWorkflow = doWorkflow;
        return this;
    }

    public StudyBuilder doEvents(boolean doEvents) {
        this.doEvents = doEvents;
        return this;
    }

    public void run(Handle handle) {
        Auth0TenantDto tenantDto = getTenantOrInsert(handle);
        UmbrellaDto umbrellaDto = getUmbrellaOrInsert(handle);
        StudyDto studyDto = getStudyOrInsert(handle, tenantDto.getId(), umbrellaDto.getId());

        ClientDto clientDto = getClientOrInsert(handle, tenantDto.getId());
        grantClientAccessToStudy(handle, clientDto, studyDto);

        UserDto adminDto = getAdminUserOrInsert(handle, clientDto.getId());

        insertStudyGovernance(handle, studyDto);
        insertStudyDetails(handle, studyDto.getId());
        insertStudyLanguages(handle, studyDto.getId());
        insertSendgrid(handle, studyDto.getId());
        insertKits(handle, studyDto.getId());

        Path dirPath = cfgPath.getParent();
        new ActivityBuilder(dirPath, cfg, varsCfg, studyDto, adminDto.getUserId()).run(handle);
        new PdfBuilder(dirPath, cfg, studyDto, adminDto.getUserId()).run(handle);

        if (doWorkflow) {
            new WorkflowBuilder(cfg, studyDto).run(handle);
        }

        if (doEvents) {
            new EventBuilder(cfg, studyDto, adminDto.getUserId()).run(handle);
        }
    }

    public void runActivity(Handle handle, String activityCode) {
        StudyDto studyDto = getStudy(handle);
        UserDto adminDto = getAdminUser(handle);
        Path dirPath = cfgPath.getParent();
        new ActivityBuilder(dirPath, cfg, varsCfg, studyDto, adminDto.getUserId()).runSingle(handle, activityCode);
    }

    public void runWorkflow(Handle handle) {
        StudyDto studyDto = getStudy(handle);
        new WorkflowBuilder(cfg, studyDto).run(handle);
    }

    public void runEvents(Handle handle) {
        StudyDto studyDto = getStudy(handle);
        UserDto adminDto = getAdminUser(handle);
        new EventBuilder(cfg, studyDto, adminDto.getUserId()).run(handle);
    }

    public void runUpdatePdfTemplates(Handle handle) {
        StudyDto studyDto = getStudy(handle);
        Path dirPath = cfgPath.getParent();
        UserDto adminDto = getAdminUser(handle);
        new PdfBuilder(dirPath, cfg, studyDto, adminDto.getUserId()).updatePdfs(handle);
    }

    public void runEnableEvents(Handle handle, boolean enable) {
        StudyDto studyDto = getStudy(handle);
        int numUpdated = handle.attach(EventDao.class).enableAllStudyEvents(studyDto.getId(), enable);
        LOG.info("{} {} event configurations for study {}",
                enable ? "Enabled" : "Disabled", numUpdated, studyDto.getGuid());
    }

    public void runInvalidate(Handle handle) {
        StudyDto studyDto = getStudy(handle);
        StudyInvalidationHelper helper = handle.attach(StudyInvalidationHelper.class);

        int numRows = handle.attach(QueuedEventDao.class).deleteQueuedEventsByStudyId(studyDto.getId());
        LOG.info("deleted {} queued events for study", numRows);

        numRows = helper.invalidateMailingAddressStatuses(studyDto.getId());
        LOG.info("invalidated {} mailing addresses", numRows);

        numRows = helper.renamePdfConfigurations(studyDto.getId());
        LOG.info("renamed {} pdf configurations", numRows);

        // Remove the governance policy
        StudyGovernanceDao studyGovernanceDao = handle.attach(StudyGovernanceDao.class);
        studyGovernanceDao.findPolicyByStudyId(studyDto.getId()).ifPresent(policy -> {
            studyGovernanceDao.removePolicy(policy.getId());
            LOG.info("removed governance policy with id {} and {} age-of-majority rules",
                    policy.getId(), policy.getAgeOfMajorityRules().size());
        });

        // Normalize and make new guid unique.
        String newGuid = studyDto.getGuid().replace("_", "-") + studyDto.getId();

        // If guid has different parts, then strip out the beginning parts until it's shorter or there's only one part left.
        while (newGuid.length() > GuidUtils.STANDARD_GUID_LENGTH) {
            String[] parts = newGuid.split("-", 2);
            if (parts.length == 1) {
                break;
            }
            newGuid = parts[1];
        }

        if (newGuid.length() > GuidUtils.STANDARD_GUID_LENGTH) {
            // If it's still longer, then generate a random one.
            newGuid = GuidUtils.randomStandardGuid();
        }

        numRows = helper.renameStudy(studyDto.getId(), newGuid);
        if (numRows != 1) {
            throw new DDPException("unable to rename study for invalidation");
        }

        StudyDto renamedStudy = handle.attach(JdbiUmbrellaStudy.class).findById(studyDto.getId());
        LOG.info("renamed study with guid={}", renamedStudy.getGuid());
    }

    private Auth0TenantDto getTenantOrInsert(Handle handle) {
        Config tenantCfg = cfg.getConfig("tenant");
        String domain = tenantCfg.getString("domain");
        String mgmtClientId = tenantCfg.getString("mgmtClientId");
        String mgmtSecret = tenantCfg.getString("mgmtSecret");

        JdbiAuth0Tenant jdbiTenant = handle.attach(JdbiAuth0Tenant.class);
        Auth0TenantDto dto = jdbiTenant.findByDomain(domain);

        if (dto == null) {
            String encryptedSecret = AesUtil.encrypt(mgmtSecret, EncryptionKey.getEncryptionKey());
            long tenantId = jdbiTenant.insert(domain, mgmtClientId, encryptedSecret);
            dto = new Auth0TenantDto(tenantId, mgmtClientId, encryptedSecret, domain);
            LOG.info("Created tenant with id={}, domain={}", tenantId, domain);
        } else {
            LOG.warn("Tenant already exists with id={}, domain={}", dto.getId(), dto.getDomain());
        }

        return dto;
    }

    private UmbrellaDto getUmbrellaOrInsert(Handle handle) {
        Config umbrellaCfg = cfg.getConfig("umbrella");
        String name = umbrellaCfg.getString("name");
        String guid = umbrellaCfg.getString("guid");

        JdbiUmbrella jdbiUmbrella = handle.attach(JdbiUmbrella.class);
        UmbrellaDto dto = jdbiUmbrella.findByGuid(guid).orElse(null);

        if (dto == null) {
            long umbrellaId = jdbiUmbrella.insert(name, guid);
            dto = new UmbrellaDto(umbrellaId, name, guid);
            LOG.info("Created umbrella with id={}, name={}, guid={}", umbrellaId, name, guid);
        } else {
            LOG.warn("Umbrella already exists with id={}, name={}, guid={}", dto.getId(), dto.getName(), dto.getGuid());
        }

        return dto;
    }

    private StudyDto getStudy(Handle handle) {
        Config studyCfg = cfg.getConfig("study");
        String guid = studyCfg.getString("guid");

        StudyDto dto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(guid);
        if (dto == null) {
            throw new DDPException("Could not find study with guid=" + guid);
        }

        return dto;
    }

    private StudyDto getStudyOrInsert(Handle handle, long tenantId, long umbrellaId) {
        Config studyCfg = cfg.getConfig("study");
        String name = studyCfg.getString("name");
        String guid = studyCfg.getString("guid");
        String baseWebUrl = studyCfg.getString("baseWebUrl");
        String irbPassword = ConfigUtil.getStrIfPresent(studyCfg, "irbPassword");
        String studyEmail = ConfigUtil.getStrIfPresent(studyCfg, "studyEmail");

        String olcPrecisionString = ConfigUtil.getStrIfPresent(studyCfg, "plusCodePrecision");
        OLCPrecision olcPrecision;
        if (olcPrecisionString == null) {
            LOG.warn("OLC precision is null.");
            olcPrecision = null;
        } else {
            LOG.info("OLC precision is " + olcPrecisionString);
            olcPrecision = OLCPrecision.valueOf(olcPrecisionString);
        }

        boolean shareLocationInformation = studyCfg.getBoolean("shareParticipantLocation");

        JdbiUmbrellaStudy jdbiStudy = handle.attach(JdbiUmbrellaStudy.class);
        StudyDto dto = jdbiStudy.findByStudyGuid(guid);

        if (dto == null) {
            Long olcPrecisionId;
            if (olcPrecision == null) {
                olcPrecisionId = null;
            } else {
                olcPrecisionId = handle.attach(JdbiOLCPrecision.class).findDtoForCode(olcPrecision).getId();
            }
            long studyId = jdbiStudy.insert(name, guid, umbrellaId, baseWebUrl,
                    tenantId, irbPassword, olcPrecisionId, shareLocationInformation, studyEmail);
            dto = handle.attach(JdbiUmbrellaStudy.class).findById(studyId);
            LOG.info("Created study with id={}, name={}, guid={}", studyId, name, guid);
        } else {
            LOG.warn("Study already exists with id={}, name={}, guid={}", dto.getId(), dto.getName(), dto.getGuid());
        }

        return dto;
    }

    private ClientDto getClientOrInsert(Handle handle, long tenantId) {
        Config clientCfg = cfg.getConfig("client");
        String clientId = clientCfg.getString("id");
        String clientSecret = clientCfg.getString("secret");
        String passwordRedirectUrl = clientCfg.getString("passwordRedirectUrl");

        JdbiClient jdbiClient = handle.attach(JdbiClient.class);
        Optional<ClientDto> clientDto = jdbiClient.findByAuth0ClientIdAndAuth0TenantId(clientId, tenantId);

        return clientDto.map(
            dto -> {
                LOG.warn("Client already exists with id={}, auth0ClientId={}", dto.getId(), dto.getAuth0ClientId());
                return dto;
            }
        ).orElseGet(
            () -> {
                String encryptedSecret = AesUtil.encrypt(clientSecret, EncryptionKey.getEncryptionKey());
                long id = jdbiClient.insertClient(clientId, encryptedSecret, tenantId, passwordRedirectUrl);
                LOG.info("Created client with id={}, auth0ClientId={}", id, clientId);
                return new ClientDto(id, clientId, encryptedSecret, passwordRedirectUrl, false, tenantId);
            }
        );
    }

    private void grantClientAccessToStudy(Handle handle, ClientDto clientDto, StudyDto studyDto) {
        JdbiClientUmbrellaStudy jdbiACL = handle.attach(JdbiClientUmbrellaStudy.class);
        List<String> studyGuids = jdbiACL.findPermittedStudyGuidsByAuth0ClientIdAndAuth0TenantId(
                clientDto.getAuth0ClientId(),
                clientDto.getAuth0TenantId()
        );
        if (!studyGuids.contains(studyDto.getGuid())) {
            jdbiACL.insert(clientDto.getId(), studyDto.getId());
            LOG.info("Granted client {} access to study {}", clientDto.getAuth0ClientId(), studyDto.getGuid());
        } else {
            LOG.warn("Client {} already has access to study {}", clientDto.getAuth0ClientId(), studyDto.getGuid());
        }
    }

    private UserDto getAdminUser(Handle handle) {
        Config adminCfg = cfg.getConfig("adminUser");
        String guid = adminCfg.getString("guid");

        UserDto dto = handle.attach(JdbiUser.class).findByUserGuid(guid);
        if (dto == null) {
            throw new DDPException("Could not find admin user with guid=" + guid);
        }

        return dto;
    }

    private UserDto getAdminUserOrInsert(Handle handle, long clientId) {
        Config adminCfg = cfg.getConfig("adminUser");
        String guid = adminCfg.getString("guid");

        JdbiUser jdbiUser = handle.attach(JdbiUser.class);
        UserDto dto = jdbiUser.findByUserGuid(guid);

        if (dto == null) {
            long userId = jdbiUser.insert(null, guid, clientId, null);
            dto = jdbiUser.findByUserId(userId);
            LOG.info("Created admin user with id={}, guid={}", userId, guid);
        } else {
            LOG.warn("Admin user already exists with id={}, guid={}", dto.getUserId(), dto.getUserGuid());
        }

        return dto;
    }

    private void insertStudyGovernance(Handle handle, StudyDto studyDto) {
        if (cfg.hasPath("governance")) {
            Config governanceCfg = cfg.getConfig("governance");
            String shouldCreateGovernedUserExprText = governanceCfg.getString("shouldCreateGovernedUserExpr");

            GovernancePolicy policy = new GovernancePolicy(
                    studyDto.getId(),
                    new Expression(shouldCreateGovernedUserExprText));
            for (Config aomRuleCfg : governanceCfg.getConfigList("ageOfMajorityRules")) {
                policy.addAgeOfMajorityRule(new AgeOfMajorityRule(
                        aomRuleCfg.getString("condition"),
                        aomRuleCfg.getInt("age"),
                        ConfigUtil.getIntIfPresent(aomRuleCfg, "prepMonths")));
            }

            policy = handle.attach(StudyGovernanceDao.class).createPolicy(policy);
            LOG.info("Created study governance policy with id={}, shouldCreateGovernedUserExprId={}, numAgeOfMajorityRules={}",
                    policy.getId(), policy.getShouldCreateGovernedUserExpr().getId(), policy.getAgeOfMajorityRules().size());
        }
    }

    private void insertStudyDetails(Handle handle, long studyId) {
        JdbiUmbrellaStudyI18n jdbiStudyI18n = handle.attach(JdbiUmbrellaStudyI18n.class);
        JdbiLanguageCode jdbiLangCode = handle.attach(JdbiLanguageCode.class);

        for (Config detailCfg : cfg.getConfigList("studyDetails")) {
            String lang = detailCfg.getString("language");
            String name = detailCfg.getString("name");
            String summary = ConfigUtil.getStrIfPresent(detailCfg, "summary");

            Long langCodeId = jdbiLangCode.getLanguageCodeId(lang);
            if (langCodeId == null) {
                throw new DDPException("Could not find language using code: " + lang);
            }

            long detailId = jdbiStudyI18n.insert(studyId, langCodeId, name, summary);
            LOG.info("Created study details with id={}, language={}, name={}, summary={}",
                    detailId, lang, name, StringUtils.abbreviate(summary, 50));
        }
    }

    private void insertStudyLanguages(Handle handle, long studyId) {
        if (!cfg.hasPath("supportedLanguages")) {
            return;
        }

        JdbiLanguageCode jdbiLangCode = handle.attach(JdbiLanguageCode.class);
        StudyLanguageDao studyLanguageDao = handle.attach(StudyLanguageDao.class);

        boolean defaultSet = false;
        Long defaultLanguageCodeId = null;
        String defaultLanguageCode = null;
        for (Config languageCfg : cfg.getConfigList("supportedLanguages")) {
            String lang = languageCfg.getString("language");
            Boolean isDefault = false;
            if (languageCfg.hasPath("isDefault")) {
                isDefault = languageCfg.getBoolean("isDefault");
            }

            String name = null;
            if (languageCfg.hasPath("name")) {
                name = languageCfg.getString("name");
            }

            Long langCodeId = jdbiLangCode.getLanguageCodeId(lang);
            if (langCodeId == null) {
                throw new DDPException("Could not find language using code: " + lang);
            }
            if (isDefault) {
                if (defaultSet) {
                    //no more than 1 language can be set as default
                    throw new DDPException("Cannot set isDefault: true for more than 1 language ");
                } else {
                    defaultLanguageCodeId = langCodeId;
                    defaultLanguageCode = lang;
                    defaultSet = true;
                }
            }

            //insert into study_language
            long studyLanguageId = studyLanguageDao.insert(studyId, langCodeId, name);
            LOG.info("Created study language with id={}, languageCode={} languageName={}", studyLanguageId, lang, name);
        }

        //set default language
        if (defaultSet) {
            LOG.info("Setting language {} as default", defaultLanguageCode);
            studyLanguageDao.setAsDefaultLanguage(studyId, defaultLanguageCodeId);
        } else {
            LOG.error("No language is set as default. Please set default language");
            throw new DDPException("No language is set as default ");
        }
    }

    private void insertSendgrid(Handle handle, long studyId) {
        Config sendgridCfg = cfg.getConfig("sendgrid");
        String apiKey = sendgridCfg.getString("apiKey");
        String fromName = sendgridCfg.getString("fromName");
        String fromEmail = sendgridCfg.getString("fromEmail");
        String defaultSalutation = sendgridCfg.getString("defaultSalutation");

        long id = handle.attach(JdbiSendgridConfiguration.class).insert(studyId, apiKey, fromName, fromEmail, defaultSalutation);
        LOG.info("Created sendgrid configuration with id={}, fromName={}, fromEmail={}", id, fromName, fromEmail);
    }

    private void insertKits(Handle handle, long studyId) {
        KitConfigurationDao kitDao = handle.attach(KitConfigurationDao.class);
        KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);

        for (Config kitCfg : cfg.getConfigList("kits")) {
            String type = kitCfg.getString("type");
            int quantity = kitCfg.getInt("quantity");

            KitType kitType = kitTypeDao.getKitTypeByName(type)
                    .orElseThrow(() -> new DDPException("Could not find kit type " + type));
            long kitId = kitDao.insertConfiguration(studyId, quantity, kitType.getId());
            LOG.info("Created kit configuration with id={}, type={}, quantity={}", kitId, type, quantity);

            for (Config ruleCfg : kitCfg.getConfigList("rules")) {
                KitRuleType ruleType = KitRuleType.valueOf(ruleCfg.getString("type"));
                if (ruleType == KitRuleType.PEX) {
                    String expr = ruleCfg.getString("expression");
                    long ruleId = kitDao.addPexRule(kitId, expr);
                    LOG.info("Added pex rule to kit configuration {} with id={}", kitId, ruleId);
                } else if (ruleType == KitRuleType.COUNTRY) {
                    String country = ruleCfg.getString("country");
                    long ruleId = kitDao.addCountryRule(kitId, country);
                    LOG.info("Added country rule to kit configuration {} with id={}, country={}", kitId, ruleId, country);
                } else if (ruleType == KitRuleType.ZIP_CODE) {
                    Set<String> zipCodes = Set.copyOf(ruleCfg.getStringList("zipCodes"));
                    long ruleId = kitDao.addZipCodeRule(kitId, zipCodes);
                    LOG.info("Added zip code rule to kit configuration {} with id={}, zipCodes={}", kitId, ruleId, zipCodes);
                } else {
                    throw new DDPException("Unsupported kit rule type " + ruleType);
                }
            }
        }
    }

    public interface StudyInvalidationHelper extends SqlObject {

        default int invalidateMailingAddressStatuses(long studyId) {
            return updateAddressStatusesToInvalid(findKitRequestPendingAddressIds(studyId));
        }

        @SqlQuery("select addr.address_id"
                + "  from user_study_enrollment as usen"
                + "  join user as u on u.user_id = usen.user_id"
                + "  join umbrella_study as study on study.umbrella_study_id = usen.study_id"
                + "  join enrollment_status_type as entype on entype.enrollment_status_type_id = usen.enrollment_status_type_id"
                + "  left join default_mailing_address as def on def.participant_user_id = u.user_id"
                + "  left join mailing_address as addr on addr.address_id = def.address_id"
                + "  left join mailing_address_validation_status as vs"
                + "       on vs.mailing_address_validation_status_id = addr.validation_status_id"
                + " where study.umbrella_study_id = :studyId"
                + "   and entype.enrollment_status_type_code = 'ENROLLED'"
                + "   and not exists ("
                + "       select 1"
                + "         from kit_request as kit"
                + "        where kit.kit_type_id = (select kit_type_id from kit_type where name = 'SALIVA')"
                + "          and kit.participant_user_id = u.user_id"
                + "          and kit.study_id = study.umbrella_study_id)"
                + "   and valid_to is null"
                + "   and addr.address_id is not null"
                + "   and vs.code != 'INVALID'")
        Set<Long> findKitRequestPendingAddressIds(@Bind("studyId") long studyId);

        @SqlUpdate("update mailing_address"
                + "    set validation_status_id = ("
                + "        select mailing_address_validation_status_id from mailing_address_validation_status where name = 'INVALID')"
                + "  where address_id in (<addressIds>)")
        int updateAddressStatusesToInvalid(@BindList(value = "addressIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> addressIds);

        @SqlUpdate("update pdf_document_configuration"
                + "    set configuration_name = concat(configuration_name, '-', umbrella_study_id)"
                + "  where umbrella_study_id = :studyId")
        int renamePdfConfigurations(@Bind("studyId") long studyId);

        @SqlUpdate("update umbrella_study"
                + "    set study_name = concat(study_name, '-', umbrella_study_id),"
                + "        guid = :newGuid,"
                + "        enable_data_export = false"
                + "  where umbrella_study_id = :studyId")
        int renameStudy(@Bind("studyId") long studyId, @Bind("newGuid") String newGuid);
    }
}
