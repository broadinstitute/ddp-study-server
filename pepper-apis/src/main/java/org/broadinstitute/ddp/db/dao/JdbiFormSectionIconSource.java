package org.broadinstitute.ddp.db.dao;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiFormSectionIconSource extends SqlObject {

    @SqlUpdate("insert into form_section_icon_source (form_section_icon_id, scale_factor_id, url)"
            + " values (:iconId, (select scale_factor_id from scale_factor where name = :scale), :url)")
    @GetGeneratedKeys
    long insert(@Bind("iconId") long iconId, @Bind("scale") String scaleFactor, @Bind("url") String url);

    default long insert(long iconId, String scaleFactor, URL url) {
        return insert(iconId, scaleFactor, url.toString());
    }

    @SqlBatch("insert into form_section_icon_source (form_section_icon_id, scale_factor_id, url)"
            + " values (:iconId, (select scale_factor_id from scale_factor where name = :scale), :url)")
    @GetGeneratedKeys
    long[] bulkInsert(@Bind("iconId") long iconId, @Bind("scale") List<String> scaleFactors, @Bind("url") List<String> urls);

    default long[] bulkInsert(long iconId, Map<String, URL> sources) {
        List<String> scaleFactors = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        sources.forEach((scaleFactor, url) -> {
            scaleFactors.add(scaleFactor);
            urls.add(url.toString());
        });
        return bulkInsert(iconId, scaleFactors, urls);
    }
}
