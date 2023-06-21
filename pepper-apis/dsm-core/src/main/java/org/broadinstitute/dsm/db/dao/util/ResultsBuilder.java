package org.broadinstitute.dsm.db.dao.util;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface ResultsBuilder {

    Object build(ResultSet rs) throws SQLException;
}
