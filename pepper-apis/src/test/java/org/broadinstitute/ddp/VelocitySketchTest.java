package org.broadinstitute.ddp;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.sendgrid.Content;
import com.sendgrid.Mail;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.broadinstitute.ddp.client.SendGridClient;
import org.junit.BeforeClass;
import org.junit.Test;

public class VelocitySketchTest {

    static VelocityEngine velocityEngine = null;

    @BeforeClass
    public static void setUp() {
        velocityEngine = new VelocityEngine();
        // in production, we will probably use more flexible
        // ways to load macros, such as VelocityEngine.evaluate()
        Properties velocityProps = new Properties();
        velocityProps.put("file.resource.loader.path", "/sandbox/ddp-study-server/pepper-apis/src/test/resources");
        velocityEngine.init(velocityProps);
    }

    @Test
    public void testBio() {
        // example of a strongly typed, templated snippet
        System.out.println(new Bio("Fred Flinstone, MD").toVtl("v1", new Properties()));
    }

    /**
     * Render a template that does translations, but that
     * does not have personalized pepper context.  It's
     * just a document--we can version and cache this
     * with cdn.
     */
    @Test
    public void testTeam() throws Exception {
        Properties english = new Properties();
        english.put("team.header","The team!");
        english.put("team.text1", "Super awesome");

        List<Bio> members = new ArrayList<>();
        members.add(new Bio("Judge Sluggo"));
        members.add(new Bio("Mr. Bill"));
        PepperTemplate teamTemplate = new Team("team.header", members).toVtl("v1", english);

        System.out.println("Template without substitutions:");
        System.out.println(IOUtils.toString(teamTemplate.render()));
    }

    /**
     * Render a template that does translations
     * and does personalization.
     */
    @Test
    public void testEmail() throws Exception {
        // less structured example.  No need to make a java
        // wrapper if all we want is a more custom experience
        String templateLocation = "velocity/email_example.vm";
        Properties translation = new Properties();

        // in the real world, a property file for each project
        // could live as a blob in the db or in bucket storage.
        translation.put("greeting","Dear");
        translation.put("yourKitIsComing","You kit is coming soon!");
        TemplateLoader templateLoater = new TemplateLoader(templateLocation, translation);

        // here we will render the template with a single set of translations
        // but without personalization so that we can save the template
        // as a specific, versioned template that can
        // store as the source of truth.  Each time a template is created
        // or changed, or when a translation is updated, we would re-run
        // this rendering step, version the text output, and use
        // that as the source of truth for a template.
        String templateWithoutPepper = IOUtils.toString(templateLoater.loadTemplate(new VelocityContext()));

        System.out.println("---- template without pepper context.  save this as a versioned template! ----");
        System.out.println(templateWithoutPepper);

        // now we read that template and apply
        // the pepper context

        String templateWithPepper = IOUtils.toString(templateLoater.loadTemplate(new PepperContext().toContext()));
        System.out.println("----- template with pepper context.  fully rendered, this is served out to endpoints. -----");
        System.out.println(templateWithPepper);

        // send email like so and say goodbye to sendgrid templates
        // Mail.addContent(new Content("text/html", templateWithPepper));
    }

    /**
     * Builds a template for a specific version and
     * set of translations.
     */
    public interface Templateable {

        String getMacroName();
    }

    /**
     * Represents a template that might be fully renderable
     * without any context.
     */
    public interface PepperTemplate {

        Reader render(PepperContext pepperContext);

        Reader render();
    }

    public static class Team implements Templateable {

        private String headerKey;

        private List<Bio> members;

        private List<String> textKeys = new ArrayList<>();

        public String getMacroName() {
            return "team";
        }

        public Team(String headerKey, List<Bio> members) {
            this.headerKey = headerKey;
            this.members = members;
            textKeys.add("team.text1");
        }

        public List<Bio> getMembers() {
            return members;
        }

        public String getHeaderKey() {
            return headerKey;
        }

        public List<String> getTextKeys() {
            return textKeys;
        }

