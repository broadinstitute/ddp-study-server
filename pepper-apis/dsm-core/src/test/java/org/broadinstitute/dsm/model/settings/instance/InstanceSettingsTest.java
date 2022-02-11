package org.broadinstitute.dsm.model.settings.instance;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.model.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class InstanceSettingsTest {

    private static InstanceSettings instanceSettings;

    @BeforeClass
    public static void before() {
        instanceSettings = new InstanceSettings();
    }

    @Test
    public void testGetInstanceSettingsAsMap() {
        InstanceSettingsDto instanceSettingsDto = new InstanceSettingsDto.Builder()
                .withDdpInstanceId(10000)
                .withHideSamplesTab(true)
                .withDefaultColumns(Collections.singletonList(new Value("value")))
                .build();
        Map<String, Object> instanceSettingsAsMap = instanceSettings.getInstanceSettingsAsMap(instanceSettingsDto);
        Assert.assertEquals(instanceSettingsAsMap.get("ddpInstanceId"), 10000);
        Assert.assertTrue((Boolean) instanceSettingsAsMap.get("hideSamplesTab"));
        Assert.assertTrue(((List)instanceSettingsAsMap.get("defaultColumns")).size() > 0);
    }

}