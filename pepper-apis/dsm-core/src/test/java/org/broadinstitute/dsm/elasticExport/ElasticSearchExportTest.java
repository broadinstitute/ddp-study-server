package org.broadinstitute.dsm.elasticExport;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.export.ExportToES;
import org.broadinstitute.dsm.pubsub.DSMtasksSubscription;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ElasticSearchExportTest {
    @BeforeClass
    public static void first(){
        TestHelper.setupDB();
    }

    @Test
    public void dsmeExportParticipantListIsMigrationTrueSpecificStudy(){
        String STUDY = "atcp";
        final boolean IS_MIGRATION = true;
        ExportToES.ExportPayload exportPayload = new ExportToES.ExportPayload();
        exportPayload.setStudy(STUDY);
        exportPayload.setIsMigration(IS_MIGRATION);
        try {
            DSMtasksSubscription.migrateToES(exportPayload);
        } catch (Exception e) {
            log.error(STUDY +" had the following error while export: ");
            e.printStackTrace();
            Assert.fail();
        }
    }

    // When making changes to the export code, run this to make sure it has not broken another study
    @Test
    public void dsmExportParticipantListIsMigrationTrueAllStuides(){
        String[] studies = new String[]{"atcp", "pancan", "brain", "osteo", "osteo2", "prostate", "gec", "angio", "Pepper-MBC", "brugada",
                 "rgp", "testboston", "cmi-lms"};
        final boolean IS_MIGRATION = true;
        boolean error = false;
        ExportToES.ExportPayload exportPayload = new ExportToES.ExportPayload();
        for (String study: studies) {
            exportPayload.setStudy(study);
            exportPayload.setIsMigration(IS_MIGRATION);
            try {
                DSMtasksSubscription.migrateToES(exportPayload);
            } catch (Exception e) {
                error = true;
                log.error(study + " had the following error while export: ");
                e.printStackTrace();
            }
            log.info("----------------DSM export finished for "+ study+" ---------------------");
        }
        Assert.assertFalse(error);
    }
}
