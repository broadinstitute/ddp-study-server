package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class DynamicFieldsMappingMigratorTest {

    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
    }

    @Test
    public void testExport() {
        final String index = "participants_structured.cmi.cmi-osteo";
        final String study = "osteo";

        List<? extends Exportable> exportables = Arrays.asList(
                AdditionalParticipantMigratorFactory.of(index, study)
        );

//        List<? extends Exportable> exportables = Arrays.asList(
//                //DynamicFieldsMappingMigrator should be first in the list to make sure that mapping will be exported for first
//                new DynamicFieldsMappingMigrator(index, study),
//                new KitRequestShippingMigrator(index, study),
//                new ParticipantDataMigrator(index, study),
//                new ParticipantMigrator(index, study),
//                new OncHistoryMigrator(index, study),
//                AdditionalParticipantMigratorFactory.of(index, study),
//                new MedicalRecordMigrator(index, study),
//                new OncHistoryDetailsMigrator(index, study),
//                new TissueMigrator(index, study),
//                new SMIDMigrator(index, study),
//                new CohortTagMigrator(index, study, new CohortTagDaoImpl()));
        exportables.forEach(Exportable::export);

    }
}
