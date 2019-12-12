package org.broadinstitute.ddp.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.jdbi.v3.core.Handle;

/**
 * DAO class for performing CRUD operations on the TemplateVariable entity.
 */
public class TemplateVariableDao {

    private String templateVariablesWithTranslationsQuery;

    public TemplateVariableDao(String templateVariablesWithTranslationsQuery) {
        this.templateVariablesWithTranslationsQuery = templateVariablesWithTranslationsQuery;
    }

    /**
     * Given a templateId and languageCodeId, returns a Map containing template variables as
     * keys and their corresponding translations as values.
     *
     * @param handle           JDBC connection
     * @param templateId     Id of the template to fetch variables for
     * @param languageCodeId Id of the language to get translations for
     * @return A map containing translated template variables
     */
    public Map<String, String> getTranslatedTemplateVariablesByTemplateIdAndLanguageCodeId(
            Handle handle,
            Long templateId,
            Long languageCodeId) {
        Map<String, String> translationByVariableName = new HashMap<>();

        try (PreparedStatement stmt = handle.getConnection().prepareStatement(templateVariablesWithTranslationsQuery)) {
            stmt.setLong(1, templateId);
            stmt.setLong(2, languageCodeId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String variableName = rs.getString(SqlConstants.TemplateVariableTable.NAME);
                String variableTranslation = rs.getString(SqlConstants.TemplateVariableTable.SUBSTITUTION_VALUE);
                translationByVariableName.put(variableName, variableTranslation);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Could not fetch translated variables for the template with id "
                    + templateId, e);
        }

        return translationByVariableName;
    }

    Integer getNumberOfVariablesForTemplateId(Connection conn, Long templateId) {
        String templateVariablesCountForTemplateIdQuery
                = "select count(*) as variables_number from template_variable where template_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(templateVariablesCountForTemplateIdQuery)) {
            stmt.setLong(1, templateId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("variables_number");
            } else {
                throw new DaoException("Expected to get 1 row, but got nothing for the template with id " + templateId);
            }
        } catch (SQLException e) {
            throw new DaoException("Could not fetch translated variables for the template with id " + templateId, e);
        }
    }

}
