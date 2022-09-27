package org.broadinstitute.dsm.db.dao.abstraction;

import java.util.Optional;

import org.broadinstitute.dsm.db.MedicalRecordFinalDto;

public class MedicalRecordFinalDaoLive implements MedicalRecordFinalDao {

    @Override
    public int create(MedicalRecordFinalDto medicalRecordFinalDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<MedicalRecordFinalDto> get(long id) {
        return Optional.empty();
    }

}