        public PepperTemplate toVtl(String version, Properties translatedValues) {
            String templateLocation = "velocity/" + getMacroName() + "_" + version + "_";
            StringWriter sw = new StringWriter();
            List macroLibs = new ArrayList();
            macroLibs.add(templateLocation + "macro.vm");
            // ugh, apparently we need to set this explicitly--import statements in the
            // macro itself don't seem to be followed
            macroLibs.add("velocity/bio_" + version + "_" + "macro.vm");
            return new PepperTemplateImpl(new TemplateLoader(this, version, translatedValues, macroLibs));

        }
    }

    public static class TemplateLoader {

        public static final String TRANSLATIONS_KEY = "t";

        private Properties translatedValues;

        private List templateLibraries = Collections.emptyList();

        private Templateable template;

        private Template velocityTemplate;

        private String contextVariableName;

        public TemplateLoader(String templateName, Properties translatedValues) {
            this.contextVariableName = templateName;
            velocityTemplate = velocityEngine.getTemplate(templateName);
            this.translatedValues = translatedValues;
        }

        public TemplateLoader(Templateable templateBuilder, String templateVersion, Properties translatedValues, List templateLibraries) {
            this.template = templateBuilder;
            this.translatedValues = translatedValues;
            this.templateLibraries = templateLibraries;

            String templateLocation = "velocity/" + template.getMacroName() + "_" + templateVersion + "_";
            velocityTemplate = velocityEngine.getTemplate(templateLocation + "snippet.vm");

            this.contextVariableName = template.getMacroName();
        }

        public Reader loadTemplate(VelocityContext context) {
            VelocityContext internalContext = new VelocityContext();
            internalContext.put(TRANSLATIONS_KEY, translatedValues);
            internalContext.put(contextVariableName, this.template);

            if (context.containsKey(contextVariableName)) {
                throw new RuntimeException("Internal context is already using " + this.template.getMacroName());
            }
            if (context.containsKey(TRANSLATIONS_KEY)) {
                throw new RuntimeException("Internal context is already using t");
            }

            for (Object key : context.internalGetKeys()) {
                internalContext.put(key.toString(), context.internalGet(key.toString()));
            }

            StringWriter sw = new StringWriter();
            velocityTemplate.merge(internalContext, sw, templateLibraries);
            return new StringReader(sw.toString());

        }
    }

    public static class PepperTemplateImpl implements PepperTemplate {

        private TemplateLoader templateLoader;

        public PepperTemplateImpl(TemplateLoader templateLoader) {
            this.templateLoader = templateLoader;
        }

        @Override
        public Reader render() {
            return templateLoader.loadTemplate(new VelocityContext());
        }

        @Override
        public Reader render(PepperContext pepperContext) {
            return templateLoader.loadTemplate(pepperContext.toContext());
        }

    }

    public static class PepperContext {

        // todo use annotations here instead of field names?
        public User operator = new User();

        public PepperContext() {
            // in the real world, this context could be built based on the operator/participant
            // guids that come in over http.  Here is a hack to give a sense of how this might work.
            operator.firstName = "Mary";

            // also, have the context has a pojo will open up options for doing "what does this look like?"
            // iterations in the WYSIWYG.  One could place fake values in
            // the context, run the template rendering, and see if it looks right.
        }

        public User getOperator() {
            return operator;
        }

        public VelocityContext toContext() {
            VelocityContext context = new VelocityContext();
            context.put("pepper", this);

            return context;
        }

        public static class User {

            public String firstName;

            public String getFirstName() {
                return firstName;
            }
        }
    }

    public static class Bio implements Templateable {

        public String getMacroName() {
            return "bio";
        }

        private String name;

        public Bio(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public PepperTemplate toVtl(String version, Properties translatedValues) {
            String templateLocation = "velocity/" + getMacroName() + "_" + version + "_";
            StringWriter sw = new StringWriter();
            List macroLibs = new ArrayList();
            macroLibs.add(templateLocation + "macro.vm");
            return new PepperTemplateImpl(new TemplateLoader(this, version, translatedValues, macroLibs));
        }
    }

    public class User {

        private String firstName;

        private String lastName;

        private String email;

        public User(String firstName, String lastName, String email) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
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
    }
}
