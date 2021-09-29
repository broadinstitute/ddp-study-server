package org.broadinstitute.ddp.studybuilder.translation;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.template.Template;

/**
 * Contains methods used for Velocity templates processing
 */
public class VelocityUtil {

    public static final char VARIABLE_PREFIX = '$';
    public static final char VARIABLE_SEP = '.';
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
     * @param template template where to find Velocity variables
     * @return list of String - list of variables
     */
    public static List<String> detectVelocityVariablesFromTemplate(Template template) {
        List<String> velocityVariables = new ArrayList<>();
        if (template != null) {
            String text = template.getTemplateText();
            boolean varStarted = false;
            char ch;
            char prevCh = '\0';
            StringBuilder variable = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                ch = text.charAt(i);
                if (ch == VARIABLE_PREFIX && prevCh != ESCAPE_PREFIX) {
                    varStarted = true;
                    variable.setLength(0);
                } else {
                    if (varStarted) {
                        if (isVariableValidChar(ch)) {
                            if (prevCh == VARIABLE_SEP && ch == VARIABLE_SEP) {
                                throw new RuntimeException(format(
                                    "Invalid Velocity variable $%s in template $s", variable, template.getTemplateText()));
                            } else {
                                variable.append(ch);
                            }
                        } else {
                            if (prevCh == VARIABLE_SEP) {
                                variable.setLength(variable.length() - 1);
                            }
                            addVariable(velocityVariables, variable, template.getTemplateText());
                            varStarted = false;
                        }
                    }
                }
                prevCh = ch;
            }
            if (variable.length() > 0) {
                velocityVariables.add(variable.toString());
            }
        }
        return velocityVariables;
    }

    private static void addVariable(List<String> velocityVariables, StringBuilder variable, String templateText) {
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
