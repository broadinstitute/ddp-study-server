package org.broadinstitute.ddp.util;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.transformers.LocalDateAdapter;

public class GsonUtil {
    public static Gson standardGson() {
        return standardBuilder().create();
    }

    public static GsonBuilder standardBuilder() {
        return new GsonBuilder()
                .registerTypeAdapter(ActivityDef.class, new ActivityDef.Deserializer())
                .registerTypeAdapter(FormBlockDef.class, new FormBlockDef.Deserializer())
                .registerTypeAdapter(ComponentBlockDef.class, new ComponentBlockDef.Deserializer())
                .registerTypeAdapter(QuestionDef.class, new QuestionDef.Deserializer())
                .registerTypeAdapter(RuleDef.class, new RuleDef.Deserializer())
                .registerTypeAdapter(SectionIcon.class, new SectionIcon.Deserializer())
                .registerTypeAdapter(SectionIcon.class, new SectionIcon.Serializer())
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
                .registerTypeAdapter(TextQuestion.class, new TextQuestion.Serializer())
                .serializeNulls();
    }

    /**
     * Builds a standard gson, but using the given exclusions
     *
     * @param classAndFieldsExclusions Exclusions to apply
     */
    public static GsonBuilder standardBuilder(ClassAndFieldsExclusion... classAndFieldsExclusions) {
        return standardBuilder().setExclusionStrategies(new ClassAndFieldsExclusionStrategy(classAndFieldsExclusions));
    }

    /**
     * An exclusion that uses the declaring class and
     * a collection of the string values of {@link SerializedName} for
     * that class that should be ignored.  Used this if you want to selectively
     * suppress the serialization of a particular {@link SerializedName}-annotated
     * field in a particular class on a particular route.
     */
    public static class ClassAndFieldsExclusion {

        private final Collection<String> serializedNamesToIgnore = new HashSet<>();

        private final Class declaringClass;

        public ClassAndFieldsExclusion(Class declaringClass, Collection<String> serializedNamesToIgnore) {
            this.declaringClass = declaringClass;
            this.serializedNamesToIgnore.addAll(serializedNamesToIgnore);
        }

        public ClassAndFieldsExclusion(Class declaringClass, String... serializedNamesToIgnore) {
            this.declaringClass = declaringClass;
            this.serializedNamesToIgnore.addAll(Arrays.asList(serializedNamesToIgnore));
        }

        public Collection<String> getSerializedNamesToIgnore() {
            return serializedNamesToIgnore;
        }

        public Class getDeclaringClass() {
            return declaringClass;
        }
    }

    /**
     * Exclusion strategy that will ignore a collection of {@link ClassAndFieldsExclusion}s.
     */
    private static class ClassAndFieldsExclusionStrategy implements ExclusionStrategy {

        private ClassAndFieldsExclusion[] exclusions;

        public ClassAndFieldsExclusionStrategy(ClassAndFieldsExclusion... exclusions) {
            this.exclusions = exclusions;
        }

        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            boolean shouldExclude = false;
            for (ClassAndFieldsExclusion exclusion : exclusions) {
                if (exclusion.getDeclaringClass().equals(fieldAttributes.getDeclaringClass())) {
                    for (String fieldName : exclusion.getSerializedNamesToIgnore()) {
                        SerializedName serializedName = fieldAttributes.getAnnotation(SerializedName.class);
                        if (serializedName != null) {
                            if (fieldName.equals(serializedName.value())) {
                                shouldExclude = true;
                                break;
                            }
                        }
                    }
                }
            }
            return shouldExclude;
        }

        @Override
        public boolean shouldSkipClass(Class<?> klass) {
            return false;
        }
    }

    static public GsonBuilder serializeFieldsWithExposeAnnotation() {
        return standardBuilder().excludeFieldsWithoutExposeAnnotation();
    }
}
