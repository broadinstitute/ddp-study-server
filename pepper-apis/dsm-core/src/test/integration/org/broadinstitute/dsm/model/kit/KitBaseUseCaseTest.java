package org.broadinstitute.dsm.model.kit;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.TestInstanceCreator;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dao.kit.KitTypeDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeImpl;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class KitBaseUseCaseTest {
    protected static final KitDao kitDao = new KitDaoImpl();
    protected static final KitTypeDao kitTypeDao = new KitTypeImpl();
    protected static final List<Integer> kitRequestIds = new ArrayList<>();
    protected static final List<Integer> kitIds = new ArrayList<>();
    protected static final List<Integer> kitTypeIds = new ArrayList<>();
    protected static final TestInstanceCreator testInstance = new TestInstanceCreator();

    protected static int USER_ID = 94;

    @BeforeClass
    public static void setUp() {
        testInstance.create();
    }

    @AfterClass
    public static void finish() {
        testInstance.delete();
    }

    @After
    public void afterEach() {
        for (Integer id : kitIds) {
            kitDao.deleteKit(id.longValue());
        }
        for (Integer id : kitRequestIds) {
            kitDao.deleteKitRequest(id.longValue());
        }
        for (Integer id : kitTypeIds) {
            kitTypeDao.delete(id);
        }
    }

    protected void setAndSaveKitRequestId(KitRequestShipping... kitRequestShippings) {
        for (KitRequestShipping kitRequestShipping: kitRequestShippings) {
            Integer insertedKitRequestId = kitDao.insertKitRequest(kitRequestShipping);
            kitRequestIds.add(insertedKitRequestId);
            kitRequestShipping.setDsmKitRequestId(insertedKitRequestId.longValue());
        }
    }

    protected KitRequestShipping getKitRequestShipping(String ddpLabel, String ddpKitRequestId) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setDdpLabel(ddpLabel);
        kitRequestShipping.setDdpKitRequestId(ddpKitRequestId);
        kitRequestShipping.setKitTypeId("1");
        kitRequestShipping.setDdpInstanceId(testInstance.getDdpInstanceId().longValue());
        kitRequestShipping.setCreatedDate(System.currentTimeMillis());
        return kitRequestShipping;
    }
}
