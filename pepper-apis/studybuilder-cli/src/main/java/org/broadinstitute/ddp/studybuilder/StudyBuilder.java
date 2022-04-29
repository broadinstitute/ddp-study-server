package org.broadinstitute.ddp.studybuilder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiOLCPrecision;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiSendgridConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrella;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyI18n;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitScheduleDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.StatisticsConfigurationDao;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.StudyI18nDto;
import org.broadinstitute.ddp.db.dto.UmbrellaDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.model.kit.KitSchedule;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.model.statistics.StatisticsType;
import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsToDbJsonSaver;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GuidUtils;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class StudyBuilder {
    private static final String UMBRELLA_UNASSIGNED = "unassigned";

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

        List<ClientDto> clientDtos = getClientsOrInsert(handle, tenantDto);
        grantClientsAccessToStudy(handle, clientDtos, studyDto);

        ClientDto webClient = clientDtos.stream()
                .filter(client -> client.getWebPasswordRedirectUrl() != null)
                .findFirst()
                .orElse(clientDtos.get(0));
        UserDto adminDto = getAdminUserOrInsert(handle, webClient.getId());

        insertStudyGovernance(handle, studyDto);
        insertOrUpdateStudyDetails(handle, studyDto.getId());
        insertOrUpdateStudyLanguages(handle, studyDto.getId());
        insertSettings(handle, studyDto, adminDto.getUserId());
        insertSendgrid(handle, studyDto.getId());
        insertKits(handle, studyDto.getId(), adminDto.getUserId());
        insertStatistics(handle, studyDto.getId());

        Path dirPath = cfgPath.getParent();
        new ActivityBuilder(dirPath, cfg, varsCfg, studyDto, adminDto.getUserId()).run(handle);
        new PdfBuilder(dirPath, cfg, studyDto, adminDto.getUserId()).run(handle);

        // save translations (stored in i18n-files) to DB table `i18n_translation`
        new TranslationsToDbJsonSaver().saveTranslations(handle, studyDto);

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

    public void updateWorkflow(Handle handle) {
        StudyDto studyDto = getStudy(handle);
        new WorkflowBuilder(cfg, studyDto).runUpdate(handle);
    }

    public void runEvents(Handle handle) {
        StudyDto studyDto = getStudy(handle);
        UserDto adminDto = getAdminUser(handle);
        new EventBuilder(cfg, studyDto, adminDto.getUserId()).run(handle);
    }

    public void runLabeledEvents(Handle handle, String[] labels) {
        StudyDto studyDto = getStudy(handle);
        UserDto adminDto = getAdminUser(handle);
        new EventBuilder(cfg, studyDto, adminDto.getUserId()).run(handle, labels);
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
        log.info("{} {} event configurations for study {}",
                enable ? "Enabled" : "Disabled", numUpdated, studyDto.getGuid());
    }

    public void runInvalidate(Handle handle) {
        StudyDto studyDto = getStudy(handle);
        StudyInvalidationHelper helper = handle.attach(StudyInvalidationHelper.class);

        int numRows = handle.attach(QueuedEventDao.class).deleteQueuedEventsByStudyId(studyDto.getId());
        log.info("deleted {} queued events for study", numRows);

        numRows = helper.invalidateMailingAddressStatuses(studyDto.getId());
        log.info("invalidated {} mailing addresses", numRows);

        numRows = helper.invalidateKitScheduleRecords(studyDto.getId());
        log.info("invalidated {} kit schedule records", numRows);

        numRows = helper.renamePdfConfigurations(studyDto.getId());
        log.info("renamed {} pdf configurations", numRows);

        // Remove the governance policy
        StudyGovernanceDao studyGovernanceDao = handle.attach(StudyGovernanceDao.class);
        studyGovernanceDao.findPolicyByStudyId(studyDto.getId()).ifPresent(policy -> {
            studyGovernanceDao.removePolicy(policy.getId());
            log.info("removed governance policy with id {} and {} age-of-majority rules",
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

        var umbrellaDto = helper.findOrInsertUmbrella(UMBRELLA_UNASSIGNED);
        numRows = helper.renameStudy(studyDto.getId(), newGuid, umbrellaDto.getId());
        if (numRows != 1) {
            throw new DDPException("unable to rename study for invalidation");
        }

        StudyDto renamedStudy = handle.attach(JdbiUmbrellaStudy.class).findById(studyDto.getId());
        log.info("renamed study with guid={} and moved to umbrella '{}'", renamedStudy.getGuid(), UMBRELLA_UNASSIGNED);
    }

    public Auth0TenantDto getTenantOrInsert(Handle handle) {
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
            log.info("Created tenant with id={}, domain={}", tenantId, domain);
        } else {
            log.warn("Tenant already exists with id={}, domain={}", dto.getId(), dto.getDomain());
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
            log.info("Created umbrella with id={}, name={}, guid={}", umbrellaId, name, guid);
        } else {
            log.warn("Umbrella already exists with id={}, name={}, guid={}", dto.getId(), dto.getName(), dto.getGuid());
        }

        return dto;
    }

    public StudyDto getStudy(Handle handle) {
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
            log.warn("OLC precision is null.");
            olcPrecision = null;
        } else {
            log.info("OLC precision is " + olcPrecisionString);
            olcPrecision = OLCPrecision.valueOf(olcPrecisionString);
        }

        String recaptchaSiteKey = ConfigUtil.getStrIfPresent(studyCfg, "recaptchaSiteKey");
        boolean shareLocationInformation = studyCfg.getBoolean("shareParticipantLocation");
        String defaultAuth0Connection = ConfigUtil.getStrIfPresent(studyCfg, "defaultAuth0Connection");
        Boolean errorPresent = ConfigUtil.getBoolIfPresent(studyCfg, "errorPresentStatusEnabled");
        boolean errorPresentStatusEnabled = errorPresent != null ? errorPresent : false;

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
                    tenantId, irbPassword, olcPrecisionId, shareLocationInformation, studyEmail, recaptchaSiteKey,
                    defaultAuth0Connection, errorPresentStatusEnabled);
            dto = handle.attach(JdbiUmbrellaStudy.class).findById(studyId);
            log.info("Created study with id={}, name={}, guid={}", studyId, name, guid);
        } else {
            log.warn("Study already exists with id={}, name={}, guid={}", dto.getId(), dto.getName(), dto.getGuid());
        }

        return dto;
    }

    public List<ClientDto> getClientsOrInsert(Handle handle, Auth0TenantDto tenantDto) {
        List<Config> clientsCfg = new ArrayList<>();
        if (cfg.hasPath("client")) {
            clientsCfg.add(cfg.getConfig("client"));
        } else {
            clientsCfg.addAll(cfg.getConfigList("clients"));
        }

        long tenantId = tenantDto.getId();
        JdbiClient jdbiClient = handle.attach(JdbiClient.class);
        List<ClientDto> clientDtos = new ArrayList<>();
        boolean alreadyHasRedirectUrl = false;

        for (var clientCfg : clientsCfg) {
            String clientId = clientCfg.getString("id");
            String clientSecret = clientCfg.getString("secret");
            String passwordRedirectUrl = ConfigUtil.getStrIfPresent(clientCfg, "passwordRedirectUrl");
            if (passwordRedirectUrl != null && alreadyHasRedirectUrl) {
                throw new DDPException("There is already a client with a password redirect URL. Currently only one is allowed.");
            }
            String clientDomain = ConfigUtil.getStrIfPresent(clientCfg, "domain");
            Long clientTenantId = null;
            if (StringUtils.isNotBlank(clientDomain)) {
                //lookup tenant
                JdbiAuth0Tenant jdbiTenant = handle.attach(JdbiAuth0Tenant.class);
                Auth0TenantDto dto = jdbiTenant.findByDomain(clientDomain);
                if (dto == null) {
                    throw new DDPException("Client Domain :" + clientDomain + " not found ");
                }
                clientTenantId = dto.getId();
                log.info("Using client domain: {} ", clientDomain);
            }

            long finalTenantId = (clientTenantId != null) ? clientTenantId : tenantId;
            ClientDto clientDto = jdbiClient
                    .findByAuth0ClientIdAndAuth0TenantId(clientId, finalTenantId)
                    .map(dto -> {
                        log.warn("Client already exists with id={}, auth0ClientId={}", dto.getId(), dto.getAuth0ClientId());
                        return dto;
                    }).orElseGet(() -> {
                        String encryptedSecret = AesUtil.encrypt(clientSecret, EncryptionKey.getEncryptionKey());
                        long id = jdbiClient.insertClient(clientId, encryptedSecret,  finalTenantId, passwordRedirectUrl);
                        log.info("Created client with id={}, auth0ClientId={}", id, clientId);
                        return new ClientDto(id, clientId, encryptedSecret, passwordRedirectUrl, false, tenantId, tenantDto.getDomain());
                    });
            alreadyHasRedirectUrl = alreadyHasRedirectUrl || passwordRedirectUrl != null;
            clientDtos.add(clientDto);
        }

        return clientDtos;
    }

    public void grantClientsAccessToStudy(Handle handle, List<ClientDto> clientDtos, StudyDto studyDto) {
        JdbiClientUmbrellaStudy jdbiACL = handle.attach(JdbiClientUmbrellaStudy.class);
        for (var clientDto : clientDtos) {
            List<String> studyGuids = jdbiACL.findPermittedStudyGuidsByAuth0ClientIdAndAuth0TenantId(
                    clientDto.getAuth0ClientId(),
                    clientDto.getAuth0TenantId()
            );
            if (!studyGuids.contains(studyDto.getGuid())) {
                jdbiACL.insert(clientDto.getId(), studyDto.getId());
                log.info("Granted client {} access to study {}", clientDto.getAuth0ClientId(), studyDto.getGuid());
            } else {
                log.warn("Client {} already has access to study {}", clientDto.getAuth0ClientId(), studyDto.getGuid());
            }
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
            log.info("Created admin user with id={}, guid={}", userId, guid);
        } else {
            log.warn("Admin user already exists with id={}, guid={}", dto.getUserId(), dto.getUserGuid());
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
            log.info("Created study governance policy with id={}, shouldCreateGovernedUserExprId={}, numAgeOfMajorityRules={}",
                    policy.getId(), policy.getShouldCreateGovernedUserExpr().getId(), policy.getAgeOfMajorityRules().size());
        }
    }

    public void insertOrUpdateStudyDetails(Handle handle, long studyId) {
        if (!cfg.hasPath("studyDetails")) {
            return;
        }

        JdbiUmbrellaStudyI18n jdbiStudyI18n = handle.attach(JdbiUmbrellaStudyI18n.class);
        JdbiLanguageCode jdbiLangCode = handle.attach(JdbiLanguageCode.class);
        List<StudyI18nDto> currentDtos = jdbiStudyI18n.findTranslationsByStudyId(studyId);

        for (Config detailCfg : cfg.getConfigList("studyDetails")) {
            String lang = detailCfg.getString("language");
            String name = detailCfg.getString("name");
            String summary = ConfigUtil.getStrIfPresent(detailCfg, "summary");

            Long langCodeId = jdbiLangCode.getLanguageCodeId(lang);
            if (langCodeId == null) {
                throw new DDPException("Could not find language using code: " + lang);
            }

            var latest = new StudyI18nDto(lang, name, summary);
            var current = currentDtos.stream()
                    .filter(d -> d.getLanguageCode().equals(lang))
                    .findFirst().orElse(null);

            if (current == null) {
                long detailId = jdbiStudyI18n.insert(studyId, langCodeId, name, summary);
                log.info("Created study details with id={}, language={}, name={}, summary={}",
                        detailId, lang, name, StringUtils.abbreviate(summary, 50));
            } else if (!current.equals(latest)) {
                DBUtils.checkUpdate(1, jdbiStudyI18n.updateByStudyIdAndLanguage(studyId, lang, name, summary));
                log.info("Updated study details for language {}", lang);
            } else {
                log.info("Study details for language {} already up-to-date", lang);
            }
        }
    }

    public void insertOrUpdateStudyLanguages(Handle handle, long studyId) {
        if (!cfg.hasPath("supportedLanguages")) {
            return;
        }

        JdbiLanguageCode jdbiLangCode = handle.attach(JdbiLanguageCode.class);
        StudyLanguageDao studyLanguageDao = handle.attach(StudyLanguageDao.class);
        List<StudyLanguage> currentLanguages = studyLanguageDao.findLanguages(studyId);

        StudyLanguage chosenDefault = null;
        for (Config languageCfg : cfg.getConfigList("supportedLanguages")) {
            String lang = languageCfg.getString("language");
            String name = ConfigUtil.getStrIfPresent(languageCfg, "name");

            boolean isDefault = languageCfg.hasPath("isDefault") && languageCfg.getBoolean("isDefault");
            if (isDefault && chosenDefault != null) {
                throw new DDPException("Cannot set isDefault: language "
                        + chosenDefault.getLanguageCode() + " was already designated as default");
            }

            Long langCodeId = jdbiLangCode.getLanguageCodeId(lang);
            if (langCodeId == null) {
                throw new DDPException("Could not find language using code: " + lang);
            }

            TranslationsProcessingData.INSTANCE.getLanguages().put(lang, langCodeId);

            var latest = new StudyLanguage(lang, name, isDefault, studyId, langCodeId);
            StudyLanguage current = currentLanguages.stream()
                    .filter(l -> l.getLanguageCode().equals(lang))
                    .findFirst().orElse(null);

            if (current == null) {
                long studyLanguageId = studyLanguageDao.insert(studyId, langCodeId, name);
                log.info("Created study language with id={}, languageCode={} languageName={}", studyLanguageId, lang, name);
            } else if (!current.equals(latest)) {
                studyLanguageDao.update(studyId, langCodeId, name);
                log.info("Updated study language {}", lang);
            } else {
                log.info("Study already has language {}", lang);
            }

            chosenDefault = isDefault ? latest : chosenDefault;
        }

        // Make sure there is one default language.
        if (chosenDefault != null) {
            log.info("Setting language {} as default", chosenDefault.getLanguageCode());
            studyLanguageDao.setAsDefaultLanguage(studyId, chosenDefault.getLanguageId());
        } else {
            log.error("No language is set as default. Please set default language");
            throw new DDPException("No language is set as default");
        }
    }

    private void insertSettings(Handle handle, StudyDto studyDto, long userId) {
        if (!cfg.hasPath("settings")) {
            log.info("No additional settings configured for study {}", studyDto.getGuid());
            return;
        }

        Config settingsCfg = cfg.getConfig("settings");

        Template inviteError = BuilderUtils.parseTemplate(settingsCfg, "inviteErrorTemplate");
        if (inviteError != null) {
            String errors = BuilderUtils.validateTemplate(inviteError);
            if (errors != null) {
                throw new DDPException("Invite error template has validation errors: " + errors);
            }
        }

        Long revisionId = null;
        if (inviteError != null) {
            revisionId = handle.attach(JdbiRevision.class).insertStart(
                    Instant.now().toEpochMilli(), userId, "Insert study settings");
        }

        boolean analyticsEnabled = settingsCfg.hasPath("analyticsEnabled") && settingsCfg.getBoolean("analyticsEnabled");
        String analyticsToken = ConfigUtil.getStrIfPresent(settingsCfg, "analyticsToken");
        boolean shouldDeleteUnsendableEmails = settingsCfg.hasPath("shouldDeleteUnsendableEmails")
                && settingsCfg.getBoolean("shouldDeleteUnsendableEmails");
        boolean shouldDisplayLanguageChangePopup = settingsCfg.hasPath("shouldDisplayLanguageChangePopup")
                && settingsCfg.getBoolean("shouldDisplayLanguageChangePopup");

        handle.attach(StudyDao.class).addSettings(studyDto.getId(), inviteError, revisionId, analyticsEnabled, analyticsToken,
                shouldDeleteUnsendableEmails, shouldDisplayLanguageChangePopup);
        log.info("Created settings for study={}, inviteErrorTmplId={}, analyticsEnabled={}, analyticsToken={},"
                        + " shouldDeleteUnsendableEmails={}, shouldDisplayLanguageChangePopup={}",
                studyDto.getGuid(), inviteError == null ? null : inviteError.getTemplateId(), analyticsEnabled, analyticsToken,
                shouldDeleteUnsendableEmails, shouldDisplayLanguageChangePopup);
    }

    private void insertSendgrid(Handle handle, long studyId) {
        if (!cfg.hasPath("sendgrid")) {
            return;
        }

        Config sendgridCfg = cfg.getConfig("sendgrid");
        String apiKey = sendgridCfg.getString("apiKey");
        String fromName = sendgridCfg.getString("fromName");
        String fromEmail = sendgridCfg.getString("fromEmail");
        String defaultSalutation = sendgridCfg.getString("defaultSalutation");

        long id = handle.attach(JdbiSendgridConfiguration.class).insert(studyId, apiKey, fromName, fromEmail, defaultSalutation);
        log.info("Created sendgrid configuration with id={}, fromName={}, fromEmail={}", id, fromName, fromEmail);
    }

    private void insertKits(Handle handle, long studyId, long userId) {
        if (!cfg.hasPath("kits")) {
            return;
        }

        KitConfigurationDao kitDao = handle.attach(KitConfigurationDao.class);
        KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);

        for (Config kitCfg : cfg.getConfigList("kits")) {
            String type = kitCfg.getString("type");
            int quantity = kitCfg.getInt("quantity");
            boolean needsApproval = kitCfg.hasPath("needsApproval") && kitCfg.getBoolean("needsApproval");

            KitType kitType = kitTypeDao.getKitTypeByName(type)
                    .orElseThrow(() -> new DDPException("Could not find kit type " + type));
            long kitId = kitDao.insertConfiguration(studyId, quantity, kitType.getId(), needsApproval);
            log.info("Created kit configuration with id={}, type={}, quantity={}, needsApproval={}",
                    kitId, type, quantity, needsApproval);

            for (Config ruleCfg : kitCfg.getConfigList("rules")) {
                KitRuleType ruleType = KitRuleType.valueOf(ruleCfg.getString("type"));
                if (ruleType == KitRuleType.PEX) {
                    String expr = ruleCfg.getString("expression");
                    long ruleId = kitDao.addPexRule(kitId, expr);
                    log.info("Added pex rule to kit configuration {} with id={}", kitId, ruleId);
                } else if (ruleType == KitRuleType.COUNTRY) {
                    String country = ruleCfg.getString("country");
                    long ruleId = kitDao.addCountryRule(kitId, country);
                    log.info("Added country rule to kit configuration {} with id={}, country={}", kitId, ruleId, country);
                } else if (ruleType == KitRuleType.ZIP_CODE) {
                    insertKipZipCodeRule(handle, ruleCfg, kitId, userId);
                } else {
                    throw new DDPException("Unsupported kit rule type " + ruleType);
                }
            }

            if (kitCfg.hasPath("schedule")) {
                var schedule = new KitSchedule(
                        kitId,
                        kitCfg.getInt("schedule.numOccurrencesPerUser"),
                        kitCfg.getString("schedule.nextTimeAmount"),
                        ConfigUtil.getStrIfPresent(kitCfg, "schedule.nextPrepTimeAmount"),
                        ConfigUtil.getStrIfPresent(kitCfg, "schedule.optOutExpr"),
                        ConfigUtil.getStrIfPresent(kitCfg, "schedule.individualOptOutExpr"));
                handle.attach(KitScheduleDao.class).createSchedule(schedule);
            }
        }
    }

    private void insertKipZipCodeRule(Handle handle, Config ruleCfg, long kitConfigId, long userId) {
        Template errorMsg = BuilderUtils.parseTemplate(ruleCfg, "errorMessageTemplate");
        if (errorMsg != null) {
            String errors = BuilderUtils.validateTemplate(errorMsg);
            if (errors != null) {
                throw new DDPException("Error message template has validation errors: " + errors);
            }
        }

        Template warningMsg = BuilderUtils.parseTemplate(ruleCfg, "warningMessageTemplate");
        if (warningMsg != null) {
            String errors = BuilderUtils.validateTemplate(warningMsg);
            if (errors != null) {
                throw new DDPException("Warning message template has validation errors: " + errors);
            }
        }

        Long revisionId = null;
        if (errorMsg != null || warningMsg != null) {
            revisionId = handle.attach(JdbiRevision.class).insertStart(
                    Instant.now().toEpochMilli(), userId, "Insert kit zip code rule messages");
        }

        Set<String> zipCodes = Set.copyOf(ruleCfg.getStringList("zipCodes"));
        long ruleId = handle.attach(KitConfigurationDao.class)
                .addZipCodeRule(kitConfigId, zipCodes, errorMsg, warningMsg, revisionId);
        log.info("Added zip code rule to kit configuration {} with id={}, zipCodes={}, errorTmplId={}, warningTmplId={}",
                kitConfigId, ruleId, zipCodes,
                errorMsg == null ? null : errorMsg.getTemplateId(),
                warningMsg == null ? null : warningMsg.getTemplateId());
    }

    private void insertStatistics(Handle handle, long studyId) {
        if (!cfg.hasPath("statistics")) {
            return;
        }
        StatisticsConfigurationDao statConfigDao = handle.attach(StatisticsConfigurationDao.class);
        for (Config statEntry : cfg.getConfigList("statistics")) {
            String typeName = statEntry.getString("type");
            String stableId = statEntry.hasPath("stableId") ? statEntry.getString("stableId") : null;
            String value = statEntry.hasPath("value") ? statEntry.getString("value") : null;
            StatisticsType statType = StatisticsType.valueOf(typeName);
            long statConfigId = statConfigDao.insertConfiguration(studyId, statType, stableId, value);
            log.info("Created statistics configuration with id={}, type={}, stableId={}, value={}",
                    statConfigId, typeName, stableId, value);
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

        @SqlUpdate("update kit_schedule_record as r"
                + "   join kit_configuration as k on k.kit_configuration_id = r.kit_configuration_id"
                + "    set r.opted_out = true, r.num_occurrences = 100"
                + "  where k.study_id = :studyId")
        int invalidateKitScheduleRecords(@Bind("studyId") long studyId);

        @SqlUpdate("update pdf_document_configuration"
                + "    set configuration_name = concat(configuration_name, '-', umbrella_study_id)"
                + "  where umbrella_study_id = :studyId")
        int renamePdfConfigurations(@Bind("studyId") long studyId);

        @SqlUpdate("update umbrella_study"
                + "    set study_name = concat(study_name, '-', umbrella_study_id),"
                + "        guid = :newGuid,"
                + "        umbrella_id = :umbrellaId,"
                + "        enable_data_export = false"
                + "  where umbrella_study_id = :studyId")
        int renameStudy(@Bind("studyId") long studyId, @Bind("newGuid") String newGuid, @Bind("umbrellaId") long umbrellaId);

        default UmbrellaDto findOrInsertUmbrella(String umbrella) {
            var jdbiUmbrella = getHandle().attach(JdbiUmbrella.class);
            return jdbiUmbrella.findByGuid(umbrella).or(() -> {
                long id = jdbiUmbrella.insert(umbrella, umbrella);
                return jdbiUmbrella.findById(id);
            }).get();
        }
    }
}
