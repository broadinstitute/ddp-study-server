package org.broadinstitute.dsm.util;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;

/**
 * For initializing DB test content shared among tests
 */
@Slf4j
public class DbSharedTestContent {

    private static boolean initialized = false;
    private static final String OSTEO2_INSTANCE_NAME = "osteo2";

    public static synchronized void createContent() {
        if (initialized) {
            log.info("Already initialized DbSharedTestContent");
            return;
        }

        createFieldSettings();
        log.info("DbSharedTestContent initialized");
        initialized = true;
    }

    private static void createFieldSettings() {
        DDPInstanceDao instanceDao = DDPInstanceDao.of();
        int ddpInstanceId = instanceDao.getDDPInstanceIdByInstanceName(OSTEO2_INSTANCE_NAME);
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(ddpInstanceId);
        FieldSettingsDto fieldSettings = builder.withFieldType("oD")
                .withColumnName("LOCAL_CONTROL")
                .withDisplayType("OPTIONS")
                .withColumnDisplay("Is the sample from local control?")
                .withPossibleValues("[{\"value\":\"Yes\"},{\"value\":\"No\"},{\"value\":\"Unknown\"}]").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("FFPE")
                .withDisplayType("OPTIONS")
                .withColumnDisplay(" Is the sample FFPE?")
                .withPossibleValues("[{\"value\":\"Yes\"},{\"value\":\"No\"},{\"value\":\"Unknown\"}]").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("DECALCIFICATION")
                .withDisplayType("OPTIONS")
                .withColumnDisplay("Method of decalcification")
                .withPossibleValues("[{\"value\":\"Nitric Acid (includes Perenyi's fluid)\"},"
                        + "{\"value\":\"Hydrochloric Acid (includes Von Ebner's solution)\"},"
                        + "{\"value\":\"Formic Acid (includes Evans/Kajian, Kristensen/Gooding/Stewart)\"},"
                        + "{\"value\":\"Acid NOS\"},{\"value\":\"EDTA\"},{\"value\":\"Sample not decalcified\"},"
                        + "{\"value\":\"Other\"},{\"value\":\"Unknown\"},{\"value\":\"Immunocal/ Soft Decal\"}]").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("BLOCKS_WITH_TUMOR")
                .withDisplayType("TEXT")
                .withColumnDisplay("Blocks with Tumor").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("TUMOR_SIZE")
                .withDisplayType("TEXTAREA")
                .withColumnDisplay("Tumor Size").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("NECROSIS")
                .withDisplayType("TEXT")
                .withColumnDisplay("% Necrosis").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("VIABLE_TUMOR")
                .withDisplayType("TEXT")
                .withColumnDisplay("% Viable Tumor").build();
        fieldSettingsDao.create(fieldSettings);

        fieldSettings = builder.withFieldType("oD")
                .withColumnName("BLOCK_TO_REQUEST")
                .withDisplayType("TEXT")
                .withColumnDisplay("BLOCK_TO_REQUEST").build();
        fieldSettingsDao.create(fieldSettings);
    }
}
