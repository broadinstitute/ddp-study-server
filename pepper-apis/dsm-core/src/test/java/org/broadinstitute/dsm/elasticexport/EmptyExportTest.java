package org.broadinstitute.dsm.elasticexport;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.export.ExportToES;
import org.broadinstitute.dsm.pubsub.DSMtasksSubscription;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class EmptyExportTest extends DbAndElasticBaseTest {

    private static final String instanceName = "empty_instance";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static DDPInstanceDto ddpInstanceDto;
    private static final DdpInstanceGroupTestUtil ddpInstanceGroupTestUtil = new DdpInstanceGroupTestUtil();
    private static final String groupName = "delete_group";
    private static String esIndex;


    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(instanceName).orElseThrow();

    }

    @AfterClass
    public static void cleanUpAfter() {
        try {
            ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
        ElasticTestUtil.deleteIndex(esIndex);
    }

    //This is to make sure not having participants or objects in a study will not break export, since there are some @NonNull and not empty
    // checks in the export code this makes sure there is no NPE
    @Test
    public void emptyExport() {
        try {
            ExportToES.ExportPayload exportPayload = new ExportToES.ExportPayload();
            exportPayload.setStudy(instanceName);
            exportPayload.setIsMigration(true);
            DSMtasksSubscription.migrateToES(exportPayload);
        } catch (Exception e) {
            Assert.fail("Some error happened during empty export");
            e.printStackTrace();
        }
    }
}
