package org.broadinstitute.ddp.content;

import static java.lang.String.format;
import static org.broadinstitute.ddp.content.I18nTemplateConstants.DDP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;


public class VelocityUtil {

    public static final char VARIABLE_PREFIX = '$';
    public static final char VARIABLE_SEP = '.';
    public static final char CONVERTED_SEP = '-';
    public static final char ESCAPE_PREFIX = '\\';

    /**
     * Detect list of Velocity variables (with removing '$' from them) defined in a specified Template.<br>
     * Example:
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

    /**
     * Comvert variables in templateText: replace '.' to '-'.
     */
    public static String convertTemplateVariablesToValid(String templateText, Collection<String> variables) {
        if (templateText != null) {
            StringBuilder convertedTemplateText = new StringBuilder(templateText.length());
            boolean varStarted = false;
            char ch;
            char prevCh = '\0';
            StringBuilder variable = new StringBuilder();
            for (int i = 0; i < templateText.length(); i++) {
                ch = templateText.charAt(i);
                if (ch == VARIABLE_PREFIX && prevCh != ESCAPE_PREFIX) {
                    varStarted = true;
                    addVariableToText(variable, convertedTemplateText, variables);
                    convertedTemplateText.append(ch);
                } else {
                    if (varStarted) {
                        if (ch == VARIABLE_SEP && !DDP.equals(variable.toString())) {
                            if (i < templateText.length() - 1 && isVariableValidChar(templateText.charAt(i + 1))) {
                                ch = CONVERTED_SEP;
                            }
                        }
                        if (!isVariableValidChar(ch)) {
                            varStarted = false;
                            addVariableToText(variable, convertedTemplateText, variables);
                        } else {
                            variable.append(ch);
                        }
                    }
                    if (!varStarted) {
                        convertedTemplateText.append(ch);
                    }
                }
            }
            addVariableToText(variable, convertedTemplateText, variables);
            return convertedTemplateText.toString();
        }
        return null;
    }

    /**
     * Comvert variables' names: replace '.' to '-'.
     */
    public static final Map<String, Object> convertVariablesToValid(Map<String, Object> context) {
        if (context != null) {
            if (context.keySet().stream().anyMatch(k -> k.indexOf(VARIABLE_SEP) != -1)) {
                Map<String, Object> convertedMap = new HashMap<>();
                context.forEach((k, v) -> {
                    convertedMap.put(
                            k.indexOf(VARIABLE_SEP) == -1 ? k : StringUtils.replaceChars(k, VARIABLE_SEP, CONVERTED_SEP), v);
                });
                return convertedMap;
            }
        }
        return context;
    }

    private static void addVariableToText(StringBuilder variable, StringBuilder convertedTemplateText, Collection<String> variables) {
        if (variable.length() > 0 && variables.contains(variable)) {
            variable.replace(0, variable.length(),
                    StringUtils.replaceChars(variable.toString(), VARIABLE_SEP, CONVERTED_SEP));
        }
        if (variable.length() > 0) {
            convertedTemplateText.append(variable);
            variable.setLength(0);
        }
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
