package org.broadinstitute.ddp.content;

import static org.apache.commons.lang3.StringUtils.contains;
import static org.broadinstitute.ddp.content.VelocityUtil.VARIABLE_PREFIX;

import java.io.StringWriter;
import java.util.Properties;
import java.util.TimeZone;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;

@Slf4j
public class I18nContentRenderer {
    private static final String TEMPLATE_NAME = "template";

    private static Long defaultLangId = null;

    private final VelocityEngine engine;

    private static Long getDefaultLanguageId() {
        if (defaultLangId == null) {
            defaultLangId = LanguageStore.getDefault().getId();
        }

        return defaultLangId;
    }

    public static RenderValueProvider newValueProvider(Handle handle,
                                                       long activityInstanceId,
                                                       long participantUserId,
                                                       String operatorGuid,
                                                       String studyGuid) {
        return newValueProvider(handle, activityInstanceId, participantUserId, operatorGuid, studyGuid, new HashMap<>());
    }

    public static RenderValueProvider newValueProvider(Handle handle,
                                       long activityInstanceId,
                                       long participantUserId, String operatorGuid, String studyGuid,
                                       Map<String, String> snapshot) {
        var builder = newValueProviderBuilder(handle, activityInstanceId, participantUserId, operatorGuid, studyGuid);

        // If there are saved snapshot substitution values, override with those so final rendered
        // content will be consistent with what user last saw when snapshot was taken.
        builder.withSnapshot(snapshot);

        return builder.build();
    }

    public static RenderValueProvider.Builder newValueProviderBuilder(Handle handle,
                                                                      long activityInstanceId,
                                                                      long participantUserId, String operatorGuid, String studyGuid) {

        var builder = new RenderValueProvider.Builder();

        Optional<User> user = handle.attach(UserDao.class).findUserById(participantUserId);
        if (user.isPresent()) {
            builder.setParticipantGuid(user.get().getGuid());
            builder.setGovernedParticipant(handle.attach(UserGovernanceDao.class)
                    .isGovernedParticipant(user.get().getGuid(), operatorGuid, studyGuid));
        }

        UserProfile profile = handle.attach(UserProfileDao.class)
                .findProfileByUserId(participantUserId)
                .orElse(null);

        ZoneId zone = TimeZone.getTimeZone("US/Central").toZoneId();
        if (profile != null) {
            if (profile.getFirstName() != null) {
                builder.setParticipantFirstName(profile.getFirstName());
            }
            if (profile.getLastName() != null) {
                builder.setParticipantLastName(profile.getLastName());
            }
            if (profile.getBirthDate() != null) {
                builder.setParticipantBirthDate(profile.getBirthDate());
            }
            if (profile.getTimeZone() != null) {
                zone = profile.getTimeZone();
            }
        }

        builder.setParticipantTimeZone(zone);
        builder.setDate(LocalDate.now(zone));

        builder.setFirstCompletedDate(handle.attach(ActivityInstanceDao.class)
                .findBaseResponseByInstanceId(activityInstanceId)
                .map(ActivityResponse::getFirstCompletedAt)
                .map(Instant::ofEpochMilli)
                .map(x -> x.atZone(TimeZone.getTimeZone("US/Central").toZoneId()))
                .map(ZonedDateTime::toLocalDate)
                .orElse(null));

        return builder;
    }

    public I18nContentRenderer() {
        engine = createVelocityEngine();
    }

    /**
     * Creates a VelocityEngine object responsible for rendering templates and sets certain properties.
     *
     * @return An initialized VelocityEngine instance
     */
    private VelocityEngine createVelocityEngine() {
        Properties velocityProps = new Properties();
        velocityProps.setProperty("parser.allow_hyphen_in_identifiers", "true");
        VelocityEngine engine = new VelocityEngine();
        engine.init(velocityProps);
        return engine;
    }

    /**
     * Renders the template content into final html. Uses a default fallback language.
     *
     * @param handle            JDBC connection
     * @param contentTemplateId Id of the template to render
     * @param languageCodeId    Language to fetch translations for
     * @param timestamp         the timestamp to pinpoint template variable substitution text revisions
     * @return A string containting the template rendered into html
     * @throws IllegalArgumentException Thrown when one of the method arguments is null
     * @throws NoSuchElementException   Thrown when a db search returns no element
     */
    public String renderContent(Handle handle, Long contentTemplateId, Long languageCodeId, long timestamp) {
        return renderContent(handle, contentTemplateId, languageCodeId, getDefaultLanguageId(), timestamp);
    }

