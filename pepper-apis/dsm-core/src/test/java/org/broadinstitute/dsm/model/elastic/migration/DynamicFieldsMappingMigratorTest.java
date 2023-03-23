package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.statics.DBConstants.OSTEO_INDEX;
import static org.junit.Assert.assertTrue;


public class DynamicFieldsMappingMigratorTest {

    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
    }

    @Test
    @Ignore
    public void testOsteoCohortTagMigrator() {
        final String index = OSTEO_INDEX;
        final String study = OLD_OSTEO_INSTANCE_NAME;

        CohortTagMigrator tagMigrator = new CohortTagMigrator(index, study, new CohortTagDaoImpl());
        Map value = tagMigrator.getDataByRealm();
        assertTrue(value.keySet().size() > 0);
    }

    @Test
    @Ignore
    public void testNewOsteoCohortTagMigrator() {
        final String index = OSTEO_INDEX;
        final String study = NEW_OSTEO_INSTANCE_NAME;

        CohortTagMigrator tagMigrator = new CohortTagMigrator(index, study, new CohortTagDaoImpl());
        Map value = tagMigrator.getDataByRealm();
        assertTrue(value.keySet().size() > 0);
    }

    @Test
    @Ignore
    public void testNonOsteoCohortTagMigrator() {
        final String index = "participants_structured.atcp.atcp";
        final String study = "atcp";

        CohortTagMigrator tagMigrator = new CohortTagMigrator(index, study, new CohortTagDaoImpl());
        Map value = tagMigrator.getDataByRealm();
        assertTrue(value.keySet().size() > 0);
    }
    @Test
    @Ignore
    public void testExport() {

        final String index = "participants_structured.cmi.cmi-brain";
        final String study = "brain";

        List<? extends Exportable> exportables = Arrays.asList(
                //DynamicFieldsMappingMigrator should be first in the list to make sure that mapping will be exported for first
                new DynamicFieldsMappingMigrator(index, study),
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
