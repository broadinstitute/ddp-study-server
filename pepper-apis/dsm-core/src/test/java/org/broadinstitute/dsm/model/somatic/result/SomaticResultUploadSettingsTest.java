package org.broadinstitute.dsm.model.somatic.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.util.ConfigManager;
import org.junit.BeforeClass;
import org.junit.Test;

public class SomaticResultUploadSettingsTest {
    private static SomaticResultUploadSettings defaultSettings;
    private static SomaticResultUploadSettings loadedConfigSettings;

    @BeforeClass
    public static void setup() {
        defaultSettings = new SomaticResultUploadSettings(null);
        Config loadedConfig = ConfigManager.getInstance().getConfig();
        loadedConfigSettings = new SomaticResultUploadSettings(loadedConfig);
    }


    @Test
    public void test_defaultSettingsObject() {
        validateSettings(defaultSettings,
                1000 * 1000 * 30L,
                1,
                "application/pdf",
                "pdf",
                1
        );
    }

    @Test
    public void test_defaultLoadedSettingsObject() {
        validateSettings(loadedConfigSettings,
                1000 * 1000 * 30L,
                2,
                "application/pdf",
                "pdf",
                1
        );
    }

    @Test
    public void test_modifiedConfigSettingsObject() {
        ConfigManager loadedConfig = ConfigManager.getInstance();
        loadedConfig.overrideValue("somatic.maxFileSize", "60");
        loadedConfig.overrideValue("somatic.mediaTypes", "application/foo,application/json,application/pdf");
        loadedConfig.overrideValue("somatic.allowedFileExtensions", "foo,json,pdf");
        Config modifiedConfig = loadedConfig.getConfig();
        SomaticResultUploadSettings uploadSettings = new SomaticResultUploadSettings(modifiedConfig);
        validateSettings(uploadSettings,
                60L,
                3,
                "application/foo",
                "foo",
                3
        );
    }

    private void validateSettings(SomaticResultUploadSettings settings,
                                  long expectedSize,
                                  int expectedMimeTypeCount,
                                  String expectedMimeType,
                                  String expectedExtension,
                                  int expectedExtensionCount) {
        assertEquals(expectedSize, settings.getMaxFileSize());
        assertEquals(expectedMimeTypeCount, settings.getMimeTypes().size());
        assertTrue(settings.getMimeTypes().contains(expectedMimeType));
        assertTrue(settings.getAllowedFileExtensions().contains(expectedExtension));
        assertEquals(expectedExtensionCount, settings.getAllowedFileExtensions().size());
    }

}
