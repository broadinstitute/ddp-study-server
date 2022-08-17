package org.broadinstitute.dsm.db.dao.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.route.kit.KitStatusChangeRoute;

public interface KitDao extends Dao<KitRequestShipping> {

    Boolean isBloodKit(String kitLabel);

    Boolean hasTrackingScan(String kitLabel);

    Optional<KitStatusChangeRoute.ScanError> updateKitRequest(KitRequestShipping kitRequestShipping, String userId);

    Optional<KitStatusChangeRoute.ScanError> insertKitTracking(KitRequestShipping kitRequestShipping, String userId);

    Optional<KitStatusChangeRoute.ScanError> updateKitReceived(KitRequestShipping kitRequestShipping, String userId);

    Integer insertKit(KitRequestShipping kitRequestShipping);

    Integer insertKitRequest(KitRequestShipping kitRequestShipping);

    Optional<KitRequestShipping> getKitRequest(Long kitRequestId);

    Optional<KitRequestShipping> getKit(Long kitId);

    Integer deleteKitRequest(Long kitRequestId);

    Integer deleteKit(Long kitId);

}
