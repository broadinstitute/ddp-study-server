package org.broadinstitute.dsm.db.dao.roles;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.jdbi.JdbiRole;
import org.broadinstitute.dsm.db.jdbi.RoleDto;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleDao implements Dao<RoleDto> {
    Logger logger = LoggerFactory.getLogger(RoleDao.class);

    public List<RoleDto> getAllRolesForStudy(String studyGuid) {
        List<RoleDto> roles;
        SimpleResult result = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiRole.class).getAllRolesForStudy(studyGuid);
            return dbVals;
        });
        roles = (List<RoleDto>) result.resultValue;
        logger.info(String.format("Got list of roles for study %s with size %d",
                studyGuid, roles.size()));
        return roles;
    }

    @Override
    public int create(RoleDto roleDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<RoleDto> get(long id) {
        return Optional.empty();
    }
}
