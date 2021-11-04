package org.broadinstitute.dsm.model.filter.tissue;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.model.TissueListWrapper;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import spark.QueryParamsMap;

public class SearchBarFilterTissueList extends BaseFilterTissueList {

    public SearchBarFilterTissueList() {
        super(null);
    }

    @Override
    public List<TissueListWrapper> filter(QueryParamsMap queryParamsMap) {
        if (!Objects.requireNonNull(queryParamsMap).hasKey("filterQuery")) return Collections.emptyList();
        String filterQuery = queryParamsMap.get("filterQuery").value();
        filterQuery = " " + ViewFilter.changeFieldsInQuery(filterQuery, false);
        String realm = queryParamsMap.get(RoutePath.REALM).value();
        String parent = queryParamsMap.get(DBConstants.FILTER_PARENT).value();
        return getListBasedOnFilterName(null, realm, parent, filterQuery, null);
    }
}
