package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiCountry extends SqlObject {
    @SqlQuery("queryPrimaryLanguageIdByCountryCode")
    @UseStringTemplateSqlLocator
    public Long getPrimaryLanguageIdByCountryCode(@Bind("countryCode") String countryCode);

    @SqlQuery("select country_name from country where country_id = :countryId")
    String getCountryNameById(@Bind Long countryId);

    @SqlQuery("select country_id from country where country_code = :code")
    public Long getCountryIdByCode(@Bind String code);

    @SqlQuery("select country_name from country where country_code = :code")
    public String getCountryNameByCode(@Bind("code") String countryCode);
}
