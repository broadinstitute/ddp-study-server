package org.broadinstitute.dsm.db.dao.kit;

import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.util.NotificationUtil;

public interface KitDao extends Dao<KitRequestShipping> {

    Boolean isBloodKit(String kitLabel);

    Boolean hasTrackingScan(String kitLabel);

    Optional<KitStatusChangeRoute.ScanError> updateKitRequest(KitRequestShipping kitRequestShipping, String userId);

    Optional<KitStatusChangeRoute.ScanError> insertKitTracking(KitRequestShipping kitRequestShipping, String userId);

    Optional<KitStatusChangeRoute.ScanError> updateKitReceived(KitRequestShipping kitRequestShipping, String userId);

    Integer insertKit(KitRequestShipping kitRequestShipping);

}
