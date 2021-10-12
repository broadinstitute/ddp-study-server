package org.broadinstitute.ddp.content;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static org.apache.commons.lang3.StringUtils.contains;
import static org.broadinstitute.ddp.content.I18nTemplateConstants.DDP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;

/**
 * Utility methods used during Velocity {@link Template} rendering.
 * It helps to render the templates where used Velocity variables containing '.'-symbol (this symbol
 * is used in order to support translations references automatic resolving: as soon as this feature
 * enabled in the study conf-files (in StudyBuilder) needs to define Velocity variables with same names
 * as translations references (without prefix `i18n.xx' where xx - langCde).
 */
public class VelocityUtil {

    /**
     * Velocity variable prefix (specified in a velocity template (in {@link Template#getTemplateText()})
     */
    public static final char VARIABLE_PREFIX = '$';
    /**
     * Velocity variable separator (in order to support translations references automatic resolving the Velocity
     * variables should be equal to translation references (which could contain dots, for example: `prequal.name`)
     */
    public static final char VARIABLE_SEP = '.';

    /** Character used to escape special characters in Velocity template */
    public static final char ESCAPE_PREFIX = '\\';

    /**
     * Detect list of Velocity variables (with removing '$' from them) defined in a specified Template.<br>
     * This method is used by StudyBuilder during translations' references automatic generation.
     *
     * <p><b>Example:</b>
     * <pre>
     *   "templateText": """<div class="PageContent-box">
     *             <p class="PageContent-box-text">$prequal.intro_thank_you</p>
     *             <p class="PageContent-box-text">$prequal.intro_auto_save</p></div>""",
     *
     *   From this template text will be extracted variables:
     *      'prequal.intro_thank_you',
     *      'prequal.intro_auto_save'.
     * </pre>
     *
     * @param templateText template text where to find Velocity variables
     * @return list of String - list of variables
     */
    public static List<String> detectVelocityVariablesFromTemplate(String templateText) {
        List<String> velocityVariables = new ArrayList<>();
        if (templateText != null) {
            boolean varStarted = false;
            char ch;
            char prevCh = '\0';
            StringBuilder variable = new StringBuilder();
            for (int i = 0; i < templateText.length(); i++) {
                ch = templateText.charAt(i);
                if (ch == VARIABLE_PREFIX && prevCh != ESCAPE_PREFIX) {
                    varStarted = true;
                    variable.setLength(0);
                } else {
                    if (varStarted) {
                        if (ch == VARIABLE_SEP && DDP.equals(variable.toString())) {
                            varStarted = false;
                            variable.setLength(0);
                        }
                        if (isVariableValidChar(ch)) {
                            if (prevCh == VARIABLE_SEP && ch == VARIABLE_SEP) {
                                throw new RuntimeException(format(
                                        "Invalid Velocity variable $%s in template $s", variable, templateText));
                            } else {
                                variable.append(ch);
                            }
                        } else {
                            if (prevCh == VARIABLE_SEP) {
                                variable.setLength(variable.length() - 1);
                            }
                            addVariableToList(velocityVariables, variable, templateText);
                            varStarted = false;
                        }
                    }
                }
                prevCh = ch;
            }
            if (variable.length() > 0) {
                if (variable.charAt(variable.length() - 1) == VARIABLE_SEP) {
                    variable.setLength(variable.length() - 1);
                }
                velocityVariables.add(variable.toString());
            }
        }
        return velocityVariables;
    }

    public static Map<String, Object> convertNestedVariablesToMap(Map<String, Object> context) {
        if (context != null && context.keySet().stream().anyMatch(k -> contains(k, VARIABLE_SEP))) {
            Map<String, Object> convertedContext = new HashMap<>();
            context.forEach((k, v) -> {
                if (contains(k, VARIABLE_SEP)) {
                    String[] splitted = StringUtils.split(k, VARIABLE_SEP);
                    Object obj = convertedContext.get(splitted[0]);
                    Map<String, String> translations;
                    if (obj != null) {
                        if (!(obj instanceof Map)) {
                            throw new DDPException("Velocity context contains invalid variable " + splitted[0]);
                        }
                        translations = (Map<String, String>)obj;
                    } else {
                        convertedContext.put(splitted[0], new HashMap<>());
                        translations = (Map<String, String>)convertedContext.get(splitted[0]);
                    }
                    translations.put(splitted[1], valueOf(v));
                } else {
                    convertedContext.put(k, v);
                }
            });
            return convertedContext;
        }
        return context;
    }

    private static void addVariableToList(List<String> velocityVariables, StringBuilder variable, String templateText) {
        if (variable.length() == 0) {
            throw new RuntimeException(format("Invalid Velocity variable $%s in template $s", variable, templateText));
        }
        velocityVariables.add(variable.toString());
        variable.setLength(0);
    }

    /**
     * Detect if character is a valid Velocity character.
     */
    private static boolean isVariableValidChar(char ch) {
        return Character.isLetter(ch) || Character.isDigit(ch) || ch == '_' || ch == '.' || ch == '-';
    }
}
