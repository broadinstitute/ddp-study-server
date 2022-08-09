package org.broadinstitute.dsm.db.dao.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.user.AssigneeDto;
import org.broadinstitute.dsm.db.jdbi.JdbiUserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssigneeDao implements Dao<AssigneeDto> {
    public static Logger logger = LoggerFactory.getLogger(AssigneeDao.class);

    public static HashMap<Long, AssigneeDto> getAssigneeMap(String realm) {
        HashMap<Long, AssigneeDto> assignees = new HashMap<>();
        TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            List<AssigneeDto> assigneeLists = handle.attach(JdbiUserRole.class).getAssigneesForStudy(realm);
            for (AssigneeDto assigneeDto : assigneeLists) {
                assignees.put(assigneeDto.getAssigneeId(), new AssigneeDto(assigneeDto.getAssigneeId(), assigneeDto.getName().orElse(""),
                        assigneeDto.getEmail().orElseThrow(), assigneeDto.getDSMLegacyId()));
            }
            return null;
        });

        logger.info("Found " + assignees.size() + " assignees ");
        return assignees;
    }

    public static Collection<AssigneeDto> getAssignees(String realm) {
        return AssigneeDao.getAssigneeMap(realm).values();
    }

    @Override
    public int create(AssigneeDto assigneeDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<AssigneeDto> get(long id) {
        return Optional.empty();
    }
}
