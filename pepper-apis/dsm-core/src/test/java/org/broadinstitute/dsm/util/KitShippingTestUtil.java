package org.broadinstitute.dsm.util;

import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;

@Slf4j
public class KitShippingTestUtil {
    private static final Map<String, List<Integer>> participantToKitType = new HashMap<>();
    private static final Map<String, List<Integer>> participantToKitRequest = new HashMap<>();
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
        participantToKitRequest.values().forEach(ids -> ids.forEach(kitRequestId -> {
            log.debug("Deleting kit request with id {}", kitRequestId);
            int deleteCount = kitDao.deleteKitRequestShipping(kitRequestId);
            if (deleteCount != 1) {
                throw new DsmInternalError("Failed to delete kit request with id " + kitRequestId);
            }
        }));
        participantToKitRequest.clear();

        participantToKitType.values().forEach(ids -> ids.forEach(kitTypeId -> {
            log.debug("Deleting kit type with id {}", kitTypeId);
            int deleteCount = kitTypeDao.delete(kitTypeId);
            if (deleteCount != 1) {
                throw new DsmInternalError("Failed to delete kit type with id " + kitTypeId);
            }
        }));
        participantToKitType.clear();
    }

    public int createTestKitShipping(ParticipantDto participant, DDPInstanceDto instanceDto) {
        KitTypeDto kitTypeDto = createKitType("SALIVA");
        return createTestKitShippingWithKitType(participant, instanceDto, kitTypeDto.getKitTypeName(), kitTypeDto.getKitTypeId(), true);
    }

    public int createTestKitShippingWithKitType(ParticipantDto participant, DDPInstanceDto instanceDto, String kitTypeName, int kitTypeId,
                                                boolean addKitType) {
        participantCounter++;
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();
        if (addKitType) {
            addKitType(ddpParticipantId, kitTypeId);
        }
        int dsmKitRequestId = createKitShipping(participant, instanceDto, kitTypeName, kitTypeId);
        addKitRequest(ddpParticipantId, dsmKitRequestId);
        log.info("Created kit request with id {} for ptp {}", dsmKitRequestId, ddpParticipantId);
        return dsmKitRequestId;
    }

    public List<Integer> getParticipantKitRequestIds(String ddpParticipantId) {
        List<Integer> ids = participantToKitRequest.get(ddpParticipantId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids;
    }

    /**
     * Creates a kit shipping request for a participant.
     * @return dsmKitRequestId
     */
    public int createKitShipping(ParticipantDto participant, DDPInstanceDto instanceDto, String kitTypeName, int kitTypeId) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(instanceDto.getInstanceName());
        String dsmKitRequestId = genDsmKitRequestId();
        String kitReqestIdStr = KitRequestShipping.writeRequest(instanceDto.getDdpInstanceId().toString(),
                dsmKitRequestId, kitTypeId, participant.getDdpParticipantIdOrThrow(),
                "test", "test", testUser, "test", "", "test",
                false, "test", ddpInstance, kitTypeName, dsmKitRequestId);
        return Integer.parseInt(kitReqestIdStr);
    }

    private String genDsmKitRequestId() {
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

    private void addKitType(String ddpParticipantId, int kitTypeId) {
        if (participantToKitType.containsKey(ddpParticipantId)) {
            participantToKitType.get(ddpParticipantId).add(kitTypeId);
            return;
        }
        participantToKitType.put(ddpParticipantId, new ArrayList<>(List.of(kitTypeId)));
    }

    private void addKitRequest(String ddpParticipantId, int kitRequestId) {
        if (participantToKitRequest.containsKey(ddpParticipantId)) {
            participantToKitRequest.get(ddpParticipantId).add(kitRequestId);
            return;
        }
        participantToKitRequest.put(ddpParticipantId, new ArrayList<>(List.of(kitRequestId)));
    }

    // TODO: review this since we may be able to get away with a fake role ID when testing -DC
    private int getRole() {
        if (roleNameToRoleId == null) {
            roleNameToRoleId = UserAdminTestUtil.getAllRoles();
        }
        return roleNameToRoleId.get(USER_ADMIN_ROLE);
    }
}
