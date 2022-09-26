
package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDao;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDto;
import org.junit.Assert;
import org.junit.Test;

public class MedicalRecordFinalMappingMigratorTest {

    @Test
    public void buildMapping() {
        var index    = "participants_structured.cmi.cmi-brain";
        var study    = "brain";
        var migrator = new MedicalRecordFinalMappingMigrator(index, study);
        migrator.setMedicalRecordAbstractionFieldDao(new MedicalRecordAbstractionFieldDaoMock());
        migrator.processAndBuildMapping();
        System.out.println(migrator.propertyMap);

        Assert.assertTrue(true);
    }


    private static class MedicalRecordAbstractionFieldDaoMock
            implements MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> {

        @Override
        public int create(MedicalRecordAbstractionFieldDto medicalRecordAbstractionFieldDto) {
            return 0;
        }

        @Override
        public int delete(int id) {
            return 0;
        }

        @Override
        public Optional<MedicalRecordAbstractionFieldDto> get(long id) {
            return Optional.empty();
        }

        @Override
        public List<MedicalRecordAbstractionFieldDto> getMedicalRecordAbstractionFieldsByInstanceName(String instanceName) {
            return List.of(
                    new MedicalRecordAbstractionFieldDto(
                            1L, "Age", "number", null, null, null,
                            true, 1L, "18", 1, 0),
                    new MedicalRecordAbstractionFieldDto(
                            2L, "Percent Core", "number", null, null, null,
                            true, 1L, "18", 2, 0),
                    new MedicalRecordAbstractionFieldDto(
                            3L, "Date Of Birth", "date", null, null, "help txt DOB 1",
                            true, 1L, "18", 1, 0),
                    new MedicalRecordAbstractionFieldDto(
                            4L, "Date Of Birth", "date", null, null, "help txt DOB 2",
                            true, 1L, "18", 2, 0),
                    new MedicalRecordAbstractionFieldDto(
                            5L, "DX Date", "date", null, null, "help txt DX Date",
                            true, 1L, "18", 4, 0),
                    new MedicalRecordAbstractionFieldDto(
                            6L, "Prostatectomy M", "button_select", null,
                            "[{\"value\":\"0\"},{\"value\":\"1\"},{\"value\":\"X\"},{\"value\":\"Other\"},"
                                    + "{\"value\":\"1a\"},{\"value\":\"1b\"},{\"value\":\"1c\"}]", null,
                            false, 1L, "18", 6, 0),
                    new MedicalRecordAbstractionFieldDto(
                            7L, "Met sites at Mets dx", "multi_options", null,
                            "[{\"value\":\"No Mets\"},{\"value\":\" Bone\"},{\"value\":\"Liver\"},{\"value\":\"Lung\"},"
                                    + "{\"value\":\"Bladder\"},{\"value\":\"Lymph Nodes\"}]",
                            "Sites of Metastases at Initial Mets Diagnosis", false, 1L, "18", 14, 0),
                    new MedicalRecordAbstractionFieldDto(
                            8L, "TNM", "multi_type_array", null,
                            "[{\"value\":\"PSA\",\"type\":\"number\"},{\"value\":\"Date of PSA\",\"type\":\"date\"}]",
                            null, false, 1L, "18", 1, 0),
                    new MedicalRecordAbstractionFieldDto(
                            9L, "TNM", "multi_type_array", null,
                            "[{\"value\":\"PSA\",\"type\":\"number\"},{\"value\":\"Date of PSA\",\"type\":\"date\"}]",
                            null, false, 1L, "18", 1, 0));

        }
    }
}
