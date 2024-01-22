package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.elastic.export.Exportable;

@Slf4j
public class StudyMigrator {

    public static void migrate(String studyName) {
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(studyName)
                .orElseThrow(() -> new DSMBadRequestException("DDP instance not found for study " + studyName));
        String index = ddpInstanceDto.getEsParticipantIndex();

        log.info("Starting migration of DSM data to ES index {} for study {}", index, studyName);
        List<? extends Exportable> exportables = Arrays.asList(
                //DynamicFieldsMappingMigrator should be first in the list to make sure that mapping will be exported for first
                new DynamicFieldsMappingMigrator(index, studyName),
                new MedicalRecordMigrator(index, studyName),
                new OncHistoryDetailsMigrator(index, studyName),
                new OncHistoryMigrator(index, studyName),
                new ParticipantDataMigrator(index, studyName),
                AdditionalParticipantMigratorFactory.of(index, studyName),
                new ParticipantMigrator(index, studyName),
                new KitRequestShippingMigrator(index, studyName),
                new TissueMigrator(index, studyName),
                new SMIDMigrator(index, studyName),
                new CohortTagMigrator(index, studyName, new CohortTagDaoImpl()),
                new ClinicalOrderMigrator(index, studyName, new ClinicalOrderDao()),
                new SomaticResultMigrator(index, studyName));
        exportables.forEach(Exportable::export);
        log.info("Finished migration of DSM data to ES index {} for study {}", index, studyName);
    }
}
