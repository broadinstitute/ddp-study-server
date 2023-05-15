package org.broadinstitute.ddp.studybuilder.task.rootpatches;

import com.typesafe.config.Config;
import org.apache.commons.cli.ParseException;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.studybuilder.task.OsteoSomaticAssentV3;
import org.broadinstitute.ddp.studybuilder.task.SimpleActivityRevisionTask;
import org.broadinstitute.ddp.studybuilder.task.SimplePdfRevisionTask;
import org.broadinstitute.ddp.studybuilder.task.osteo.Osteo2GermlineConsentAddendumPdfV3;
import org.broadinstitute.ddp.studybuilder.task.osteo.Osteo2GermlineConsentVersion3;
import org.broadinstitute.ddp.studybuilder.task.osteo.Osteo2GermlinePedConsentVersion3;
import org.broadinstitute.ddp.studybuilder.task.osteo.Osteo2SomaticConsentVersion3;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoConsentVersion3;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoSomaticConsentAddendumPdfV3;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoSomaticConsentPedVersion3;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Osteo2pecgsUpdates implements CustomTask {

    private List<CustomTask> taskList = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        taskList.add(new OsteoConsentVersion3());
        taskList.add(new Osteo2SomaticConsentVersion3());
        taskList.add(new OsteoSomaticAssentV3()); //assent portion of  CONSENT_ADDENDUM_PEDIATRIC
        taskList.add(new OsteoSomaticConsentPedVersion3()); //consent portion of  CONSENT_ADDENDUM_PEDIATRIC
        taskList.add(new OsteoSomaticConsentAddendumPdfV3());
        taskList.add(new Osteo2GermlineConsentVersion3());
        taskList.add(new Osteo2GermlinePedConsentVersion3());
        taskList.add(new Osteo2GermlineConsentAddendumPdfV3());

        SimpleActivityRevisionTask osteoPediatricConsentAndAssentVersion3 = new SimpleActivityRevisionTask();
        SimpleActivityRevisionTask osteoMedicalRecordTextUpdateVersion3 = new SimpleActivityRevisionTask();
        taskList.add(osteoPediatricConsentAndAssentVersion3);
        taskList.add(osteoMedicalRecordTextUpdateVersion3);

        SimplePdfRevisionTask osteoPdfRevisionVersion3 = new SimplePdfRevisionTask();
        SimplePdfRevisionTask osteoMRPdfRevisionVersion3 = new SimplePdfRevisionTask();
        taskList.add(osteoPdfRevisionVersion3);
        taskList.add(osteoMRPdfRevisionVersion3);

        taskList.forEach(task -> task.init(cfgPath, studyCfg, varsCfg));

        try {
            osteoPediatricConsentAndAssentVersion3.consumeArguments(
                    new String[]{"patches/osteo-version-3-changes.conf"});
            osteoPdfRevisionVersion3.consumeArguments(
                    new String[]{"patches/osteo-consent-parental-v3.conf"});
            osteoMedicalRecordTextUpdateVersion3.consumeArguments(
                    new String[]{"patches/osteo-medical-records-release-text-version3-changes.conf"});
            osteoMRPdfRevisionVersion3.consumeArguments(
                    new String[]{"patches/osteo-v3-medicalrecords-pdfs.conf"}
            );
        } catch (ParseException parseException) {
            throw new DDPException(parseException.getMessage());
        }
    }

    @Override
    public void run(Handle handle) {
        taskList.forEach(task -> task.run(handle));
    }
}
