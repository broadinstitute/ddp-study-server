package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.ExpressionTable;

import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiExpression extends SqlObject {

    default String generateUniqueGuid() {
        return DBUtils.uniqueStandardGuid(getHandle(), ExpressionTable.TABLE_NAME, ExpressionTable.GUID);
    }

    @SqlQuery("select expression_text from expression where expression_id = :id")
    String getExpressionById(@Bind("id") Long id);

    @SqlUpdate("insert into expression(expression_guid, expression_text) values (:expressionGuid,:expressionText)")
    @GetGeneratedKeys
    long insert(@Bind("expressionGuid") String expressionGuid, @Bind("expressionText") String expressionText);

    default Expression insertExpression(String text) {
        String guid = generateUniqueGuid();
        return new Expression(insert(guid, text), guid, text);
    }

    @SqlUpdate("update expression set expression_text = :text where expression_guid = :guid")
    int updateByGuid(@Bind("guid") String guid, @Bind("text") String text);

    @SqlUpdate("update expression set expression_text = :text where expression_id = :expressionId")
    int updateById(@Bind("expressionId") long expressionId, @Bind("text") String text);

    @SqlUpdate("delete from expression where expression_id = :expressionId")
    int deleteById(long expressionId);

    @SqlQuery("select * from expression where expression_id = :expressionId")
    @RegisterConstructorMapper(Expression.class)
    Optional<Expression> getById(long expressionId);

    @SqlQuery("select * from expression where expression_guid = :expressionGuid")
    @RegisterConstructorMapper(Expression.class)
    Optional<Expression> getByGuid(@Bind("expressionGuid") String expressionGuid);
}
