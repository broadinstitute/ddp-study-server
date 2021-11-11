package org.broadinstitute.ddp.pex;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

/**
 * Queries used internally in pex interpreter's data access.
 */
interface PexDao extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestActivityInstanceStatusByUserGuidActivityCodeAndQuestionStableId")
    Optional<InstanceStatusType> findLatestActivityInstanceStatus(@Bind("userGuid") String userGuid,
                                                                  @Bind("studyGuid") String studyGuid,
                                                                  @Bind("activityCode") String activityCode);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionTypeByUserInstanceAndStudyActivity")
    Optional<QuestionType> findQuestionType(@Bind("userGuid") String userGuid,
                                            @Bind("studyGuid") String studyGuid,
                                            @Bind("activityCode") String activityCode,
                                            @Bind("stableId") String stableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestBoolAnswerByUserGuidActivityCodeAndQuestionStableId")
    Boolean findLatestBoolAnswer(@Bind("userGuid") String userGuid,
                                 @Bind("activityCode") String activityCode,
                                 @Bind("stableId") String stableId,
                                 @Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryBoolAnswerByStableIdAndActivityInstance")
    Boolean findSpecificBoolAnswer(@Bind("activityInstanceGuid") String activityInstanceGuid,
                                   @Bind("stableId") String stableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestTextAnswerByUserGuidActivityCodeAndQuestionStableId")
    String findLatestTextAnswer(@Bind("userGuid") String userGuid,
                                @Bind("activityCode") String activityCode,
                                @Bind("stableId") String stableId,
                                @Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryTextAnswerByStableIdAndActivityInstance")
    String findSpecificTextAnswer(@Bind("activityInstanceGuid") String activityInstanceGuid,
                                  @Bind("stableId") String stableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestPicklistAnswersAsSelectedOptionGroupsByUserInstanceAndStableId")
    String findLatestPicklistAnswer(@Bind("userGuid") String userGuid,
                                    @Bind("activityCode") String activityCode,
                                    @Bind("stableId") String stableId,
                                    @Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("querySpecificPicklistAnswersAsSelectedOptionGroupsByStableIdAndActivityInstance")
    String findSpecificPicklistAnswer(@Bind("activityInstanceGuid") String activityInstanceGuid,
                                      @Bind("stableId") String stableId);


    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestDateAnswerByUserGuidActivityCodeAndQuestionStableId")
    @RegisterRowMapper(ZeroedDateValueMapper.class)
    DateValue findLatestDateAnswer(@Bind("userGuid") String userGuid,
                                   @Bind("activityCode") String activityCode,
                                   @Bind("stableId") String stableId,
                                   @Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDateAnswerByStableIdAndActivityInstance")
    @RegisterRowMapper(ZeroedDateValueMapper.class)
    DateValue findSpecificDateAnswer(@Bind("activityInstanceGuid") String activityInstanceGuid,
                                     @Bind("stableId") String stableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestNumericIntegerAnswerByUserGuidActivityCodeAndQuestionStableId")
    Long findLatestNumericIntegerAnswer(@Bind("userGuid") String userGuid,
                                        @Bind("activityCode") String activityCode,
                                        @Bind("stableId") String stableId,
                                        @Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryNumericIntegerAnswerByStableIdAndActivityInstance")
    Long findSpecificNumericIntegerAnswer(@Bind("activityInstanceGuid") String activityInstanceGuid,
                                          @Bind("stableId") String stableId);

    class ZeroedDateValueMapper implements RowMapper<DateValue> {
        @Override
        public DateValue map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new DateValue(
                    Optional.ofNullable((Integer) rs.getObject("year")).orElse(0),
                    Optional.ofNullable((Integer) rs.getObject("month")).orElse(0),
                    Optional.ofNullable((Integer) rs.getObject("day")).orElse(0));
        }
    }
}
