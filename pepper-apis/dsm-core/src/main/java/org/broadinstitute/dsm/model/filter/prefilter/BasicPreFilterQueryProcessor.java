package org.broadinstitute.dsm.model.filter.prefilter;

import java.util.Map;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

// The purpose of this class is to add a new filter if it's not in the original filters
// or concatenate it with the existing filter yielding the joined filter for the specific key
public class BasicPreFilterQueryProcessor implements PreFilterQueryProcessor {

    private static final int PREFIX_WITH_ALIAS_INDEX = 0;
    private static final int ALIAS_INDEX             = 2;

    private final Map<String, String> filters;

    public BasicPreFilterQueryProcessor(Map<String, String> originalFilters) {
        this.filters = originalFilters;
    }

    @Override
    public Map<String, String> update(String query) {
        return addIfAbsentOrMergeIfPresent(query);
    }

    private Map<String, String> addIfAbsentOrMergeIfPresent(String query) {
        String prefixWithAlias = query.split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR)[PREFIX_WITH_ALIAS_INDEX];
        String alias           = prefixWithAlias.split(Filter.SPACE)[ALIAS_INDEX];
        if (isAliasPresent(alias)) {
            concatenateWithExistingQuery(query, alias);
        } else {
            addNewQuery(query, alias);
        }
        return filters;
    }

    private boolean isAliasPresent(String alias) {
        return filters.containsKey(alias);
    }

    private void addNewQuery(String query, String alias) {
        filters.put(alias, query);
    }

    private void concatenateWithExistingQuery(String query, String alias) {
        filters.put(alias, filters.get(alias).concat(query));
    }

}
