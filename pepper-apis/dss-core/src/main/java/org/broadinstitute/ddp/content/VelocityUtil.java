package org.broadinstitute.ddp.content;

import static org.apache.commons.lang3.StringUtils.contains;
import static org.broadinstitute.ddp.content.I18nTemplateConstants.DDP;
import static org.broadinstitute.ddp.util.PropertiesToMapTreeUtil.propertiesToMap;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.Token;
import org.apache.velocity.runtime.parser.node.ASTReference;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.apache.velocity.runtime.visitor.BaseVisitor;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;

/**
 * Utility methods used during Velocity {@link Template} rendering.
 * It helps to render the templates where used Velocity variables which names contain '.'-symbol (this symbol
 * is used in order to make a variable equal to a corresponding translation name if name is a nested in a section,
 * for example: 'prequal.name'). It needs to define Velocity variables with same names
 * as translations references (without prefix `i18n.xx' where xx - langCde).
 * For example, in StudyBuilder conf file the translations defined in files en.conf, es.conf.. like:
 * <pre>
 * "prequal": {
 *   "name": "Prequalifier Survey",
 *   "title": "Lets get started",
 *   ...
 * </pre>
 * In other conf files a translation can be referenced like:
 * <pre>
 *     "templateText": "$prompt"
 *     ...
 *     "variables": [
 *        {
 *           "name": "prompt",
 *            "translations": [
 *             { "language": "en", "text": ${i18n.en.prequal.name} }
 *            ]
 *        }
 *     ]
 * </pre>
 * Translations references automatic resolving allows not to specify section "variables" [..]
 * but instead it needs to specify translations references as variables names in a templateText.
 * For example:
 * <pre>
 *    "templateText": "$prequal.name"
 * </pre>
 * And during study data generation (to be saved to DB) the variables and translation references will be generated
 * automatically for all defined languages (because a translation reference (without "i18n.xx") coincides with a variable name).
 */
public class VelocityUtil {

    /**
     * Template fake name
     */
    public static final String TEMPLATE_NAME = "template_name";

    /**
     * Velocity variable prefix (specified in a velocity template (in {@link Template#getTemplateText()})
     */
    public static final char VARIABLE_PREFIX = '$';
    /**
     * Velocity variable separator (in order to support translations references automatic resolving the Velocity
     * variables should be equal to translation references (which could contain dots, for example: `prequal.name`)
     */
    public static final char VARIABLE_SEP = '.';

    /**
     * Detect list of Velocity variables (with removing '$' from them) defined in a specified
     * {@link Template#getTemplateText()}.<br>
     * This method is used by StudyBuilder during {@link TemplateVariable} translations' references automatic generation.
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
    public static List<String> extractVelocityVariablesFromTemplate(String templateText) {
        try {
            RuntimeInstance ri = new RuntimeInstance();
            // todo arz double check
            SimpleNode node = ri.parse(new StringReader(templateText), Velocity.getTemplate(TEMPLATE_NAME));
            TemplateParserVisitor visitor = new TemplateParserVisitor();
            visitor.visit(node, null);
            return visitor.getVariables();
        } catch (ParseException e) {
            throw new DDPException("Error parse template: " + templateText, e);
        }
    }

    /**
     * Converts properties to hierarchy of maps: properties names splitted into parts which separated by '.'
     * and loaded to tree of maps and the leaves of the tree - variable values.
     * Such Map tree will be loaded to a Velocity context to evaluate variables.
     *
     * <p>For example a template:
     * <pre>
     *     "Sections: $prequal.sect1.name, $prequal.sect2.name"
     * </pre>
     * and variables:
     * <pre>
     * "prequal.sect1.name" = "Sect_One"
     * "prequal.sect2.name" = "Sect_Two"
     * </pre>
     */
    public static Map<String, Object> convertVariablesWithCompoundNamesToMap(Map<String, Object> variables) {
        return variables != null && variables.keySet().stream().anyMatch(k -> contains(k, VARIABLE_SEP))
                ? propertiesToMap(variables) : variables;
    }

    /**
     * Used for Velocity templates parsing.
     * When a variable is encountered and parsed - it is added to
     * a result array `variables`.
     * NOTE: variables which are started with '$ddp.' are ignored.
     */
    static class TemplateParserVisitor extends BaseVisitor {

        private List<String> variables = new ArrayList<>();

        @Override
        public Object visit(final ASTReference node, final Object data) {
            StringBuilder var = new StringBuilder();
            Token t = node.getFirstToken();
            var.append(t);
            while (!t.equals(node.getLastToken())) {
                t = t.next;
                var.append(t);
            }
            String variable = var.toString();
            if (!variable.startsWith(VARIABLE_PREFIX + DDP)) {
                variables.add(variable.substring(1));
            }
            return null;
        }

        public List<String> getVariables() {
            return variables;
        }
    }
}
