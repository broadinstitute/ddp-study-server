package org.broadinstitute.dsm.util;

import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;

public class KitShippingTestUtil {
    private static final Map<String, Integer> participantToKitType = new HashMap<>();
    private static final Map<String, Integer> participantToKitRequest = new HashMap<>();
    private static final KitTypeDao kitTypeDao = new KitTypeImpl();
    private static final KitDao kitDao = new KitDao();
    private int participantCounter;
    private final String testUser;
    private final String baseName;
    private Map<String, Integer>  roleNameToRoleId = null;

    public KitShippingTestUtil(String testUser, String baseName) {
        this.testUser = testUser;
        this.baseName = baseName;
        this.participantCounter = 0;
    }

    public void tearDown() {
        participantToKitType.values().forEach(kitTypeDao::delete);
        participantToKitType.clear();
        participantToKitRequest.values().forEach(kitDao::deleteKitRequest);
        participantToKitRequest.clear();
    }

    public int createTestKitShipping(ParticipantDto participant, DDPInstanceDto instanceDto) {
        participantCounter++;
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();
        KitTypeDto kitTypeDto = createKitType("SALIVA");
        participantToKitType.put(ddpParticipantId, kitTypeDto.getKitTypeId());
        int kitRequestId = createKitShipping(participant, instanceDto, kitTypeDto);
        participantToKitRequest.put(ddpParticipantId, kitRequestId);
        return kitRequestId;
    }

    private int createKitShipping(ParticipantDto participant, DDPInstanceDto instanceDto, KitTypeDto kitTypeDto) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(instanceDto.getInstanceName());
        String kitReqestIdStr = KitRequestShipping.writeRequest(instanceDto.getDdpInstanceId().toString(),
                genDsmKitRequestId(), kitTypeDto.getKitTypeId(), participant.getDdpParticipantIdOrThrow(),
                "test", "test", testUser, "test", "", "test",
                false, "test", ddpInstance, kitTypeDto.getKitTypeName(), "test");
        return Integer.parseInt(kitReqestIdStr);
    }

    public String genDsmKitRequestId() {
        return String.format("%s_%d_%d_ABCDEFGHIJKLMNOP", baseName, participantCounter,
                Instant.now().toEpochMilli()).substring(0, 20);
    }

    private KitTypeDto createKitType(String kitTypeName) {
        KitTypeDto kitTypeDto = KitTypeDto.builder()
                .withRequiresInsertInKitTracking(false)
                .withManualSentTrack(false)
                .withNoReturn(false)
                .withKitTypeName(kitTypeName)
                .withRequiredRole(getRole())
                .build();
        int kitTypeId = kitTypeDao.create(kitTypeDto);
        kitTypeDto.setKitTypeId(kitTypeId);
        return kitTypeDto;
    }

    private int getRole() {
        if (roleNameToRoleId == null) {
            roleNameToRoleId = UserAdminTestUtil.getAllRoles();
        }
        return roleNameToRoleId.get(USER_ADMIN_ROLE);
    }
}
