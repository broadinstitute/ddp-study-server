package org.broadinstitute.ddp.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class PopulateHruidsScript extends TxnAwareBaseTest {

    @Test
    public void populateUsersWithoutHruids() {
        TransactionWrapper.useTxn(handle -> {
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            List<Long> userIdsWithoutHruids = jdbiUser.getUserIdsForUsersWithoutHruids();

            for (Long userId : userIdsWithoutHruids) {
                String hruid = DBUtils.uniqueUserHruid(handle);
                assertEquals(1, jdbiUser.updateUserHruid(userId, hruid));
            }

            userIdsWithoutHruids = jdbiUser.getUserIdsForUsersWithoutHruids();
            assertTrue(userIdsWithoutHruids.isEmpty());
        });
    }

}
