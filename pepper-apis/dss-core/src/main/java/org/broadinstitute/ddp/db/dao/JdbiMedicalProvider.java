package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiMedicalProvider extends SqlObject {
    /**
     * Inserts a medical provider into the database for a particular user and study
     * Do not call this directly or we will be sad: There are hooks in the DAO to inform
     * DSM of the change
     * .-""""""-.
     * .'        '.
     * /   O    O  \
     * :            :
     * |            |
     * :    .------.:
     * \  '        '/
     * '.          '
     * '-......-'
     *
     * @param medicalProviderDto medical provider to add
     * @return number of inserted rows
     */
    @SqlUpdate(
            "insert into user_medical_provider (user_id, umbrella_study_id, institution_type_id,"
                    + " user_medical_provider_guid, institution_name, physician_name, city, state, postal_code, phone, legacy_guid, street)"
                    + " values (:userId, :umbrellaStudyId, "
                    + "   (select institution_type_id from institution_type where institution_type_code = :institutionType), "
                    + " :userMedicalProviderGuid, :institutionName, :physicianName, :city, :state, :postalCode, "
                    + ":phone, :legacyGuid, :street)"
    )
    int insert(@BindBean MedicalProviderDto medicalProviderDto);

    /**
     * Updates a medical provider in the database for a particular user and study
     * Do not call this directly or we will be sad: There are hooks in the DAO to inform
     * DSM of the change
     * .-""""""-.
     * .'        '.
     * /   O    O  \
     * :            :
     * |            |
     * :    .------.:
     * \  '        '/
     * '.          '
     * '-......-'
     *
     * @param medicalProviderDto medical provider to update
     * @return number of updated rows
     */
    @SqlUpdate(
            "update user_medical_provider set institution_type_id = "
                    + "(select institution_type_id from institution_type where institution_type_code = :institutionType), "
                    + " institution_name = :institutionName, physician_name = :physicianName, city = :city, state = :state"
                    + " where user_medical_provider_guid = :userMedicalProviderGuid"
    )
    int updateByGuid(@BindBean MedicalProviderDto medicalProviderDto);

    /**
     * Inserts a medical provider into the database for a particular user and study
     * Do not call this directly or we will be sad: There are hooks in the DAO to inform
     * DSM of the change
     * .-""""""-.
     * .'        '.
     * /   O    O  \
     * :            :
     * |            |
     * :    .------.:
     * \  '        '/
     * '.          '
     * '-......-'
     *
     * @param userMedicalProviderGuid medical provider to delete
     * @return number of deleted rows
     */
    @SqlUpdate("delete from user_medical_provider where user_medical_provider_guid = :userMedicalProviderGuid")
    int deleteByGuid(String userMedicalProviderGuid);

    /**
     * Inserts a medical provider into the database for a particular user and study
     * Do not call this directly or we will be sad: There are hooks in the DAO to inform
     * DSM of the change
     * .-""""""-.
     * .'        '.
     * /   O    O  \
     * :            :
     * |            |
     * :    .------.:
     * \  '        '/
     * '.          '
     * '-......-'
     *
     * @param id medical provider to delete
     * @return number of deleted rows
     */
    @SqlUpdate("delete from user_medical_provider where user_medical_provider_id = :id")
    int deleteById(@Bind("id") long id);

    @SqlUpdate("delete from user_medical_provider where user_id = :userId")
    int deleteByUserId(@Bind("userId") Long userId);

    @SqlQuery("SELECT "
            + "     ump.user_medical_provider_id, "
            + "     ump.user_medical_provider_guid, "
            + "     ump.user_id, "
            + "     ump.umbrella_study_id, "
            + "     it.institution_type_code, "
            + "     ump.institution_name, "
            + "     ump.physician_name, "
            + "     ump.city, "
            + "     ump.state,"
            + "     ump.postal_code,"
            + "     ump.phone,"
            + "     ump.legacy_guid,"
            + "     ump.street "
            + "FROM "
            + "     user_medical_provider ump "
            + "JOIN institution_type it ON it.institution_type_id = ump.institution_type_id "
            + "WHERE "
            + "     user_medical_provider_id = :userMedicalProviderId")
    @RegisterConstructorMapper(MedicalProviderDto.class)
    Optional<MedicalProviderDto> getById(@Bind("userMedicalProviderId") long userMedicalProviderId);


    @SqlQuery("SELECT "
            + "     ump.user_medical_provider_id, "
            + "     ump.user_medical_provider_guid, "
            + "     ump.user_id, "
            + "     ump.umbrella_study_id, "
            + "     it.institution_type_code, "
            + "     ump.institution_name, "
            + "     ump.physician_name, "
            + "     ump.city, "
            + "     ump.state,"
            + "     ump.postal_code,"
            + "     ump.phone,"
            + "     ump.legacy_guid,"
            + "     ump.street "
            + "FROM "
            + "     user_medical_provider ump "
            + "JOIN institution_type it ON it.institution_type_id = ump.institution_type_id "
            + "WHERE "
            + "     user_medical_provider_guid = :userMedicalProviderGuid")
    @RegisterConstructorMapper(MedicalProviderDto.class)
    Optional<MedicalProviderDto> getByGuid(@Bind("userMedicalProviderGuid") String userMedicalProviderGuid);

    @SqlQuery("SELECT "
            + "     ump.user_medical_provider_id, "
            + "     ump.user_medical_provider_guid, "
            + "     ump.user_id, "
            + "     ump.umbrella_study_id, "
            + "     it.institution_type_code, "
            + "     ump.institution_name, "
            + "     ump.physician_name, "
            + "     ump.city, "
            + "     ump.state,"
            + "     ump.postal_code,"
            + "     ump.phone,"
            + "     ump.legacy_guid,"
            + "     ump.street "
            + "FROM "
            + "     user_medical_provider ump "
            + "JOIN umbrella_study us ON ump.umbrella_study_id = us.umbrella_study_id "
            + "JOIN institution_type it ON it.institution_type_id = ump.institution_type_id "
            + "JOIN user u ON ump.user_id = u.user_id "
            + "WHERE "
            + "     u.guid = :userGuid AND us.guid = :studyGuid"
            + "     AND it.institution_type_id = :institutionTypeId"
    )
    @RegisterConstructorMapper(MedicalProviderDto.class)
    List<MedicalProviderDto> getAllByUserGuidStudyGuidAndInstitutionTypeId(
            @Bind("userGuid") String userGuid,
            @Bind("studyGuid") String studyGuid,
            @Bind("institutionTypeId") long institutionTypeId
    );

    @SqlQuery("SELECT "
            + "     ump.user_medical_provider_id, "
            + "     ump.user_medical_provider_guid, "
            + "     ump.user_id, "
            + "     ump.umbrella_study_id, "
            + "     it.institution_type_code, "
            + "     ump.institution_name, "
            + "     ump.physician_name, "
            + "     ump.city, "
            + "     ump.state,"
            + "     ump.postal_code,"
            + "     ump.phone,"
            + "     ump.legacy_guid,"
            + "     ump.street "
            + "FROM "
            + "     user_medical_provider ump "
            + "JOIN umbrella_study us ON ump.umbrella_study_id = us.umbrella_study_id "
            + "JOIN institution_type it ON it.institution_type_id = ump.institution_type_id "
            + "JOIN user u ON ump.user_id = u.user_id "
            + "WHERE "
            + "     u.guid = :userGuid and us.guid = :studyGuid "
    )
    @RegisterConstructorMapper(MedicalProviderDto.class)
    List<MedicalProviderDto> getAllByUserGuidStudyGuid(
            @Bind("userGuid") String userGuid,
            @Bind("studyGuid") String studyGuid
    );

    @SqlQuery("SELECT "
            + "     ump.user_medical_provider_id, "
            + "     ump.user_medical_provider_guid, "
            + "     ump.user_id, "
            + "     ump.umbrella_study_id, "
            + "     it.institution_type_code, "
            + "     ump.institution_name, "
            + "     ump.physician_name, "
            + "     ump.city, "
            + "     ump.state,"
            + "     ump.postal_code,"
            + "     ump.phone,"
            + "     ump.legacy_guid,"
            + "     ump.street "
            + "FROM "
            + "     user_medical_provider ump "
            + "JOIN institution_type it ON it.institution_type_id = ump.institution_type_id "
            + "WHERE "
            + "     ump.umbrella_study_id = :studyId "
            + "     AND ump.user_id in (<userIds>) "
    )
    @RegisterConstructorMapper(MedicalProviderDto.class)
    List<MedicalProviderDto> getAllByUsersAndStudyIds(
            @BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) List<Long> userIds,
            @Bind("studyId") long studyId
    );
}
