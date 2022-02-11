package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.TestHelper;
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
    @Ignore
    public void testExport() {
        final String index = "participants_structured.cmi.angio";
        final String study = "angio";
        List<? extends Exportable> exportables = Arrays.asList(
                //DynamicFieldsMappingMigrator should be first in the list to make sure that mapping will be exported for first
                new DynamicFieldsMappingMigrator(index, study),
                new ParticipantDataMigrator(index, study),
                new ParticipantMigrator(index, study),
                new OncHistoryMigrator(index, study),
                new MedicalRecordMigrator(index, study),
                new OncHistoryDetailsMigrator(index, study),
                new KitRequestShippingMigrator(index, study),
                new TissueMigrator(index, study));
        exportables.forEach(Exportable::export);

    }
}