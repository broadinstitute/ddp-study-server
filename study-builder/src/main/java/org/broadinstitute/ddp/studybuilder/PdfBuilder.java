package org.broadinstitute.ddp.studybuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.typesafe.config.Config;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiPdfTemplates;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiStudyPdfMapping;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.pdf.PdfTemplateDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.BooleanAnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.MailingAddressTemplate;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfDataSource;
import org.broadinstitute.ddp.model.pdf.PdfDataSourceType;
import org.broadinstitute.ddp.model.pdf.PdfTemplate;
import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.PhysicianInstitutionTemplate;
import org.broadinstitute.ddp.model.pdf.ProfileSubstitution;
import org.broadinstitute.ddp.model.pdf.SubstitutionType;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PdfBuilder.class);

    private Path dirPath;
    private Config cfg;
    private StudyDto studyDto;
    private long adminUserId;

    public PdfBuilder(Path dirPath, Config cfg, StudyDto studyDto, long adminUserId) {
        this.dirPath = dirPath;
        this.cfg = cfg;
        this.studyDto = studyDto;
        this.adminUserId = adminUserId;
    }

    // todo: deprecate this feature
    public void updatePdfs(Handle handle) {
        for (Config pdfCfg : cfg.getConfigList("pdfs")) {
            PdfMappingType mappingType = null;
            if (pdfCfg.hasPath("mapping")) {
                Config mapping = pdfCfg.getConfig("mapping");
                mappingType = PdfMappingType.valueOf(mapping.getString("type"));
            }
            updatePdfPages(handle, pdfCfg, mappingType);
        }
    }

    private void updatePdfPages(Handle handle, Config pdfCfg, PdfMappingType mappingType) {
        JdbiPdfTemplates jdbiPdfTemplates = handle.attach(JdbiPdfTemplates.class);
        List<? extends Config> pages = pdfCfg.getConfigList("pages");
        for (int pageItr = 0; pageItr < pages.size(); pageItr++) {
            Config pageCfg = pages.get(pageItr);
            File file = dirPath.resolve(pageCfg.getString("filepath")).toFile();
            if (!file.exists()) {
                throw new DDPException("Pdf page file is missing: " + file);
            }

            byte[] pdfBytes;
            try (FileInputStream input = new FileInputStream(file)) {
                pdfBytes = IOUtils.toByteArray(input);
            } catch (IOException e) {
                throw new DDPException(e);
            }

            long baseTemplateid;
            String pageType = pageCfg.getString("type");
            if (PdfTemplateType.CUSTOM.name().equals(pageType)) {
                Optional<PdfTemplateDto> pdfTemplateDto = jdbiPdfTemplates.getCustomPdfTemplateByStudyAndType(
                        studyDto.getGuid(), mappingType.name());
                if (pdfTemplateDto.isPresent()) {
                    baseTemplateid = pdfTemplateDto.get().getId();
                    //update bytes
                    updatePdfPage(handle, baseTemplateid, pdfBytes, pageType);
                } else {
                    LOG.warn("Custom template not found for study : {} mapping type: {}. skipping update... ",
                            studyDto.getGuid(), mappingType);
                }
            } else if (PdfTemplateType.MAILING_ADDRESS.name().equals(pageType)) {
                Optional<PdfTemplateDto> pdfTemplateDto = jdbiPdfTemplates.getMailingAddressPdfTemplateByStudy(studyDto.getGuid());
                if (pdfTemplateDto.isPresent()) {
                    baseTemplateid = pdfTemplateDto.get().getId();
                    updatePdfPage(handle, baseTemplateid, pdfBytes, pageType);
                } else {
                    LOG.warn("Mailing address template not found for study : {}. skipping update... ",
                            studyDto.getGuid());
                }
            } else if (InstitutionType.PHYSICIAN.name().equals(pageType) || InstitutionType.INITIAL_BIOPSY.name().equals(pageType)
                    || InstitutionType.INSTITUTION.name().equals(pageType)) {
                Optional<PdfTemplateDto> pdfTemplateDto = jdbiPdfTemplates.getPhysicianInstitutionPdfTemplateByTypeAndStudy(
                        studyDto.getGuid(), pageType);
                if (pdfTemplateDto.isPresent()) {
                    baseTemplateid = pdfTemplateDto.get().getId();
                    updatePdfPage(handle, baseTemplateid, pdfBytes, pageType);
                } else {
                    LOG.warn("Physician institute template not found for study : {} page type: {}. skipping update... ",
                            studyDto.getGuid(), pageType);
                }
            } else {
                throw new DDPException("Unsupported pdf page type: " + pageType);
            }
        }
    }

    private void updatePdfPage(Handle handle, long baseTemplateid, byte[] pdfBytes, String pageType) {
        handle.attach(JdbiPdfTemplates.class).updatePdfBaseTemplate(baseTemplateid, pdfBytes);
        LOG.info("Updated pdf bytes for pdfBaseTemplateId: {} study:{} pageType: {}",
                baseTemplateid, studyDto.getGuid(), pageType);
    }

    void run(Handle handle) {
        for (Config pdfCfg : cfg.getConfigList("pdfs")) {
            insertPdfConfig(handle, pdfCfg);
        }
    }

    public long insertPdfConfig(Handle handle, Config pdfCfg) {
        List<? extends Config> versions = pdfCfg.getConfigList("versions");
        if (versions.isEmpty()) {
            throw new DDPException("Need to have at least one version for pdf with name=" + pdfCfg.getString("name"));
        }

        PdfDao pdfDao = handle.attach(PdfDao.class);

        long pdfId = -1L;
        for (Config versionCfg : versions) {
            long revId = createRevision(handle, pdfCfg, versionCfg);
            PdfConfiguration pdf = buildPdfConfiguration(handle, revId, pdfCfg, versionCfg);
            boolean hasExisting = pdfDao.findConfigInfoByStudyIdAndName(pdf.getStudyId(), pdf.getConfigName()).isPresent();
            if (hasExisting) {
                long versionId = pdfDao.insertNewConfigVersion(pdf);
                pdfId = pdf.getId();
                LOG.info("Added pdf configuration version for id={} with name={}, filename={}, displayName={}, versionId={}, versionTag={}",
                        pdfId, pdf.getConfigName(), pdf.getFilename(), pdf.getDisplayName(), pdf.getVersion().getVersionTag());
            } else {
                pdfId = pdfDao.insertNewConfig(pdf);
                LOG.info("Created pdf configuration with id={}, name={}, filename={}, displayName={}, versionId={}, versionTag={}",
                        pdfId, pdf.getConfigName(), pdf.getFilename(), pdf.getDisplayName(),
                        pdf.getVersion().getId(), pdf.getVersion().getVersionTag());
            }
        }

        if (pdfCfg.hasPath("mapping")) {
            Config mapping = pdfCfg.getConfig("mapping");
            PdfMappingType type = PdfMappingType.valueOf(mapping.getString("type"));
            long mappingId = handle.attach(JdbiStudyPdfMapping.class).insert(studyDto.getId(), type, pdfId);
            LOG.info("Added mapping for pdf id {} with id={}, type={}", pdfId, mappingId, type);
        }

        return pdfId;
    }

    private long createRevision(Handle handle, Config pdfCfg, Config versionCfg) {
        String name = pdfCfg.getString("name");
        String versionTag = versionCfg.getString("tag");
        String reason = String.format(
                "Create pdf configuration for study=%s with name=%s version=%s",
                studyDto.getGuid(), name, versionTag);

        Instant start = ConfigUtil.getInstantIfPresent(versionCfg, "start");
        if (start == null) {
            start = Instant.now();
        }

        long revId;
        Instant end = ConfigUtil.getInstantIfPresent(versionCfg, "end");
        if (end == null) {
            revId = handle.attach(JdbiRevision.class).insertStart(start.toEpochMilli(), adminUserId, reason);
        } else {
            String terminatedReason = String.format(
                    "Terminate pdf configuration for study=%s with name=%s version=%s",
                    studyDto.getGuid(), name, versionTag);
            revId = handle.attach(JdbiRevision.class).insertTerminated(
                    start.toEpochMilli(), end.toEpochMilli(), adminUserId, reason, adminUserId, terminatedReason);
        }

        return revId;
    }

    private PdfConfiguration buildPdfConfiguration(Handle handle, long revId, Config pdfCfg, Config versionCfg) {
        PdfConfigInfo info = new PdfConfigInfo(studyDto.getId(), pdfCfg.getString("name"),
                pdfCfg.getString("filename"), pdfCfg.getString("displayName"));
        PdfVersion version = buildPdfVersion(handle, revId, versionCfg);

        List<PdfTemplate> templates = new ArrayList<>();
        for (Config fileCfg : versionCfg.getConfigList("files")) {
            templates.add(buildPdfTemplate(handle, fileCfg));
        }

        return new PdfConfiguration(info, version, templates);
    }

    private PdfVersion buildPdfVersion(Handle handle, long revId, Config versionCfg) {
        PdfVersion version = new PdfVersion(versionCfg.getString("tag"), revId);

        JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);
        for (Config sourceCfg : versionCfg.getConfigList("sources")) {
            PdfDataSource source;
            PdfDataSourceType type = PdfDataSourceType.valueOf(sourceCfg.getString("type"));
            switch (type) {
                case ACTIVITY:
                    String activityCode = sourceCfg.getString("activityCode");
                    String versionTag = sourceCfg.getString("versionTag");
                    ActivityVersionDto activityVersionDto = jdbiActivityVersion
                            .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag)
                            .orElseThrow(() -> new DDPException(String.format(
                                    "Could not find activity version for pdf data source using activityCode=%s versionTag=%s",
                                    activityCode, version)));
                    source = new PdfActivityDataSource(activityVersionDto.getActivityId(), activityVersionDto.getId());
                    break;
                case EMAIL:         // fall-through
                case PARTICIPANT:
                    source = new PdfDataSource(type);
                    break;
                default:
                    throw new DDPException("Unsupported pdf data source type " + type);
            }
            version.addDataSource(source);
        }

        return version;
    }

    private PdfTemplate buildPdfTemplate(Handle handle, Config fileCfg) {
        File filepath = dirPath.resolve(fileCfg.getString("filepath")).toFile();
        if (!filepath.exists()) {
            throw new DDPException("Pdf file is missing: " + filepath);
        }

        byte[] rawBytes;
        try (FileInputStream input = new FileInputStream(filepath)) {
            rawBytes = IOUtils.toByteArray(input);
        } catch (IOException e) {
            throw new DDPException(e);
        }

        String type = fileCfg.getString("type");
        if (PdfTemplateType.CUSTOM.name().equals(type)) {
            return buildCustomTemplate(handle, fileCfg, rawBytes);
        } else if (PdfTemplateType.MAILING_ADDRESS.name().equals(type)) {
            return buildMailingAddressTemplate(fileCfg, rawBytes);
        } else if (InstitutionType.PHYSICIAN.name().equals(type)) {
            return buildProviderTemplate(fileCfg, InstitutionType.PHYSICIAN, rawBytes);
        } else if (InstitutionType.INITIAL_BIOPSY.name().equals(type)) {
            return buildProviderTemplate(fileCfg, InstitutionType.INITIAL_BIOPSY, rawBytes);
        } else if (InstitutionType.INSTITUTION.name().equals(type)) {
            return buildProviderTemplate(fileCfg, InstitutionType.INSTITUTION, rawBytes);
        } else {
            throw new DDPException("Unsupported pdf template file type " + type);
        }
    }

    private PdfTemplate buildMailingAddressTemplate(Config fileCfg, byte[] rawBytes) {
        return new MailingAddressTemplate(
                rawBytes,
                ConfigUtil.getStrIfPresent(fileCfg, "fields.firstName"),
                ConfigUtil.getStrIfPresent(fileCfg, "fields.lastName"),
                ConfigUtil.getStrIfPresent(fileCfg, "fields.proxyFirstName"),
                ConfigUtil.getStrIfPresent(fileCfg, "fields.proxyLastName"),
                fileCfg.getString("fields.street"),
                fileCfg.getString("fields.city"),
                fileCfg.getString("fields.state"),
                fileCfg.getString("fields.zip"),
                ConfigUtil.getStrIfPresent(fileCfg, "fields.country"),
                fileCfg.getString("fields.phone"));
    }

    private PdfTemplate buildProviderTemplate(Config fileCfg, InstitutionType type, byte[] rawBytes) {
        return new PhysicianInstitutionTemplate(
                rawBytes,
                type,
                type == InstitutionType.PHYSICIAN ? fileCfg.getString("fields.name") : null,
                fileCfg.getString("fields.institution"),
                fileCfg.getString("fields.city"),
                fileCfg.getString("fields.state"),
                ConfigUtil.getStrIfPresent(fileCfg, "fields.street"),
                ConfigUtil.getStrIfPresent(fileCfg, "fields.zip"),
                ConfigUtil.getStrIfPresent(fileCfg, "fields.phone"));
    }

    private PdfTemplate buildCustomTemplate(Handle handle, Config fileCfg, byte[] rawBytes) {
        CustomTemplate template = new CustomTemplate(rawBytes);
        for (Config subCfg : fileCfg.getConfigList("substitutions")) {
            String type = subCfg.getString("type");
            String field = subCfg.getString("field");

            if (SubstitutionType.PROFILE.name().equals(type)) {
                String profileField = subCfg.getString("profileField");
                template.addSubstitution(new ProfileSubstitution(field, profileField));
                continue;
            }

            String activityCode = subCfg.getString("activityCode");
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

            if (SubstitutionType.ACTIVITY_DATE.name().equals(type)) {
                template.addSubstitution(new ActivityDateSubstitution(field, activityId));
            } else if (QuestionType.TEXT.name().equals(type)) {
                String stableId = subCfg.getString("questionStableId");
                template.addSubstitution(new AnswerSubstitution(field, activityId, QuestionType.TEXT, stableId));
            } else if (QuestionType.DATE.name().equals(type)) {
                String stableId = subCfg.getString("questionStableId");
                template.addSubstitution(new AnswerSubstitution(field, activityId, QuestionType.DATE, stableId));
            } else if (QuestionType.BOOLEAN.name().equals(type)) {
                String stableId = subCfg.getString("questionStableId");
                boolean checkIfFalse = subCfg.getBoolean("checkIfFalse");
                template.addSubstitution(new BooleanAnswerSubstitution(field, activityId, stableId, checkIfFalse));
            } else if (QuestionType.PICKLIST.name().equals(type)) {
                String stableId = subCfg.getString("questionStableId");
                template.addSubstitution(new AnswerSubstitution(field, activityId, QuestionType.PICKLIST, stableId));
            } else {
                throw new DDPException("Unsupported custom pdf substitution type " + type);
            }
        }

        return template;
    }
}
