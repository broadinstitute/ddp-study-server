package org.broadinstitute.dsm.db.dao.abstraction;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.broadinstitute.dsm.db.dao.Dao;

public interface MedicalRecordFinalDao extends Dao<MedicalRecordFinalDto> {

    Map<String, List<MedicalRecordFinalDto>> readAllByInstanceName(String instanceName);
}
