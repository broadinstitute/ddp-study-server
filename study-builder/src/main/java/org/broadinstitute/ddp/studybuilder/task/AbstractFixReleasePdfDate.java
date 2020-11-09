package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfSubstitution;
import org.broadinstitute.ddp.model.pdf.PdfTemplate;
import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pdf.SubstitutionType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

/**
 * Task to update the ACTIVITY_DATE substitution for the custom template in the release pdf for a study. The substitution should reference
 * the release activity, if it's not it will be updated.
 */
abstract class AbstractFixReleasePdfDate implements CustomTask {

    private String studyGuid;
    private String releasePdfConfigName;
    private String releasePdfVersionTag;
    private String releaseActivityCode;

    AbstractFixReleasePdfDate(String studyGuid, String releasePdfConfigName, String releasePdfVersionTag, String releaseActivityCode) {
        this.studyGuid = studyGuid;
        this.releasePdfConfigName = releasePdfConfigName;
        this.releasePdfVersionTag = releasePdfVersionTag;
        this.releaseActivityCode = releaseActivityCode;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }
    }

    boolean fixActivityDateSubstitution(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        PdfDao pdfDao = handle.attach(PdfDao.class);
        PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), releasePdfConfigName)
                .orElseThrow(() -> new DDPException("Could not find release pdf info"));
        PdfVersion version = pdfDao.findConfigVersionByConfigIdAndVersionTag(info.getId(), releasePdfVersionTag)
                .orElseThrow(() -> new DDPException("Could not find release pdf version"));
        PdfConfiguration config = pdfDao.findFullConfig(info, version);

        ActivityDateSubstitution sub = null;
        List<PdfTemplate> templates =
                config.getTemplateIds().stream()
                        .map(id -> handle.attach(PdfDao.class)
                                .findFullTemplateByTemplateId(id)
                                .orElseThrow(()-> new DDPException("Could not find template with id: " + id)))
                                .collect(Collectors.toList());
        for (PdfTemplate template : templates) {
            if (template.getType() == PdfTemplateType.CUSTOM) {
                for (PdfSubstitution substitution : ((CustomTemplate) template).getSubstitutions()) {
                    if (substitution.getType() == SubstitutionType.ACTIVITY_DATE) {
                        if (sub != null) {
                            throw new DDPException("Found more than one ACTIVITY_DATE substitution in release pdf custom template");
                        }
                        sub = (ActivityDateSubstitution) substitution;
                    }
                }
            }
        }
        if (sub == null) {
            throw new DDPException("Could not find ACTIVITY_DATE substitution in release pdf configuration");
        }

        long releaseActId = ActivityBuilder.findActivityId(handle, studyDto.getId(), releaseActivityCode);
        if (sub.getActivityId() == releaseActId) {
            return false;
        } else {
            DBUtils.checkUpdate(1, pdfDao.getPdfSql().updateActivityDateSubstitution(sub.getId(), releaseActId));
            return true;
        }
    }
}
