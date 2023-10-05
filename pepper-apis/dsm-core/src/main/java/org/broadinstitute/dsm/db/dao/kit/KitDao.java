package org.broadinstitute.dsm.db.dao.kit;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.model.kit.ScanError;

public interface KitDao extends Dao<KitRequestShipping> {

    Boolean isBloodKit(String kitLabel);

    Boolean hasTrackingScan(String kitLabel);

    Optional<ScanError> updateKitRequest(KitRequestShipping kitRequestShipping, String userId);

    Optional<ScanError> updateKitReceived(KitRequestShipping kitRequestShipping, String userId);

    /**
     * Inserts a new ddp_kit using kit fields from the given {@link KitRequestShipping}
     */
    Long insertKit(KitRequestShipping kitRequestShipping);

    /**
     * Inserts a new ddp_kit_request and returns the generated primary key
     */
    Long insertKitRequest(KitRequestShipping kitRequestShipping);

    Optional<KitRequestShipping> getKitRequest(Long kitRequestId);

    Optional<KitRequestShipping> getKit(Long kitId);

    Integer deleteKitRequest(Long kitRequestId);

    Integer deleteKit(Long kitId);

    Optional<KitRequestShipping> getKitByDdpLabel(String ddpLabel, String kitLabel);

    Optional<List<KitRequestShipping>> getSubkitsByDdpLabel(String ddpLabel, String kitLabel);

    List<KitRequestShipping> getKitsByHruid(String hruid);

    Optional<ScanError> updateKitLabel(KitRequestShipping kitRequestShipping);

    /**
     * Inserts a new row in ddp_kit_tracking if there is no row for the given kitLabel
     */
    Optional<ScanError> insertKitTrackingIfNotExists(String kitLabel, String trackingReturnId,
                                                     int userId);
}
