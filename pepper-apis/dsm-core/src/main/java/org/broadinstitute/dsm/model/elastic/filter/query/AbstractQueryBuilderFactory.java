package org.broadinstitute.dsm.model.elastic.filter.query;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.broadinstitute.dsm.model.participant.Util;

@NoArgsConstructor
@AllArgsConstructor
@Setter
public class AbstractQueryBuilderFactory {

    private String alias;

    public BaseAbstractQueryBuilder create() {
        BaseAbstractQueryBuilder abstractQueryBuilder = new BaseAbstractQueryBuilder();
        if (Util.isUnderDsmKey(alias)) {
            abstractQueryBuilder = new DsmAbstractQueryBuilder();
        }
        return abstractQueryBuilder;
    }

}
