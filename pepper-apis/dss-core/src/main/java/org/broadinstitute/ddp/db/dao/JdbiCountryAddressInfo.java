package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.address.CountryAddressInfo;
import org.broadinstitute.ddp.model.address.CountryAddressInfoSummary;
import org.broadinstitute.ddp.model.address.SubnationalDivision;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

@UseStringTemplateSqlLocator
public interface JdbiCountryAddressInfo extends SqlObject {
    @SqlQuery("getAllCountryAddressInfoSummaries")
    @RegisterBeanMapper(CountryAddressInfoSummary.class)
    List<CountryAddressInfoSummary> getAllCountryAddressInfoSummaries();

    /**
     * Get all the address info summaries sorted by name, with U.S. and Canada at top.
     *
     * @return list of summaries
     */
    default List<CountryAddressInfoSummary> getAllOrderedSummaries() {
        CountryAddressInfoSummary usa = null;
        CountryAddressInfoSummary canada = null;
        List<CountryAddressInfoSummary> summaries = new ArrayList<>();

        for (CountryAddressInfoSummary summary : getAllCountryAddressInfoSummaries()) {
            if (Locale.US.getCountry().equals(summary.getCode())) {
                usa = summary;
            } else if (Locale.CANADA.getCountry().equals(summary.getCode())) {
                canada = summary;
            } else {
                summaries.add(summary);
            }
        }

        if (usa == null) {
            throw new DaoException("Could not find address into for U.S.");
        }

        if (canada == null) {
            throw new DaoException("Could not find address info for Canada.");
        }

        summaries.sort(Comparator.comparing(CountryAddressInfoSummary::getName));
        summaries.add(0, canada);
        summaries.add(0, usa);

        return summaries;
    }


    @SqlQuery
    @RegisterBeanMapper(value = CountryAddressInfo.class, prefix = "c")
    @RegisterBeanMapper(value = SubnationalDivision.class, prefix = "d")
    @UseRowReducer(CountryDivisionRowReducer.class)
    Optional<CountryAddressInfo> getCountryAddressInfo(String countryCode);


    class CountryDivisionRowReducer implements LinkedHashMapRowReducer<Long, CountryAddressInfo> {
        @Override
        public void accumulate(Map<Long, CountryAddressInfo> map, RowView rowView) {
            CountryAddressInfo country = map.computeIfAbsent(rowView.getColumn("c_country_address_info_id", Long.class),
                    id -> rowView.getRow(CountryAddressInfo.class));
            if (rowView.getColumn("d_country_subnational_division_id", Long.class) != null) {
                country.addSubnationalDivision(rowView.getRow(SubnationalDivision.class));
            }
        }
    }
}
