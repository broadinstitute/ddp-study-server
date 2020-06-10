package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUmbrellaStudy extends SqlObject {

    @CreateSqlObject
    JdbiOLCPrecision getJdbiOLCPrecision();

    @SqlQuery("select u.umbrella_guid"
            + " from umbrella u"
            + " join umbrella_study us on us.umbrella_id = u.umbrella_id"
            + " where us.umbrella_study_id = :studyId")
    String findUmbrellaGuidForStudyId(@Bind("studyId") long studyId);

    @SqlQuery("select us.umbrella_study_id, us.umbrella_id, us.study_name, us.guid, us.irb_password, us.web_base_url, us.auth0_tenant_id,"
            + " op.olc_precision_code, us.share_participant_location, us.study_email, us.enable_data_export, us.recaptcha_site_key"
            + " from umbrella_study us"
            + " left join olc_precision op on op.olc_precision_id = us.olc_precision_id")
    @RegisterConstructorMapper(StudyDto.class)
    List<StudyDto> findAll();

    @SqlQuery("select us.umbrella_study_id, us.umbrella_id, us.study_name, us.guid, us.irb_password, us.web_base_url, us.auth0_tenant_id,"
            + " op.olc_precision_code, us.share_participant_location, us.study_email, us.enable_data_export, us.recaptcha_site_key"
            + " from umbrella_study us"
            + " left join olc_precision op on op.olc_precision_id = us.olc_precision_id"
            + " where us.guid = :studyGuid")
    @RegisterConstructorMapper(StudyDto.class)
    StudyDto findByStudyGuid(@Bind("studyGuid") String studyGuid);

    @SqlQuery("select us.umbrella_study_id, us.umbrella_id, us.study_name, us.guid, us.irb_password, us.web_base_url, us.auth0_tenant_id,"
            + " op.olc_precision_code, us.share_participant_location, us.study_email, us.enable_data_export, us.recaptcha_site_key"
            + " from umbrella_study us"
            + " left join olc_precision op on op.olc_precision_id = us.olc_precision_id"
            + " where us.umbrella_study_id = :studyId")
    @RegisterConstructorMapper(StudyDto.class)
    StudyDto findById(@Bind("studyId") long studyId);

    @SqlQuery("select us.umbrella_study_id, us.umbrella_id, us.study_name, us.guid, us.irb_password, us.web_base_url, us.auth0_tenant_id,"
            + " op.olc_precision_code, us.share_participant_location, us.study_email, us.enable_data_export, us.recaptcha_site_key"
            + " from umbrella_study as us"
            + " join umbrella as u on us.umbrella_id = u.umbrella_id"
            + " left join olc_precision op on op.olc_precision_id = us.olc_precision_id"
            + " where u.umbrella_guid = :umbrellaGuid")
    @RegisterConstructorMapper(StudyDto.class)
    List<StudyDto> findByUmbrellaGuid(@Bind("umbrellaGuid") String umbrellaGuid);

    @SqlQuery("select us.umbrella_study_id, us.umbrella_id, us.study_name, us.guid, us.irb_password, us.web_base_url, us.auth0_tenant_id,"
            + " op.olc_precision_code, us.share_participant_location, us.study_email, us.enable_data_export, us.recaptcha_site_key"
            + " from umbrella_study as us"
            + " join auth0_tenant as t on t.auth0_tenant_id = us.auth0_tenant_id "
            + " left join olc_precision op on op.olc_precision_id = us.olc_precision_id"
            + " where t.auth0_domain = :domain"
            + " and us.guid = :studyGuid")
    @RegisterConstructorMapper(StudyDto.class)
    StudyDto findByDomainAndStudyGuid(@Bind("domain") String auth0Domain, @Bind("studyGuid") String studyGuid);

    @SqlQuery("select umbrella_study_id from umbrella_study where guid = :studyGuid")
    Optional<Long> getIdByGuid(@Bind("studyGuid") String studyGuid);

    @SqlQuery("select guid from umbrella_study where umbrella_study_id = :id")
    String findGuidByStudyId(@Bind("id") long id);

    @SqlUpdate("update umbrella_study set share_participant_location = :shareLocation where guid = :guid")
    int updateShareLocationForStudy(@Bind("shareLocation") boolean shareLocation, @Bind("guid") String guid);

    @SqlUpdate("UPDATE umbrella_study"
            + " set olc_precision_id = (select olc_precision_id from olc_precision where olc_precision_code = :precisionCode)"
            + " WHERE guid = :guid")
    int updateOlcPrecisionForStudy(@Bind("precisionCode") OLCPrecision precision, @Bind("guid") String guid);

    @SqlQuery("select irb_password from umbrella_study where guid = :studyGuid")
    String getIrbPasswordUsingStudyGuid(String studyGuid);

    @SqlUpdate("insert into umbrella_study(study_name, guid, umbrella_id, web_base_url, auth0_tenant_id,"
            + " irb_password, olc_precision_id, share_participant_location, study_email, enable_data_export, recaptcha_site_key) "
            + "values(:studyName, :studyGuid, :umbrellaId, :webBaseUrl, :auth0TenantId, :irbPassword, :precisionId,"
            + " :shareLocation, :studyEmail, true, :recaptchaSiteKey)")
    @GetGeneratedKeys
    long insert(@Bind("studyName") String studyName,
                @Bind("studyGuid") String studyGuid,
                @Bind("umbrellaId") long umbrellaId,
                @Bind("webBaseUrl") String webBaseUrl,
                @Bind("auth0TenantId") long auth0TenantId,
                @Bind("irbPassword") String irbPassword,
                @Bind("precisionId") Long precisionId,
                @Bind("shareLocation") boolean shareLocation,
                @Bind("studyEmail") String studyEmail,
                @Bind("recaptchaSiteKey") String recaptchaSiteKey);

    default long insert(String studyName, String studyGuid, long umbrellaId, String webBaseUrl, long auth0TenantId,
                        OLCPrecision precision, boolean shareLocation, String studyEmail, String recaptchaSiteKey) {
        Long precisionId;
        if (precision == null) {
            precisionId = null;
        } else {
            precisionId = getJdbiOLCPrecision().findDtoForCode(precision).getId();
        }
        return insert(studyName, studyGuid, umbrellaId, webBaseUrl, auth0TenantId, null, precisionId, shareLocation, studyEmail,
                recaptchaSiteKey);
    }

    @SqlUpdate("update umbrella_study set irb_password = :irbPassword where guid = :studyGuid")
    int updateIrbPasswordByGuid(@Bind("irbPassword") String irbPassword, @Bind("studyGuid") String studyGuid);

    @SqlUpdate("update umbrella_study set web_base_url = :webBaseUrl where guid = :studyGuid")
    boolean updateWebBaseUrl(@Bind("studyGuid") String studyGuid,
                             @Bind("webBaseUrl") String webBaseUrl);

    @SqlQuery("select umbrella_guid from umbrella u join umbrella_study us on u.umbrella_id=us.umbrella_id where us.guid = :studyGuid")
    String getUmbrellaGuidForStudyGuid(@Bind("studyGuid") String studyGuid);
}
