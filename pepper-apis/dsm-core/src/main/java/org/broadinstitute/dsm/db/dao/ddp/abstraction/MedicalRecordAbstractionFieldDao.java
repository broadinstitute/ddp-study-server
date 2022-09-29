
package org.broadinstitute.dsm.db.dao.ddp.abstraction;

import java.util.List;

import org.broadinstitute.dsm.db.dao.Dao;

/**
 * A Dao interface for operating with `ddp_medical_record_abstraction_field` table in DB
 */
public interface MedicalRecordAbstractionFieldDao<T> extends Dao<T> {

    /**
     * Returns the list of `MedicalRecordAbstractionFieldDto` objects filtered by study instance name
     * @param instanceName a study instance name
     */
    List<MedicalRecordAbstractionFieldDto> getMedicalRecordAbstractionFieldsByInstanceName(String instanceName);

    /**
     * Returns the possible values defined for each data type filtered by the combination of display name and type
     * @param displayName a display name for the concrete data type
     * @param type        a type definition for the concrete data type
     */
    String getPossibleValuesByDisplayNameAndType(String displayName, String type);

}
