package org.broadinstitute.dsm.model.elastic.migration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
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
        DynamicFieldsMappingMigrator dynamicFieldsMigrator = new DynamicFieldsMappingMigrator(index, studyName);
        dynamicFieldsMigrator.export();

        // TODO: use getMigators once this list is adjusted.  -DC
        List<? extends BaseMigrator> exportables = Arrays.asList(
                new MedicalRecordMigrator(index, studyName),
                new OncHistoryDetailsMigrator(index, studyName),
                new OncHistoryMigrator(index, studyName),
                new ParticipantDataMigrator(index, studyName),
                //AdditionalParticipantMigratorFactory.of(index, studyName),
                new ParticipantMigrator(index, studyName),
                new KitRequestShippingMigrator(index, studyName),
                new TissueMigrator(index, studyName),
                new SMIDMigrator(index, studyName),
                new CohortTagMigrator(index, studyName, new CohortTagDaoImpl()),
                new ClinicalOrderMigrator(index, studyName, new ClinicalOrderDao()),
                new SomaticResultMigrator(index, studyName));
        // TODO: A bit hacky but temporary -DC
        if (exportLogs == null) {
            exportables.forEach(BaseMigrator::export);
        } else {
            exportables.forEach(migrator -> migrator.export(exportLogs));
        }
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
        getMigrators(index, studyName).forEach(migrator -> migrator.exportParticipants(ddpParticipantIds, exportLogs));
        log.info("Finished migrating DSM data for {} participants to ES index {} for study {}",
                ddpParticipantIds.size(), index, studyName);
    }

    /**
     * Verify DSM ES data for a realm/index
     *
     * @param ddpParticipantIds list of participant IDs to verify for (if empty, verify all participants)
     * @param ptpToEsData ES DSM participant data by participant ID
     * @param verifyFields true if differences in record fields should be logged
     */
    public static List<VerificationLog> verifyParticipants(List<String> ddpParticipantIds,
                                                           Map<String, Map<String, Object>> ptpToEsData,
                                                           DDPInstance ddpInstance, boolean verifyFields) {
        String index = ddpInstance.getParticipantIndexES();
        log.info("Verifying DSM data for {} participants in ES index {}",
                ddpParticipantIds.isEmpty() ? "all" : ddpParticipantIds.size(), index);

        List<VerificationLog> verificationLogs = new ArrayList<>();
        getMigrators(index, ddpInstance.getName()).forEach(migrator ->
                migrator.verifyParticipants(ddpParticipantIds, ptpToEsData, verificationLogs, verifyFields));

        log.info("Finished verifying DSM data in ES index {}", index);
        return verificationLogs;
    }

    private static List<? extends BaseMigrator> getMigrators(String index, String realm) {
        return Arrays.asList(
            new MedicalRecordMigrator(index, realm),
            new OncHistoryDetailsMigrator(index, realm),
            new OncHistoryMigrator(index, realm),
            new ParticipantDataMigrator(index, realm),
            new ParticipantMigrator(index, realm),
            new KitRequestShippingMigrator(index, realm),
            new TissueMigrator(index, realm),
            new SMIDMigrator(index, realm),
            new CohortTagMigrator(index, realm, new CohortTagDaoImpl()),
            new ClinicalOrderMigrator(index, realm, new ClinicalOrderDao()),
            new SomaticResultMigrator(index, realm)
        );
    }
}
