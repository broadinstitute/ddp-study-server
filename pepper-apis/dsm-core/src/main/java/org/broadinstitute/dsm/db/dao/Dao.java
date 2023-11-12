package org.broadinstitute.dsm.db.dao;

import java.util.Optional;

public interface Dao<T> {

    int create(T t);

    int delete(int id);

    //todo change get to work with int or delete to work with long, either way works but they should match
    Optional<T> get(long id);
}
