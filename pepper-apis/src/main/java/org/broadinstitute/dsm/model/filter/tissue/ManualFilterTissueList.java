package org.broadinstitute.dsm.model.filter.tissue;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.model.TissueList;
import org.broadinstitute.dsm.model.TissueListWrapper;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.PatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;

//reloadWithDefault/Manual/Empty filtering
public class ManualFilterTissueList extends BaseFilterTissueList {

    private static final Logger logger = LoggerFactory.getLogger(ManualFilterTissueList.class);

    public ManualFilterTissueList(String jsonBody) {
        super(jsonBody);
    }

    @Override
    public List<TissueListWrapper> filter(QueryParamsMap queryParamsMap) {
        List<TissueListWrapper> wrapperList = new ArrayList<>();
        if (Objects.isNull(queryParamsMap)) return wrapperList;
        prepareNeccesaryData(queryParamsMap);
        if (!queryParamsMap.hasKey(RoutePath.REALM)) throw new NoSuchElementException("realm is necessary");
        String realm = queryParamsMap.get(RoutePath.REALM).value();
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
        String defaultFilter = queryParamsMap.get(RoutePath.FILTER_DEFAULT).value();
        List<TissueList> tissueListList;
        if ("0".equals(defaultFilter)) {
            tissueListList = TissueList.getAllTissueListsForRealmNoFilter(realm);
            wrapperList = TissueListWrapper.getTissueListData(ddpInstance, null, tissueListList);
        } else if ("1".equals(defaultFilter)) {
            String userEmail = queryParamsMap.value(RequestParameter.USER_MAIL);
            String defaultFilterName = ViewFilter.getDefaultFilterForUser(userEmail, TISSUE_LIST_PARENT);
            if (StringUtils.isNotBlank(defaultFilterName)) {
                wrapperList = getListBasedOnFilterName(defaultFilterName, realm, TISSUE_LIST_PARENT, null, null);
            } else {
                tissueListList = TissueList.getAllTissueListsForRealmNoFilter(realm);
                wrapperList = TissueListWrapper.getTissueListData(ddpInstance, null, tissueListList);
            }
        } else {
            wrapperList = filterTissueList(filters, PatchUtil.getColumnNameMap(), quickFilterName, ddpInstance, filterQuery);
        }
        logger.info("Found " + wrapperList.size() + " tissues for Tissue View");
        return wrapperList;
    }
}
