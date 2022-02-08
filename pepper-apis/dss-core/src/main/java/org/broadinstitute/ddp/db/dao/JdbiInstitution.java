package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.dto.InstitutionDto;
import org.broadinstitute.ddp.db.dto.InstitutionSuggestionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiInstitution extends SqlObject {

    @SqlQuery(
            "   select institution_id, institution_guid, city_id, name"
            + " from institution where institution_guid = :institutionGuid"
    )
    @RegisterRowMapper(InstitutionDto.InstitutionDtoMapper.class)
    Optional<InstitutionDto> getByGuid(@Bind("institutionGuid") String institutionGuid);

    @SqlQuery("getSuggestionsByNamePattern")
    @UseStringTemplateSqlLocator
    @RegisterRowMapper(InstitutionSuggestionDto.InstitutionSuggestionDtoMapper.class)
    List<InstitutionSuggestionDto> getSuggestionsByNamePattern(@Bind("namePattern") String namePattern);

    @SqlQuery("getLimitedSuggestionsByAnchoredAndFreeNamePatterns")
    @UseStringTemplateSqlLocator
    @RegisterRowMapper(InstitutionSuggestionDto.InstitutionSuggestionDtoMapper.class)
    Stream<InstitutionSuggestionDto> getLimitedSuggestionsByNamePatterns(@Bind("anchored") String anchoredPattern,
                                                                         @Bind("free") String freePattern,
                                                                         @Bind("limit") int limit);

    @SqlUpdate("delete from institution where institution_guid = :institutionGuid")
    int deleteByGuid(@Bind("institutionGuid") String institutionGuid);

    @SqlUpdate("delete from institution where institution_id in (<ids>)")
    int bulkDeleteByIds(@BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) Set<Long> ids);

    @SqlUpdate(
            "   insert into institution (institution_guid, city_id, name)"
            + " values (:institutionGuid, :cityId, :name)"
    )
    @GetGeneratedKeys
    long insert(@BindBean InstitutionDto institutionDto);

    @SqlUpdate(
            "   insert into institution_alias (alias)"
            + " values (:institutionAlias)"
    )
    @GetGeneratedKeys
    long insertInsitutionAlias(@Bind("institutionAlias") String institutionAlias);

    @SqlUpdate(
            "   insert into institution__institution_alias (institution_id, institution_alias_id)"
            + " values (:institutionId, :institutionAliasId)"
    )
    int insertInsitutionInstitutionAlias(
            @Bind("institutionId") long institutionId,
            @Bind("institutionAliasId") long institutionAliasId
    );

    @SqlUpdate("delete from institution_alias where institution_alias_id = :institutionAliasId")
    int deleteInstitutionAlias(
            @Bind("institutionAliasId") long institutionAliasId
    );

    @SqlUpdate(
            "delete from institution__institution_alias where institution_id = :institutionId "
            + " and institution_alias_id = :institutionAliasId"
    )
    int deleteInstitutionInstitutionAlias(
            @Bind("institutionId") long institutionId,
            @Bind("institutionAliasId") long institutionAliasId
    );

    default long insertAlias(long institutionId, String alias) {
        long institutionAliasId = insertInsitutionAlias(alias);
        insertInsitutionInstitutionAlias(institutionId, institutionAliasId);
        return institutionAliasId;
    }

    default int deleteAlias(long institutionId, long institutionAliasId) {
        int numDeletedRelTable = deleteInstitutionInstitutionAlias(institutionId, institutionAliasId);
        int numDeletedAlias = deleteInstitutionAlias(institutionAliasId);
        return numDeletedAlias + numDeletedRelTable;
    }

}
