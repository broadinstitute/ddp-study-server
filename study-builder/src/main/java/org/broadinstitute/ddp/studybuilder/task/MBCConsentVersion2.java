package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiBlockContent;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MBCConsentVersion2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(MBCConsentVersion2.class);
    private static final String DATA_FILE = "patches/tissue-blood-consent-version2.conf";
    private static final String MBC_STUDY = "cmi-mbc";

    private Path cfgPath;
    private Config cfg;
    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);

        if (!studyCfg.getString("study.guid").equals(MBC_STUDY)) {
            throw new DDPException("This task is only for the " + MBC_STUDY + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.parse(dataCfg.getString("timestamp"));
        this.cfgPath = cfgPath;
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        PdfBuilder builder = new PdfBuilder(cfgPath.getParent(), cfg, studyDto, adminUser.getId());

        String activityCode = dataCfg.getString("activityCode.tissue");
        LOG.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        revisionConsent("tissue", handle, adminUser.getId(), studyDto, activityCode, versionTag, timestamp.toEpochMilli());

        LOG.info("Adding new pdf version for tissue consent");
        builder.insertPdfConfig(handle, dataCfg.getConfig("tissueconsentPdfV2"));
        addNewConsentDataSourceToReleasePdf(handle, studyDto.getId(), dataCfg.getString("tissuereleasePdfName"), activityCode, versionTag);

        activityCode = dataCfg.getString("activityCode.blood");
        LOG.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        revisionConsent("blood", handle, adminUser.getId(), studyDto, activityCode, versionTag, timestamp.toEpochMilli());

        LOG.info("Adding new pdf version for blood consent");
        builder.insertPdfConfig(handle, dataCfg.getConfig("bloodconsentPdfV2"));
        addNewConsentDataSourceToReleasePdf(handle, studyDto.getId(), dataCfg.getString("bloodreleasePdfName"), activityCode, versionTag);
    }

    private void revisionConsent(String key, Handle handle, long adminUserId, StudyDto studyDto,
                                 String activityCode, String versionTag, long timestamp) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp, adminUserId, reason);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);

        revisionContentBlock(handle, meta, version2, key, "s1_participation_detail");
        revisionContentBlock(handle, meta, version2, key, "s1_risks_detail");
        revisionContentBlock(handle, meta, version2, key, "s1_contact_detail");
        revisionContentBlock(handle, meta, version2, key, "s2_involvement_detail");
        revisionContentBlock(handle, meta, version2, key, "s2_timing_detail");
        revisionContentBlock(handle, meta, version2, key, "s2_risks_detail");
        revisionContentBlock(handle, meta, version2, key, "s2_confidentiality_detail");
        revisionContentBlock(handle, meta, version2, key, "s2_authorization_detail");
        revisionContentBlock(handle, meta, version2, key, "s3_agree_list");
    }

    private void revisionContentBlock(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto, String key, String part) {
        String oldName = String.format("mbc_%sconsent_%s", key, part);
        String newName = String.format("mbc_%sconsent_v2_%s", key, part);
        String bodyTemplateText = "$" + oldName;

        Template newTemplate = Template.html("$" + newName);
        String newTemplateText = dataCfg.getConfig(key).getString(part);
        newTemplate.addVariable(TemplateVariable.single(newName, "en", newTemplateText));

        JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);
        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
                .findContentBlockByBodyText(versionDto.getActivityId(), bodyTemplateText);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newRevId = jdbiRevision.copyAndTerminate(contentBlock.getRevisionId(), meta);
        int numUpdated = jdbiBlockContent.updateRevisionById(contentBlock.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DDPException(String.format(
                    "Unable to terminate active block_content with id=%d, blockId=%d, bodyTemplateId=%d, bodyTemplateText=%s",
                    contentBlock.getId(), contentBlock.getBlockId(), contentBlock.getBodyTemplateId(), bodyTemplateText));
        }

        TemplateDao templateDao = handle.attach(TemplateDao.class);
        templateDao.disableTemplate(contentBlock.getBodyTemplateId(), meta);
        long newTemplateId = templateDao.insertTemplate(newTemplate, versionDto.getRevId());
        long newBlockContentId = jdbiBlockContent.insert(contentBlock.getBlockId(), newTemplateId,
                contentBlock.getTitleTemplateId(), versionDto.getRevId());

        LOG.info("Created block_content with id={}, blockId={}, bodyTemplateId={} for bodyTemplateText={}",
                newBlockContentId, contentBlock.getBlockId(), newTemplateId, bodyTemplateText);
    }

    private void addNewConsentDataSourceToReleasePdf(Handle handle, long studyId, String pdfName, String activityCode, String versionTag) {
        PdfDao pdfDao = handle.attach(PdfDao.class);
        JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyId, pdfName)
                .orElseThrow(() -> new DDPException("Could not find pdf with name=" + pdfName));

        PdfVersion version = pdfDao.findOrderedConfigVersionsByConfigId(info.getId())
                .stream()
                .filter(ver -> ver.getAcceptedActivityVersions().containsKey(activityCode))
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find pdf version with data source for activityCode=" + activityCode));

        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);
        long activityVersionId = jdbiActivityVersion.findByActivityCodeAndVersionTag(studyId, activityCode, versionTag)
                .map(ActivityVersionDto::getId)
                .orElseThrow(() -> new DDPException(String.format(
                        "Could not find activity version id for activityCode=%s versionTag=%s", activityCode, versionTag)));

        pdfDao.insertDataSource(version.getId(), new PdfActivityDataSource(activityId, activityVersionId));

        LOG.info("Added activity data source with activityCode={} versionTag={} to pdf {} version {}",
                activityCode, versionTag, info.getConfigName(), version.getVersionTag());
    }

    private interface SqlHelper extends SqlObject {
        /**
         * Find the content block that has the given body template text. Make sure it is from a block that belongs in the expected activity
         * (and thus the expected study). This is done using a `union` subquery to find all the top-level and nested block ids for the
         * activity and using that to match on the content block.
         */
        @SqlQuery("select bt.* from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + " where tmpl.template_text = :text"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        @RegisterConstructorMapper(BlockContentDto.class)
        BlockContentDto findContentBlockByBodyText(@Bind("activityId") long activityId, @Bind("text") String bodyTemplateText);
    }
}
