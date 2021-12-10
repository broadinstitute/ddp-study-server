package org.broadinstitute.dsm.model.filter.tissue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.TissueListWrapper;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.PatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;

//Saved/Shared filtering
public class SavedFiltersTissueList extends BaseFilterTissueList {

    private static final Logger logger = LoggerFactory.getLogger(SavedFiltersTissueList.class);


    public SavedFiltersTissueList(String jsonMap) {
        super(jsonMap);
    }

    @Override
    public List<TissueListWrapper> filter(QueryParamsMap queryParamsMap) {
        prepareNeccesaryData(queryParamsMap);
        String filterName = Objects.requireNonNull(queryParamsMap).get(RequestParameter.FILTER_NAME).value();
        if (!queryParamsMap.hasKey(RoutePath.REALM)) throw new NoSuchElementException("realm is necessary");
        String realm = queryParamsMap.get(RoutePath.REALM).value();
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
        if (!queryParamsMap.hasKey(DBConstants.FILTER_PARENT)) throw new RuntimeException("parent is necessary");
        Filter[] filters = null;
        if (StringUtils.isBlank(filterName)) {
            filters = new Gson().fromJson(queryParamsMap.get(RequestParameter.FILTERS).value(), Filter[].class);
        }
        return filterTissueList(filters, PatchUtil.getColumnNameMap(), filterName == null ? quickFilterName : filterName, ddpInstance, filterQuery);

    }




}
