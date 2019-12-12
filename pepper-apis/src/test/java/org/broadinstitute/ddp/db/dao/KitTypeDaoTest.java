package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.junit.Before;
import org.junit.Test;

public class KitTypeDaoTest extends TxnAwareBaseTest {
    private KitTypeDao dao;

    @Before
    public void setup() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(KitTypeDao.class);
            return dao;
        });
    }

    @Test
    public void findTestGetAllKitTypes() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(KitTypeDao.class);
            List<KitType> allKitTypes = dao.getAllKitTypes();
            assertNotNull(allKitTypes);
            assertFalse(allKitTypes.isEmpty());
            assertTrue(allKitTypes.size() > 1);
            allKitTypes.forEach(this::checkKitType);
            return null;
        });
    }

    @Test
    public void findBlookKit() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(KitTypeDao.class);
            KitType bloodKitType = dao.getBloodKitType();
            checkKitType(bloodKitType);
            assertEquals("BLOOD", bloodKitType.getName().toUpperCase());
            return null;
        });
    }

    @Test
    public void findSalivaKit() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(KitTypeDao.class);
            KitType salivaKitType = dao.getSalivaKitType();
            checkKitType(salivaKitType);
            assertEquals("SALIVA", salivaKitType.getName().toUpperCase());
            return null;
        });
    }

    private void checkKitType(KitType k) {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(KitTypeDao.class);

            assertTrue(StringUtils.isNotBlank(k.getName()));
            assertTrue(k.getId() > 0);
            return null;
        });
    }

}
