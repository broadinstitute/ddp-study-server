package org.broadinstitute.dsm.db.dao.kit;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.model.kit.ScanResult;

// todo arz remove useless interface
public interface KitDao extends Dao<KitRequestShipping> {

    Boolean isBloodKit(String kitLabel);

    Boolean hasTrackingScan(String kitLabel);

    Optional<ScanResult> updateKitScanInfo(KitRequestShipping kitRequestShipping, String userId);

    Optional<ScanResult> updateKitReceived(KitRequestShipping kitRequestShipping, String userId);

    /**
     * Inserts a new ddp_kit using kit fields from the given {@link KitRequestShipping}
     */
    Integer insertKit(KitRequestShipping kitRequestShipping);

    /**
     * Inserts a new ddp_kit_request and returns the generated primary key
     */
    Integer insertKitRequest(KitRequestShipping kitRequestShipping);

    Optional<KitRequestShipping> getKitRequest(Long kitRequestId);

    Optional<KitRequestShipping> getKit(Long kitId);

    Integer deleteKitRequest(Integer kitRequestId);

    Integer deleteKit(Integer kitId);

    Optional<KitRequestShipping> getKitByDdpLabel(String ddpLabel, String kitLabel);

    Optional<List<KitRequestShipping>> getSubkitsByDdpLabel(String ddpLabel, String kitLabel);

    List<KitRequestShipping> getKitsByHruid(String hruid);

    Optional<ScanResult> updateKitLabel(KitRequestShipping kitRequestShipping);

    /**
     * Inserts a new row in ddp_kit_tracking if there is no row for the given kitLabel
     */
    Optional<ScanResult> insertKitTrackingIfNotExists(String kitLabel, String trackingReturnId,
                                                      int userId);
}
