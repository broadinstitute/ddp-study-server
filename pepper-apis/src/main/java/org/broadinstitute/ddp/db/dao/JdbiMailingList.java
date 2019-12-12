package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
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
     * Adds someone to the mailing list for a study if they are not already on it
     * Works similar to  insertByStudyGuidIfNotStoredAlready() but uses `umbrellaGuid`
     */
    @SqlUpdate("insert into study_mailing_list(first_name, last_name, email, umbrella_study_id, "
            + " umbrella_id, info, date_created) "
            + "(select :firstName, :lastName, :email, NULL, u.umbrella_id, "
            + " :info, :dateCreatedMillis from umbrella u "
            + "where u.umbrella_guid = :umbrellaGuid and not exists (select 1 from study_mailing_list l where l.email = :email "
            + "and l.umbrella_id = u.umbrella_id))"
    )
    int insertByUmbrellaGuidIfNotStoredAlready(
            @Bind("firstName") String firstName,
            @Bind("lastName") String lastName,
            @Bind("email") String emailAddress,
            @Bind("info") String info,
            @Bind("dateCreatedMillis") Long dateCreatedMillis,
            @Bind("umbrellaGuid") String umbrellaGuid
    );

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

    @SqlQuery("select l.first_name, l.last_name, l.email, :studyCode as study_code, l.umbrella_id, l.info, l.additional_information,"
            + " date_created as date_created_millis from study_mailing_list l, umbrella_study s "
            + " where s.guid = :studyCode and s.umbrella_study_id = l.umbrella_study_id and l.email = :email")
    @RegisterConstructorMapper(MailingListEntryDto.class)
    Optional<MailingListEntryDto> findByEmailAndStudy(@Bind("email") String emailAddress,
                                                      @Bind("studyCode") String studyCode);

    @SqlQuery(
            "select sml.first_name, sml.last_name, sml.email, sml.info, "
             + "sml.additional_information, NULL as study_code, sml.umbrella_id, sml.date_created as date_created_millis "
             + "from study_mailing_list sml join umbrella u on sml.umbrella_id = u.umbrella_id "
             + "where sml.email = :email and u.umbrella_guid = :umbrellaGuid"
    )
    @RegisterConstructorMapper(MailingListEntryDto.class)
    Optional<MailingListEntryDto> findByEmailAndUmbrellaGuid(
            @Bind("email") String emailAddress,
            @Bind("umbrellaGuid") String umbrellaGuid
    );

    @SqlQuery("select l.first_name, l.last_name, l.email, :studyCode as study_code, l.umbrella_id, l.info, l.additional_information,"
            + " date_created as date_created_millis from study_mailing_list l, umbrella_study s "
            + " where s.guid = :studyCode and s.umbrella_study_id = l.umbrella_study_id")
    @RegisterConstructorMapper(MailingListEntryDto.class)
    List<MailingListEntryDto> findByStudy(@Bind("studyCode") String studyCode);

    class MailingListEntryDto {
        private String firstName;
        private String lastName;
        private String email;
        private String info;
        private String studyCode;
        private Long umbrellaId;
        private long dateCreatedMillis;

        public MailingListEntryDto(
                String firstName,
                String lastName,
                String email,
                String studyCode,
                Long umbrellaId,
                String info,
                long dateCreatedMillis
        ) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.studyCode = studyCode;
            this.umbrellaId = umbrellaId;
            this.info = info;
            this.dateCreatedMillis = dateCreatedMillis;
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

        public String getStudyCode() {
            return studyCode;
        }

        public Long getUmbrellaId() {
            return umbrellaId;
        }

        public long getDateCreatedMillis() {
            return dateCreatedMillis;
        }
    }
}
