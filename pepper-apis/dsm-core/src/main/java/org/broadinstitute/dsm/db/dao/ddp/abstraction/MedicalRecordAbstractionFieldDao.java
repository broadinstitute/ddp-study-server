package org.broadinstitute.dsm.db.dao.ddp.abstraction;

import org.broadinstitute.dsm.db.dao.Dao;

import java.util.List;

public interface MedicalRecordAbstractionFieldDao<T> extends Dao<T> {
    List<MedicalRecordAbstractionFieldDto> getMedicalRecordAbstractionFieldsByInstanceName(String instanceName);

    String getPossibleValuesByDisplayNameAndType(String displayName, String type);
}