    /**
     * Render look up template from from givent template Id and render it applying variable map values given followed up
     * by the language specific variables associated with the template.
     *
     * @param handle            JDBC connection
     * @param templateId        Id of the template to render
     * @param languageCodeId    The language to look for
     * @param varNameToValueMap Map of variable names to values to be applied to template
     * @param timestamp         the timestamp to pinpoint template variable substitution text revisions
     * @return the rendered template
     * @throws IllegalArgumentException Thrown when one of the method arguments is null
     * @throws NoSuchElementException   Thrown when a db search returns no element
     */
    public String renderContent(Handle handle, Long templateId, Long languageCodeId,
                                Map<String, ?> varNameToValueMap, long timestamp) {
        Map<String, String> varNameToString = new HashMap<>();
        for (Map.Entry<String, ?> entry : varNameToValueMap.entrySet()) {
            varNameToString.put(entry.getKey(), convertToString(entry.getValue()));
        }
        return render(handle, templateId, languageCodeId, getDefaultLanguageId(), varNameToString, timestamp);
    }

    /**
     * Renders the template content into final html.
     *
     * @param handle                JDBC connection
     * @param contentTemplateId     Id of the template to render
     * @param languageCodeId        Language to fetch translations for
     * @param defaultLanguageCodeId The fallback language if no translations are found for the languageCodeId
     * @param timestamp             the timestamp to pinpoint template variable substitution text revisions
     * @return A string containing the template rendered into html
     * @throws IllegalArgumentException Thrown when one of the method arguments is null
     * @throws NoSuchElementException   Thrown when a db search returns no element
     */
    public String renderContent(Handle handle, Long contentTemplateId, Long languageCodeId, Long defaultLanguageCodeId, long timestamp) {
        return render(handle, contentTemplateId, languageCodeId, defaultLanguageCodeId, Collections.emptyMap(), timestamp);
    }

    private String render(Handle handle, Long contentTemplateId, Long languageCodeId, Long defaultLanguageCodeId,
                          Map<String, String> varNameToValueMap, long timestamp) {

        if (contentTemplateId == null) {
            throw new IllegalArgumentException("contentTemplateId cannot be null");
        }

        if (languageCodeId == null) {
            throw new IllegalArgumentException("languageCodeId cannot be null");
        }

        TemplateDao templateDao = handle.attach(TemplateDao.class);
        Optional<TemplateDao.TextAndVarCount> res = templateDao.findTextAndVarCountById(contentTemplateId);
        if (res.isEmpty() || res.get().getText() == null) {
            throw new NoSuchElementException("could not find template with id " + contentTemplateId);
        }
        String templateText = res.get().getText();
        int templateVariableCount = res.get().getVarCount();

        Map<String, String> translatedTemplateVariables = null;
        List<Long> languageCodeIds = Arrays.asList(languageCodeId, defaultLanguageCodeId);
        boolean allVariablesTranslated = false;

        for (Long langCodeId : languageCodeIds) {
            log.info("Fetching translations for the variables with templateId " + contentTemplateId);
            translatedTemplateVariables = templateDao
                    .findAllTranslatedVariablesByIds(Set.of(contentTemplateId), langCodeId, null, timestamp)
                    .getOrDefault(contentTemplateId, new HashMap<>());
            int translatedTemplateVariablesCount = translatedTemplateVariables.size();
            if (templateVariableCount == translatedTemplateVariablesCount) {
                log.info("Found all required translations for variables with templateId " + contentTemplateId);
                allVariablesTranslated = true;
                break;
            } else if (templateVariableCount != translatedTemplateVariablesCount) {
                if (translatedTemplateVariablesCount == 0) {
                    log.warn("No translations for the language with id "
                            + langCodeId + ", trying the next language");
                } else {
                    String errMsg = "The template with id " + contentTemplateId + " is only partially translated: "
                            + templateVariableCount + " variable(s) exist while only "
                            + translatedTemplateVariablesCount + " are translated, " + "trying the next language";
                    log.warn(errMsg);
                }
            }
        }

        if (!allVariablesTranslated) {
            log.warn("The template with id " + contentTemplateId
                    + " is not completely translated, some variables won't be substituted!");
        }

        Map<String, Object> allVariables = new HashMap<>(varNameToValueMap);
        if (translatedTemplateVariables != null) {
            allVariables.putAll(translatedTemplateVariables);
        }

        return renderToString(templateText, allVariables);
    }

