
package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.abstraction.MedicalRecordFinalDao;
import org.broadinstitute.dsm.model.elastic.MedicalRecordFinalObjectTransformer;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class MedicalRecordFinalMigrator extends BaseCollectionMigrator {

    private final MedicalRecordFinalDao medicalRecordFinalDao;    
    
    public MedicalRecordFinalMigrator(String index, String realm, MedicalRecordFinalDao medicalRecordFinalDao) {
        super(index, realm, ESObjectConstants.MEDICAL_RECORD_FINAL);
        this.medicalRecordFinalDao = medicalRecordFinalDao;
        this.objectTransformer = new MedicalRecordFinalObjectTransformer(realm);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return (Map) medicalRecordFinalDao.readAllByInstanceName(realm);
    }

}

