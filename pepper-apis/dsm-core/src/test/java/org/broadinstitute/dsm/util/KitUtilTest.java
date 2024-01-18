package org.broadinstitute.dsm.util;

import static org.mockito.Mockito.mock;

import java.util.List;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.KitRequestCreateLabel;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitUtilTest extends DbAndElasticBaseTest {

    private EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);
    private static String instanceName;
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;

    @BeforeClass
    public static void setup() throws Exception {
        instanceName = "kitutiltest";
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
    }

    @AfterClass
    public static void tearDown() {
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @Test
    public void testCreateLabel() {
        KitRequestCreateLabel kitRequestCreateLabel = KitRequestCreateLabel.builder()
                .build();
        KitUtil.createLabel(List.of(kitRequestCreateLabel), mockEasyPostUtil);
    }
}
