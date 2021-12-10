package org.broadinstitute.dsm.db.dao;

import java.util.Optional;

public interface Dao<T> {

    int create(T t);

    int delete(int id);

    Optional<T> get(long id);
}
