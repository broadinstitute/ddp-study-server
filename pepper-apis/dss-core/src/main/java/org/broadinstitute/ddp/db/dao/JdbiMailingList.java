package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiMailingList extends SqlObject {

    /**
     * Adds someone to the mailing list for a study if they are not already on it.
     *
     * @param firstName    person's first name
     * @param lastName     person's last name
     * @param emailAddress person's email address
     * @param studyGuid    the guid for the study
     * @param info         comma delimited list of custom info that the subscriber is adding
     * @param dateCreatedMillis to overwrite data creationg time
     * @return 1 if the person was added and weren't on the list before, 0 if they are already on the list.  Any other
     *         value indicates an error and is the number of rows updated.
     */
    @SqlUpdate("insert into study_mailing_list(first_name, last_name, email, umbrella_study_id, "
            + " umbrella_id, info, date_created) "
            + "(select :firstName, :lastName, :email, s.umbrella_study_id, NULL, "
            + " :info, :dateCreatedMillis from umbrella_study s "
            + "where s.guid = :studyGuid and not exists (select 1 from study_mailing_list l where email = :email "
            + "and umbrella_study_id = s.umbrella_study_id))"
    )
    int insertByStudyGuidIfNotStoredAlready(
            @Bind("firstName") String firstName,
            @Bind("lastName") String lastName,
            @Bind("email") String emailAddress,
            @Bind("studyGuid") String studyGuid,
            @Bind("info") String info,
            @Bind("dateCreatedMillis") Long dateCreatedMillis
    );

    /**
     * Adds someone to the mailing list for a study if they are not already on it.
     *
     * @param firstName    person's first name
     * @param lastName     person's last name
     * @param emailAddress person's email address
     * @param studyGuid    the guid for the study
     * @param info         comma delimited list of custom info that the subscriber is adding
     * @param dateCreatedMillis to overwrite data creationg time
     * @param languageCodeId mailing list user's preferred language
     * @return 1 if the person was added and weren't on the list before, 0 if they are already on the list.  Any other
     *         value indicates an error and is the number of rows updated.
     */
    @SqlUpdate("insert into study_mailing_list(first_name, last_name, email, umbrella_study_id, "
            + " umbrella_id, info, date_created, language_code_id) "
            + "(select :firstName, :lastName, :email, s.umbrella_study_id, NULL, "
            + " :info, :dateCreatedMillis, :languageCodeId from umbrella_study s "
            + "where s.guid = :studyGuid and not exists (select 1 from study_mailing_list l where email = :email "
            + "and umbrella_study_id = s.umbrella_study_id))"
    )
    int insertByStudyGuidIfNotStoredAlready(
            @Bind("firstName") String firstName,
            @Bind("lastName") String lastName,
            @Bind("email") String emailAddress,
            @Bind("studyGuid") String studyGuid,
            @Bind("info") String info,
            @Bind("dateCreatedMillis") Long dateCreatedMillis,
            @Bind("languageCodeId") Long languageCodeId
    );

    /**
     * Adds someone to the mailing list for a study if they are not already on it
     * Works similar to  insertByStudyGuidIfNotStoredAlready() but uses `umbrellaGuid`
     */
    @SqlUpdate("insert into study_mailing_list(first_name, last_name, email, umbrella_study_id, "
            + " umbrella_id, info, date_created, language_code_id) "
            + "(select :firstName, :lastName, :email, NULL, u.umbrella_id, "
            + " :info, :dateCreatedMillis, :languageCodeId from umbrella u "
            + "where u.umbrella_guid = :umbrellaGuid and not exists (select 1 from study_mailing_list l where l.email = :email "
            + "and l.umbrella_id = u.umbrella_id))"
    )
    int insertByUmbrellaGuidIfNotStoredAlready(
            @Bind("firstName") String firstName,
            @Bind("lastName") String lastName,
            @Bind("email") String emailAddress,
            @Bind("info") String info,
            @Bind("dateCreatedMillis") Long dateCreatedMillis,
            @Bind("umbrellaGuid") String umbrellaGuid,
            @Bind("languageCodeId") Long languageCodeId
    );

    /**
     * Bulk insert mailing list entries for a study but only if email is not already in the list.
     *
     * @param entries list of entries to insert
     * @return array of insert counts, where each element is 1 if email was added and weren't on the list before, or 0
     *         if they are already on the list. Any other value indicates an error and is the number of rows updated.
     */
    @SqlBatch("insert into study_mailing_list (first_name, last_name, email, umbrella_study_id, umbrella_id, info, date_created)"
            + "select :dto.getFirstName, :dto.getLastName, :dto.getEmail, s.umbrella_study_id, :dto.getUmbrellaId,"
            + "       :dto.getInfo, :dto.getDateCreatedMillis"
            + "  from umbrella_study as s"
            + " where s.guid = :dto.getStudyGuid"
            + "   and not exists (select 1 from study_mailing_list ls"
            + "       where ls.email = :dto.getEmail and ls.umbrella_study_id = s.umbrella_study_id)")
    int[] bulkInsertIfNotStoredAlready(@BindMethods("dto") Iterable<MailingListEntryDto> entries);

    @SqlUpdate("delete from study_mailing_list where study_mailing_list_id = :id")
    int deleteById(@Bind("id") long id);

    @SqlUpdate("delete from study_mailing_list where email = :email and umbrella_study_id = :studyId")
    int deleteByEmailAndStudyId(@Bind("email") String email, @Bind("studyId") long studyId);

    @SqlUpdate("update study_mailing_list set date_created = :dateCreatedMillis where email = :email and umbrella_study_id = :studyId")
    int updateDateCreatedByEmailAndStudy(
            @Bind("dateCreatedMillis") long dateCreatedMillis,
            @Bind("email") String email,
            @Bind("studyId") long studyId
    );

    @SqlQuery("select study_mailing_list_id from study_mailing_list as sml "
            + "join umbrella_study as study on study.umbrella_study_id = sml.umbrella_study_id "
            + "where sml.email = :email and study.guid = :studyGuid")
    Optional<Long> findIdByEmailAndStudyGuid(@Bind("email") String email, @Bind("studyGuid") String studyGuid);

    @SqlQuery("select l.first_name, l.last_name, l.email, :studyGuid as study_guid, l.umbrella_id, l.info, l.additional_information,  "
            + " date_created as date_created_millis, l.language_code_id, lc.iso_language_code as language_code "
            + " from study_mailing_list l join umbrella_study s on s.umbrella_study_id = l.umbrella_study_id  "
            + " left join language_code as lc on lc.language_code_id = l.language_code_id"
            + " where s.guid = :studyGuid and l.email = :email")
    @RegisterConstructorMapper(MailingListEntryDto.class)
    Optional<MailingListEntryDto> findByEmailAndStudy(@Bind("email") String emailAddress,
                                                      @Bind("studyGuid") String studyGuid);

    @SqlQuery(
            "select sml.first_name, sml.last_name, sml.email, sml.info, "
             + "  sml.additional_information, NULL as study_guid, sml.umbrella_id, sml.date_created as date_created_millis, "
             +       "sml.language_code_id, lc.iso_language_code as language_code "
             + " from study_mailing_list sml join umbrella u on sml.umbrella_id = u.umbrella_id "
             + " left join language_code as lc on lc.language_code_id = sml.language_code_id "
             + " where sml.email = :email and u.umbrella_guid = :umbrellaGuid"
    )
    @RegisterConstructorMapper(MailingListEntryDto.class)
    Optional<MailingListEntryDto> findByEmailAndUmbrellaGuid(
            @Bind("email") String emailAddress,
            @Bind("umbrellaGuid") String umbrellaGuid
    );

    @SqlQuery("select l.first_name, l.last_name, l.email, :studyGuid as study_guid, l.umbrella_id, l.info, l.additional_information,"
            + " date_created as date_created_millis, l.language_code_id, lc.iso_language_code as language_code "
            + " from study_mailing_list l join umbrella_study s on s.umbrella_study_id = l.umbrella_study_id "
            + " left join language_code as lc on lc.language_code_id = l.language_code_id"
            + " where s.guid = :studyGuid")
    @RegisterConstructorMapper(MailingListEntryDto.class)
    List<MailingListEntryDto> findByStudy(@Bind("studyGuid") String studyGuid);

    class MailingListEntryDto {
        private String firstName;
        private String lastName;
        private String email;
        private String info;
        private String studyGuid;
        private Long umbrellaId;
        private long dateCreatedMillis;
        private Long languageCodeId;
        private String languageCode;

        public MailingListEntryDto(
                String firstName,
                String lastName,
                String email,
                String studyGuid,
                Long umbrellaId,
                String info,
                long dateCreatedMillis,
                Long  languageCodeId,
                String languageCode
        ) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.studyGuid = studyGuid;
            this.umbrellaId = umbrellaId;
            this.info = info;
            this.dateCreatedMillis = dateCreatedMillis;
            this.languageCodeId = languageCodeId;
            this.languageCode = languageCode;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getEmail() {
            return email;
        }

        public String getInfo() {
            return info;
        }

        public String getStudyGuid() {
            return studyGuid;
        }

        public Long getUmbrellaId() {
            return umbrellaId;
        }

        public long getDateCreatedMillis() {
            return dateCreatedMillis;
        }

        public Long getLanguageCodeId() {
            return languageCodeId;
        }

        public String getLanguageCode() {
            return languageCode;
        }
    }
}
