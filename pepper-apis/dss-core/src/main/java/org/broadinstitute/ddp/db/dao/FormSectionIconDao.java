package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionIconSourceTable;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionIconTable;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ScaleFactorTable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;

public interface FormSectionIconDao extends SqlObject {

    @CreateSqlObject
    JdbiFormSectionIcon getJdbiFormSectionIcon();

    @CreateSqlObject
    JdbiFormSectionIconSource getJdbiFormSectionIconSource();

    default void insertIcons(long sectionId, List<SectionIcon> icons) {
        icons.forEach(icon -> insertIcon(sectionId, icon));
    }

    default long insertIcon(long sectionId, SectionIcon icon) {
        long iconId = getJdbiFormSectionIcon().insert(sectionId, icon);
        long[] iconSourceIds = getJdbiFormSectionIconSource().bulkInsert(iconId, icon.getSources());
        if (iconSourceIds.length != icon.getSources().size()) {
            throw new DaoException("Not all icon source urls inserted for section " + sectionId);
        }
        icon.setSectionId(sectionId);
        return iconId;
    }

    default Optional<SectionIcon> findById(long iconId) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(FormSectionIconDao.class, "querySectionIconAndSourcesById")
                .render();
        Map<Long, SectionIcon> res = getHandle().createQuery(query)
                .bind("iconId", iconId)
                .registerRowMapper(ConstructorMapper.factory(SectionIcon.class))
                .reduceRows(new HashMap<>(), new IconRowReducer());
        return Optional.ofNullable(res.get(iconId));
    }

    default Collection<SectionIcon> findAllBySectionId(long sectionId) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(FormSectionIconDao.class, "querySectionIconsBySectionId")
                .render();
        Map<Long, SectionIcon> res = getHandle().createQuery(query)
                .bind("sectionId", sectionId)
                .registerRowMapper(ConstructorMapper.factory(SectionIcon.class))
                .reduceRows(new HashMap<>(), new IconRowReducer());
        return new ArrayList<>(res.values());
    }

    class IconRowReducer implements BiFunction<Map<Long, SectionIcon>, RowView, Map<Long, SectionIcon>> {
        @Override
        public Map<Long, SectionIcon> apply(Map<Long, SectionIcon> accumulator, RowView row) {
            long iconId = row.getColumn(FormSectionIconTable.ID, Long.class);
            long sectionId = row.getColumn(FormSectionTable.ID, Long.class);

            SectionIcon icon = accumulator.computeIfAbsent(iconId, id -> row.getRow(SectionIcon.class));
            String scale = row.getColumn(ScaleFactorTable.NAME, String.class);
            String rawUrl = row.getColumn(FormSectionIconSourceTable.URL, String.class);

            URL url;
            try {
                url = new URL(rawUrl);
            } catch (MalformedURLException e) {
                String msg = String.format("Encountered malformed url '%s' while processing scale factor '%s' for"
                        + " icon id %d and section id %d", rawUrl, scale, iconId, sectionId);
                throw new DaoException(msg, e);
            }

            icon.putSource(scale, url);
            return accumulator;
        }
    }
}
