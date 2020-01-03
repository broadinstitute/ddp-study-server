package org.broadinstitute.ddp.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile.SqlQuery;
import org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceStatusTypeTable;
import org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceTable;
import org.broadinstitute.ddp.constants.SqlConstants.FormActivitySettingTable;
import org.broadinstitute.ddp.constants.SqlConstants.FormTypeTable;
import org.broadinstitute.ddp.constants.SqlConstants.StudyActivityTable;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormInstanceDao {

    private static final Logger LOG = LoggerFactory.getLogger(FormInstanceDao.class);

    private SectionBlockDao sectionBlockDao;

    private String formActivityByGuidQuery;

    public static FormInstanceDao fromDaoAndConfig(SectionBlockDao sectionBlockDao, Config sqlConfig) {
        return new FormInstanceDao(sectionBlockDao,
                sqlConfig.getString(SqlQuery.FORM_ACTIVITY_BY_GUID));
    }

    public FormInstanceDao(SectionBlockDao sectionBlockDao, String formActivityByGuidQuery) {
        this.sectionBlockDao = sectionBlockDao;
        this.formActivityByGuidQuery = formActivityByGuidQuery;
    }

    /**
     * Get specific form activity instance, translated to given language.
     *
     * @param handle       the jdbi handle
     * @param instanceGuid the form instance guid
     * @param isoLangCode  the language iso code
     * @param style        the content style to use for converting content
     * @return form activity instance, or null if not found
     */
    public FormInstance getTranslatedFormByGuid(Handle handle, String instanceGuid, String isoLangCode, ContentStyle style) {
        return getTranslatedFormByGuid(handle, instanceGuid, isoLangCode, style, false);
    }

    // This allows fetching form with deprecated questions. Prefer the other method that excludes them.
    public FormInstance getTranslatedFormByGuid(Handle handle, String instanceGuid, String isoLangCode, ContentStyle style,
                                                boolean includeDeprecated) {
        FormInstance form = getBaseFormByGuid(handle, instanceGuid, isoLangCode);
        if (form != null) {
            long langCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(isoLangCode);
            loadAllSectionsForForm(handle, form, langCodeId, includeDeprecated);
            form.renderContent(handle, new I18nContentRenderer(), langCodeId, style);
            form.setDisplayNumbers();
        }
        return form;
    }

    /**
     * Get only basic properties of a form activity instance, without any of the sections or blocks.
     *
     * @param handle       the jdbi handle
     * @param instanceGuid the form instance guid
     * @param isoLangCode  the language iso code
     * @return form activity instance with only the basic properties, or null if not found
     */
    public FormInstance getBaseFormByGuid(Handle handle, String instanceGuid, String isoLangCode) {
        FormInstance form = null;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(formActivityByGuidQuery)) {
            stmt.setString(1, isoLangCode);
            stmt.setString(2, instanceGuid);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                FormType formType = FormType.valueOf(rs.getString(FormTypeTable.CODE));
                String name = rs.getString(ActivityInstanceTable.NAME);
                String subtitle = rs.getString(ActivityInstanceTable.SUBTITLE);
                String status = rs.getString(ActivityInstanceTable.STATUS_TYPE_NAME);
                String activityCode = rs.getString(StudyActivityTable.CODE);

                String listStyleHintCode = rs.getString("list_style_hint_code");
                ListStyleHint hint = listStyleHintCode == null ? null : ListStyleHint.valueOf(listStyleHintCode);
                Long readonlyHintTemplateId = (Long) rs.getObject(FormActivitySettingTable.READONLY_HINT_TEMPLATE_ID);
                Long introductionSectionId = (Long) rs.getObject("introduction_section_id");
                Long closingSectionId = (Long) rs.getObject("closing_section_id");

                long instanceId = rs.getLong(ActivityInstanceTable.ID);
                long activityId = rs.getLong(StudyActivityTable.ID);
                String statusTypeCode = rs.getString(ActivityInstanceStatusTypeTable.ACTIVITY_STATUS_TYPE_CODE);
                Long editTimeoutSec = (Long) rs.getObject(StudyActivityTable.EDIT_TIMEOUT_SEC);
                boolean isWriteOnce = rs.getBoolean(StudyActivityTable.IS_WRITE_ONCE);
                boolean isInstanceReadonly = rs.getBoolean(ActivityInstanceTable.IS_READONLY);
                long createdAtMillis = rs.getLong(ActivityInstanceTable.CREATED_AT);
                Long firstCompletedAt = (Long) rs.getObject(ActivityInstanceTable.FIRST_COMPLETED_AT);
                boolean isReadonly = ActivityInstanceUtil.isReadonly(editTimeoutSec, createdAtMillis, statusTypeCode,
                        isWriteOnce, isInstanceReadonly);
                Long lastUpdatedTemplateId = (Long)rs.getObject(FormActivitySettingTable.LAST_UPDATED_TEXT_TEMPLATE_ID);
                LocalDateTime lastUpdated = rs.getObject(FormActivitySettingTable.LAST_UPDATED, LocalDateTime.class);
                boolean isFollowup = rs.getBoolean(StudyActivityTable.IS_FOLLOWUP);
                boolean isHidden = rs.getBoolean(ActivityInstanceTable.IS_HIDDEN);
                // todo: handle isHidden
                form = new FormInstance(
                        instanceId,
                        activityId,
                        activityCode,
                        formType,
                        instanceGuid,
                        name,
                        subtitle,
                        status,
                        isReadonly,
                        hint,
                        readonlyHintTemplateId,
                        introductionSectionId,
                        closingSectionId,
                        createdAtMillis,
                        firstCompletedAt,
                        lastUpdatedTemplateId,
                        lastUpdated,
                        isFollowup
                );

                if (rs.next()) {
                    throw new RuntimeException("Too many rows found for form instance " + instanceGuid
                            + " and iso language " + isoLangCode);
                }
            } else {
                LOG.info("None found for form instance {} and iso language {}", instanceGuid, isoLangCode);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not query for form activity", e);
        }
        return form;
    }

    /**
     * Helper to fetch and fill in the sections and blocks for a form activity instance. Any pre-existing sections will be cleared.
     *
     * @param handle     the jdbi handle
     * @param form       the form activity instance
     * @param langCodeId the language code id
     */
    public void loadAllSectionsForForm(Handle handle, FormInstance form, long langCodeId) {
        loadAllSectionsForForm(handle, form, langCodeId, false);
    }

    // This allows fetching sections with deprecated questions. Prefer the other method that excludes them.
    public void loadAllSectionsForForm(Handle handle, FormInstance form, long langCodeId, boolean includeDeprecated) {
        List<Long> bodyIds = handle.attach(JdbiFormActivityFormSection.class).getOrderedBodySectionIds(form.getGuid());

        List<Long> sectionIds = new ArrayList<>(bodyIds);
        if (form.getIntroductionSectionId() != null) {
            sectionIds.add(form.getIntroductionSectionId());
        }
        if (form.getClosingSectionId() != null) {
            sectionIds.add(form.getClosingSectionId());
        }

        Map<Long, FormSection> sections = handle.attach(org.broadinstitute.ddp.db.dao.SectionBlockDao.class)
                .findAllInstanceSectionsById(sectionIds);
        Map<Long, List<FormBlock>> blockMap = sectionBlockDao.getBlocksForSections(handle, sectionIds, form.getGuid(), langCodeId,
                includeDeprecated);

        List<FormSection> bodySections = new ArrayList<>();
        for (long id : bodyIds) {
            FormSection section = sections.get(id);
            if (section == null) {
                throw new DaoException("Could not find body section with id " + id + " for instance " + form.getGuid());
            }
            section.addBlocks(blockMap.getOrDefault(id, new ArrayList<>()));
            bodySections.add(section);
        }
        form.getBodySections().clear();
        form.addBodySections(bodySections);

        if (form.getIntroductionSectionId() != null) {
            FormSection intro = sections.get(form.getIntroductionSectionId());
            if (intro == null) {
                throw new DaoException("Could not find introduction section with id "
                        + form.getIntroductionSectionId() + " for instance " + form.getGuid());
            }
            intro.addBlocks(blockMap.getOrDefault(form.getIntroductionSectionId(), new ArrayList<>()));
            form.setIntroduction(intro);
        } else {
            form.setIntroduction(null);
        }

        if (form.getClosingSectionId() != null) {
            FormSection closing = sections.get(form.getClosingSectionId());
            if (closing == null) {
                throw new DaoException("Could not find closing section with id "
                        + form.getClosingSectionId() + " for instance " + form.getGuid());
            }
            closing.addBlocks(blockMap.getOrDefault(form.getClosingSectionId(), new ArrayList<>()));
            form.setClosing(closing);
        } else {
            form.setClosing(null);
        }
    }
}