    /**
     * Given the language, render all of the templates.
     *
     * @param handle      the database handle
     * @param templateIds the set of template ids
     * @param langCodeId  the language code id
     * @param timestamp   the timestamp to pinpoint template variable substitution text revisions
     * @return mapping of template id to its rendered template string
     * @throws NoSuchElementException when any one of the templates is not found
     */
    public Map<Long, String> bulkRender(Handle handle, Set<Long> templateIds, long langCodeId,
                                        Map<String, Object> context, long timestamp) {
        if (templateIds == null || templateIds.isEmpty()) {
            return new HashMap<>();
        }

        TemplateDao tmplDao = handle.attach(TemplateDao.class);
        Map<Long, TemplateDao.TextAndVarCount> templateData = tmplDao.findAllTextAndVarCountsByIds(templateIds);
        Map<Long, Map<String, String>> variables = tmplDao
                .findAllTranslatedVariablesByIds(templateIds, langCodeId, getDefaultLanguageId(), timestamp);
        Map<Long, String> rendered = new HashMap<>(templateIds.size());

        for (long templateId : templateIds) {
            TemplateDao.TextAndVarCount data = templateData.get(templateId);
            if (data == null || data.getText() == null) {
                throw new NoSuchElementException("Could not find template text with id " + templateId);
            }

            Map<String, Object> vars = new HashMap<>(variables.getOrDefault(templateId, new HashMap<>()));
            if (vars.size() != data.getVarCount()) {
                log.warn("Not all variables will be translated for template id {} (found {}/{})",
                        templateId, vars.size(), data.getVarCount());
            }

            // Var count is the count of explicitly declared variables in template,
            // which excludes special vars. So we add context here after the count check.
            vars.putAll(context);

            rendered.put(templateId, renderToString(data.getText(), vars));
        }

        return rendered;
    }

    public Map<Long, String> bulkRender(Handle handle, Set<Long> templateIds, long langCodeId, long timestamp) {
        return bulkRender(handle, templateIds, langCodeId, Collections.emptyMap(), timestamp);
    }

    /**
     * Render all the templates for, and apply them to, the given collection.
     *
     * @param handle      the database handle
     * @param renderables the collection of things to render
     * @param style       the content style to format rendered templates
     * @param langCodeId  the language code id
     * @param timestamp   the timestamp to pinpoint template variable substitution text revisions
     * @param <T>         the type of object that needs rendering
     */
    public <T extends Renderable> void bulkRenderAndApply(Handle handle, Collection<T> renderables,
                                                          ContentStyle style, long langCodeId, long timestamp) {
        Set<Long> templateIds = new HashSet<>();

        for (Renderable renderable : renderables) {
            renderable.registerTemplateIds(templateIds::add);
        }

        Map<Long, String> rendered = bulkRender(handle, templateIds, langCodeId, timestamp);
        Renderable.Provider<String> provider = rendered::get;

        for (Renderable renderable : renderables) {
            renderable.applyRenderedTemplates(provider, style);
        }
    }

    /**
     * Render Velocity template (resolving Variables specified in parameter `context`).<br>
     * NOTE: this version of the method supports a new feature `translations' references automatic generation`
     * which means that Velocity variables can contain dots ('.') in it's names.
     * Therefore right before loading to Velocity context the Variables' names are converted: '.' replaced to '-'
     * (except $ddp.) - in both context map and template text. This is done with help of {@link VelocityUtil} methods.
     *
     * @param template  template text (stored in {@link Template#getTemplateText()}
     * @param context   map with Velocity variables to be loaded to Velocity context
     * @return a string with rendered template text
     */
    public String renderToString(String template, Map<String, Object> context) {
        VelocityContext ctx = new VelocityContext(context);
        StringWriter writer = new StringWriter();
        engine.evaluate(ctx, writer, TEMPLATE_NAME, template);
        String result = writer.toString();
        if (contains(result, VARIABLE_PREFIX)) {
            // Here we have a second pass in case of variables in the substitution values,
            // e.g. participantName() with locale-dependent position in the sentence.
            writer = new StringWriter();
            engine.evaluate(ctx, writer, TEMPLATE_NAME, result);
            result = writer.toString();
        }
        return result;
    }

    public static String convertToString(Object obj) {
        //todo can make this conversion a little more sophisticated, e.g. different date formats for different locales or langCodes
        if (obj instanceof LocalDate) {
            return DateTimeFormatter.ofPattern("MMMM dd, yyyy").format((LocalDate) obj);
        } else {
            return obj != null ? obj.toString() : null;
        }
    }
}
