package org.broadinstitute.dsm.db.dao.kit;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.Dao;

public interface KitDao extends Dao<KitRequestShipping> {

    Boolean isBloodKit(String kitLabel);

    Boolean hasTrackingScan(String kitLabel);

    Integer updateKitRequest(KitRequestShipping kitRequestShipping, String userId) throws KitException;

}
