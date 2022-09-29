
package org.broadinstitute.dsm.db.dao.abstraction;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.broadinstitute.dsm.db.dao.Dao;

/**
 * A Dao interface for operating with `ddp_medical_record_final` table in DB.
 */
public interface MedicalRecordFinalDao extends Dao<MedicalRecordFinalDto> {

    /**
     * Reads all instances of MedicalRecordFinalDto filtered by study instance name
     * @param instanceName a study instance name
     */
    Map<String, List<MedicalRecordFinalDto>> readAllByInstanceName(String instanceName);
}
