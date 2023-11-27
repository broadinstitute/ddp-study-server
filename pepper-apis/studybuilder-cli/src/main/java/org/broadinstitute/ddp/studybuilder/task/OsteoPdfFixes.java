package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.JdbiPdfTemplates;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.PdfSql;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfSubstitution;
import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.SubstitutionType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OsteoPdfFixes implements CustomTask {
    private static final String ACTIVITY_DATA_FILE = "patches/pdf-fixes.conf";
    private static final String V2_VERSION_TAG = "v2";

    private Config studyCfg;
    private Config dataCfg;
    private Path cfgPath;

    private Handle handle;
    private StudyDto studyDto;
    private PdfDao pdfDao;
    private PdfSql pdfSql;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.studyCfg = studyCfg;
        this.cfgPath = cfgPath;
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.handle = handle;
        this.pdfDao = handle.attach(PdfDao.class);
        this.pdfSql = handle.attach(PdfSql.class);

        for (var activityCfg : dataCfg.getConfigList("pdfTemplates")) {
            updateActivity(activityCfg);
        }
    }

    private void updateActivity(Config pdfCfg) {
        String pdfConfigName = pdfCfg.getString("name");
        log.info("Editing pdf {}", pdfConfigName);

        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), pdfConfigName)
                .orElseThrow(() -> new DDPException("Could not find release pdf info"));

        PdfVersion pdfVersion = pdfDao.findOrderedConfigVersionsByConfigId(info.getId())
                .stream()
                .filter(v -> v.getVersionTag().equals(V2_VERSION_TAG))
                .findFirst()
                .orElseThrow(() -> new DDPException("Expected release pdf version 2 but not found"));

        CustomTemplate customTemplate = (CustomTemplate) pdfDao.findFullTemplatesByVersionId(pdfVersion.getId())
                .stream()
                .filter(pdfTemplate -> pdfTemplate.getType().equals(PdfTemplateType.CUSTOM))
                .findFirst()
                .orElseThrow(() -> new DDPException("Expected custom template but not found"));

        if (!customTemplate.getSubstitutions().isEmpty()) {
            throw new DDPException(pdfConfigName + " has already needed substitution");
        }

        List<PdfSubstitution> substitutions = new ArrayList<>();

        for (Config subCfg : pdfCfg.getConfigList("substitutions")) {
            String type = subCfg.getString("type");
            String field = ConfigUtil.getStrIfPresent(subCfg, "field");

            String activityCode = subCfg.getString("activityCode");
            long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
            String parentStableId = ConfigUtil.getStrIfPresent(subCfg, "parentQuestionStableId");

            if (SubstitutionType.ACTIVITY_DATE.name().equals(type)) {
                substitutions.add(new ActivityDateSubstitution(field, activityId));
            } else if (QuestionType.TEXT.name().equals(type)) {
                String stableId = subCfg.getString("questionStableId");
                substitutions.add(new AnswerSubstitution(field, activityId, QuestionType.TEXT, stableId, parentStableId));
            } else if (QuestionType.DATE.name().equals(type)) {
                String stableId = subCfg.getString("questionStableId");
                substitutions.add(new AnswerSubstitution(field, activityId, QuestionType.DATE, stableId, parentStableId));
            } else {
                throw new DDPException("Unsupported custom pdf substitution type " + type + " for this task");
            }
        }

        log.info("Was collected {} substitutions", substitutions.size());

        for (PdfSubstitution substitution : substitutions) {
            substitution.setTemplateId(customTemplate.getId());
            long subId = insertSubstitution(substitution);
            pdfSql.assignSubstitutionToCustomTemplate(customTemplate.getId(), subId);
        }

        if (pdfCfg.hasPath("filepath")) {
            File filepath = cfgPath.getParent().resolve(pdfCfg.getString("filepath")).toFile();
            if (!filepath.exists()) {
                throw new DDPException("Pdf file is missing: " + filepath);
            }

            byte[] rawBytes;
            try (FileInputStream input = new FileInputStream(filepath)) {
                rawBytes = IOUtils.toByteArray(input);
            } catch (IOException e) {
                throw new DDPException(e);
            }

            handle.attach(JdbiPdfTemplates.class).updatePdfBaseTemplate(customTemplate.getId(), rawBytes);
            log.info("Updated pdf bytes for pdfBaseTemplateId: {} filepath: {}", customTemplate.getId(), pdfCfg.getString("filepath"));
        }

        log.info("PDF {} was successfully updated", pdfConfigName);
    }


    long insertSubstitution(PdfSubstitution substitution) {
        long subId = pdfSql.insertBaseSubstitution(substitution.getPlaceholder(), substitution.getType());
        substitution.setId(subId);
        switch (substitution.getType()) {
            case ACTIVITY_DATE:
                DBUtils.checkInsert(1, pdfSql.insertActivityDateSubstitution(
                        subId, ((ActivityDateSubstitution) substitution).getActivityId()));
                break;
            case ANSWER:
                insertAnswerSubstitution((AnswerSubstitution) substitution);
                break;
            default:
                throw new DaoException("unhandled pdf substitution type " + substitution.getType());
        }
        return subId;
    }

    private void insertAnswerSubstitution(AnswerSubstitution substitution) {
        DBUtils.checkInsert(1, pdfSql.insertBaseAnswerSubstitution(
                substitution.getId(), substitution.getActivityId(),
                substitution.getQuestionStableId(), substitution.getParentQuestionStableId()));
    }
}
