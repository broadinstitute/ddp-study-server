package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.service.adminoperation.ExportLog;

@Slf4j
public class StudyMigrator {

    /**
     * Export study data to ES for a list of participants
     *
     * @param exportLogs  list export logs to append to (can be null if not using export logs)
     */
    public static void migrate(String studyName, List<ExportLog> exportLogs) {
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
                //!! TEMP AdditionalParticipantMigratorFactory.of(index, studyName),
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

    /**
     * Export study data to ES for a list of participants
     *
     * @param exportLogs  list export logs to append to
     */
    public static void migrateParticipants(List<String> ddpParticipantIds, String studyName,
                                           List<ExportLog> exportLogs) {
        if (ddpParticipantIds.isEmpty()) {
            throw new DsmInternalError("Empty participant list for migrateParticipants");
        }
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(studyName)
                .orElseThrow(() -> new DSMBadRequestException("DDP instance not found for study " + studyName));
        // TODO: I'm not sure how these studies work (or don't work) for StudyMigrator.migrate, so not supporting
        // them here until I can figure that out. Specifically, I'm unsure why 'AdditionalParticipantMigratorFactory' is
        // part of that export. -DC
        if (List.of("OSTEO", "OSTEO2").contains(studyName.toUpperCase())) {
            throw new DSMBadRequestException("Study is not supported for participant migration: " + studyName);
        }
        String index = ddpInstanceDto.getEsParticipantIndex();

        log.info("Migrating DSM data for {} participants to ES index {} for study {}",
                ddpParticipantIds.size(), index, studyName);
        List<? extends BaseMigrator> exportables = Arrays.asList(
                new MedicalRecordMigrator(index, studyName),
                new OncHistoryDetailsMigrator(index, studyName),
                new OncHistoryMigrator(index, studyName),
                new ParticipantDataMigrator(index, studyName),
                new ParticipantMigrator(index, studyName),
                new KitRequestShippingMigrator(index, studyName),
                new TissueMigrator(index, studyName),
                new SMIDMigrator(index, studyName),
                new CohortTagMigrator(index, studyName, new CohortTagDaoImpl()),
                new ClinicalOrderMigrator(index, studyName, new ClinicalOrderDao()),
                new SomaticResultMigrator(index, studyName));
        exportables.forEach(migrator -> migrator.exportParticipants(ddpParticipantIds, exportLogs));
        log.info("Finished migrating DSM data for {} participants to ES index {} for study {}",
                ddpParticipantIds.size(), index, studyName);
    }
}
