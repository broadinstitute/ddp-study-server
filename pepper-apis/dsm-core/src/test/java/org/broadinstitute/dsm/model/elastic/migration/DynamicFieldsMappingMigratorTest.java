package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dao.abstraction.MedicalRecordFinalDaoLive;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


public class DynamicFieldsMappingMigratorTest {

    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
    }

    @Test
    @Ignore
    public void testExport() {

        final String index = "participants_structured.cmi.cmi-mpc";
        final String study = "Prostate";

        List<? extends Exportable> exportables = Arrays.asList(
                // DynamicFieldsMappingMigrator should be first in the list to make sure that mapping will be exported for first
                new DynamicFieldsMappingMigrator(index, study),
                new MedicalRecordFinalMappingMigrator(index, study),
                new MedicalRecordFinalMigrator(index, study, new MedicalRecordFinalDaoLive()),
                new KitRequestShippingMigrator(index, study),
                new ParticipantDataMigrator(index, study),
                new ParticipantMigrator(index, study),
                new OncHistoryMigrator(index, study),
                AdditionalParticipantMigratorFactory.of(index, study),
                new MedicalRecordMigrator(index, study),
                new OncHistoryDetailsMigrator(index, study),
                new TissueMigrator(index, study),
                new SMIDMigrator(index, study),
                new CohortTagMigrator(index, study, new CohortTagDaoImpl()),
                new ClinicalOrderMigrator(index, study, new ClinicalOrderDao()));

        exportables.forEach(Exportable::export);

    }
}
