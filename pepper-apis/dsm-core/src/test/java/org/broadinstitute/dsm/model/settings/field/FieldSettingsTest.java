package org.broadinstitute.dsm.model.settings.field;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FieldSettingsTest {

    private static final String acceptanceStatusPossibleValue = "[{\"value\":\"ACCEPTED\",\"name\":\"Accepted\",default:true},{\"value\":\"IN_REVIEW\",\"name\":\"In Review\"},{\"name\":\"More Info Needed\",\"value\":\"MORE_INFO_NEEDED\"},{\"name\":\"Not Accepted\",\"value\":\"NOT_ACCEPTED\"},{\"name\":\"Waitlist\",\"value\":\"WAITLIST\"},{\"name\":\"Pre-review\",\"value\":\"PRE_REVIEW\"}]";
    private static final String activePossibleValue = "[{\"value\":\"ACTIVE\",\"name\":\"Active\"},{\"value\":\"HOLD\",\"name\":\"HOLD\"},{\"value\":\"INACTIVE\",\"name\":\"Inactive\"}]";
    private static final String ethnicityPossibleValue = "[{\"name\":\"Hispanic\",\"value\":\"HISPANIC\",default:true},{\"name\":\"Not Hispanic\",\"value\":\"NOT_HISPANIC\"},{\"name\":\"Unknown or Not Reported\",\"value\":\"UNKNOWN\"}]";
    private static final String actions = "[{\"name\":\"REGISTRATION_STATUS\",\"type\":\"ELASTIC_EXPORT.workflows\"}]";
    private static final String acceptanceStatusColumnName = "ACCEPTANCE_STATUS";
    private static final String activeColumnName = "ACTIVE";
    private static final String ethnicityColumnName = "ETHNICITY";
    public static final String REGISTRATION_STATUS = "REGISTRATION_STATUS";

    private static FieldSettings fieldSettings;
    private static int instanceId;
    private static int fieldSettingsId;
    private static DDPInstanceDao ddpInstanceDao;

    @BeforeClass
    public static void first() {
        TestHelper.setupDB();
        fieldSettings = new FieldSettings();
        ddpInstanceDao = new DDPInstanceDao();
    }

    @AfterClass
    public static void tearDown() {
        if (instanceId > -1) ddpInstanceDao.delete(instanceId);
        if (fieldSettingsId > -1) FieldSettingsDao.of().delete(fieldSettingsId);
    }

    @Test
    public void testGetDefaultOption() {
        String defaultOption = fieldSettings.getDefaultValue(acceptanceStatusPossibleValue);
        Assert.assertEquals("ACCEPTED", defaultOption);
    }

    @Test
    public void testIsDefaultOption() {
        boolean isDefaultOption = fieldSettings.isDefaultValue(acceptanceStatusPossibleValue);
        Assert.assertTrue(isDefaultOption);
    }

    @Test
    public void testGetDefaultOptions() {
        Map<String, String> defaultOptions = fieldSettings.getColumnsWithDefaultValues(createStaticFieldSettingDtoList());
        Assert.assertEquals("ACCEPTED", defaultOptions.get(acceptanceStatusColumnName));
        Assert.assertEquals("HISPANIC", defaultOptions.get(ethnicityColumnName));
        Assert.assertNull(defaultOptions.get(activeColumnName));
    }

    @Test
    public void testIsElasticExportWorkflowType() {
        boolean isElasticExportWorkflowType = fieldSettings.isElasticExportWorkflowType(actions);
        Assert.assertTrue(fieldSettings.isElasticExportWorkflowType(new FieldSettingsDto.Builder(0).withActions(actions).build()));
        Assert.assertTrue(isElasticExportWorkflowType);
    }

    @Test
    public void testGetColumnsWithDefaultOptionsFilteredByElasticExportWorkflow() {
        List<FieldSettingsDto> staticFieldSettingDtoList = createStaticFieldSettingDtoList();
        Map<String, String> columnsWithDefaultOptionsFilteredByElasticExportWorkflow =
                fieldSettings.getColumnsWithDefaultOptionsFilteredByElasticExportWorkflow(staticFieldSettingDtoList);
        Assert.assertNotNull(columnsWithDefaultOptionsFilteredByElasticExportWorkflow.get(acceptanceStatusColumnName));
    }

    @Test
    public void isColumnExportable() {
        instanceId = ddpInstanceDao.create(new DDPInstanceDto.Builder().build());
        FieldSettingsDto fieldSettingsDto = new FieldSettingsDto.Builder(instanceId)
                .withActions(actions)
                .withColumnName(REGISTRATION_STATUS)
                .build();
        fieldSettingsId = FieldSettingsDao.of().create(fieldSettingsDto);
        Assert.assertTrue(fieldSettings.isColumnExportable(instanceId, REGISTRATION_STATUS));
    }

    List<FieldSettingsDto> createStaticFieldSettingDtoList() {
        FieldSettingsDto fieldSettingsDto1 = new FieldSettingsDto.Builder(9999).build();
        fieldSettingsDto1.setColumnName(acceptanceStatusColumnName);
        fieldSettingsDto1.setPossibleValues(acceptanceStatusPossibleValue);
        fieldSettingsDto1.setActions(actions);
        FieldSettingsDto fieldSettingsDto2 = new FieldSettingsDto.Builder(10000).build();
        fieldSettingsDto2.setColumnName(activeColumnName);
        fieldSettingsDto2.setPossibleValues(activePossibleValue);
        FieldSettingsDto fieldSettingsDto3 = new FieldSettingsDto.Builder(10001).build();
        fieldSettingsDto3.setColumnName(ethnicityColumnName);
        fieldSettingsDto3.setPossibleValues(ethnicityPossibleValue);

        return Arrays.asList(fieldSettingsDto1, fieldSettingsDto2, fieldSettingsDto3);
    }
}