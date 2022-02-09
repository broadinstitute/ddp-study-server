package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.Test;

public class SnapshottedAddressServiceTest extends AddressServiceTest {

    @Test
    public void test_snapshotAddressOnSubmit() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class).insertInstance(
                    act.getDef().getActivityId(), userGuid);
            MailAddress defaultAddress = buildTestAddress();
            defaultAddress.setDefault(true);
            service.addAddress(handle, defaultAddress, userGuid, userGuid);
            int initialNumAddresses = service.findAllAddressesForParticipant(handle, userGuid).size();
            MailAddress snapshottedAddress = service.snapshotAddress(handle, userGuid, userGuid, instanceDto.getId());
            List<MailAddress> addresses = service.findAllAddressesForParticipant(handle, userGuid);
            assertEquals(initialNumAddresses + 1, addresses.size());
            MailAddress snapshottedAddress1 = addresses.stream()
                    .filter(m -> m.getGuid().equals(snapshottedAddress.getGuid())).findFirst().orElse(null);
            assertNotNull(snapshottedAddress1);
            assertFalse(snapshottedAddress1.isDefault());
            assertEquals(snapshottedAddress1.getGuid(), snapshottedAddress.getGuid());
            Map<String, String> subs = handle.attach(ActivityInstanceDao.class).findSubstitutions(instanceDto.getId());
            String addresssGuid = subs.get(I18nTemplateConstants.Snapshot.ADDRESS_GUID);
            assertEquals(snapshottedAddress.getGuid(), addresssGuid);
            handle.rollback();
        });
    }
}
