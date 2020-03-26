package org.broadinstitute.ddp.model.study;

import java.util.Set;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.exception.DDPException;

public class PasswordPolicy {

    public static final int DEFAULT_MAX_REPEATED_CHARS = 2;
    public static final int DEFAULT_MIN_CHAR_CLASSES = 3;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private transient PolicyType type;
    @SerializedName("minimumLength")
    private int minLength;
    @SerializedName("minimumCharacterClasses")
    private int minCharClasses;
    @SerializedName("characterClasses")
    private Set<CharClass> charClasses;
    @SerializedName("maximumRepeatedCharacters")
    private Integer maxRepeatedChars;

    public static PasswordPolicy fromType(PolicyType type, Integer minLength) {
        switch (type) {
            case NONE:
                return none(minLength);
            case LOW:
                return low(minLength);
            case FAIR:
                return fair(minLength);
            case GOOD:
                return good(minLength);
            case EXCELLENT:
                return excellent(minLength);
            default:
                throw new DDPException("Unhandled password policy type " + type);
        }
    }

    public static PasswordPolicy none(Integer minLength) {
        minLength = (minLength != null ? minLength : PolicyType.NONE.getDefaultMinPasswordLength());
        return new PasswordPolicy(PolicyType.NONE, minLength, 0, CharClasses.empty(), null);
    }

    public static PasswordPolicy low(Integer minLength) {
        minLength = (minLength != null ? minLength : PolicyType.LOW.getDefaultMinPasswordLength());
        return new PasswordPolicy(PolicyType.LOW, minLength, 0, CharClasses.empty(), null);
    }

    public static PasswordPolicy fair(Integer minLength) {
        minLength = (minLength != null ? minLength : PolicyType.FAIR.getDefaultMinPasswordLength());
        return new PasswordPolicy(PolicyType.FAIR, minLength, DEFAULT_MIN_CHAR_CLASSES, CharClasses.alphanumeric(), null);
    }

    public static PasswordPolicy good(Integer minLength) {
        minLength = (minLength != null ? minLength : PolicyType.GOOD.getDefaultMinPasswordLength());
        return new PasswordPolicy(PolicyType.GOOD, minLength, DEFAULT_MIN_CHAR_CLASSES, CharClasses.all(), null);
    }

    public static PasswordPolicy excellent(Integer minLength) {
        minLength = (minLength != null ? minLength : PolicyType.EXCELLENT.getDefaultMinPasswordLength());
        return new PasswordPolicy(PolicyType.EXCELLENT, minLength, DEFAULT_MIN_CHAR_CLASSES, CharClasses.all(), DEFAULT_MAX_REPEATED_CHARS);
    }

    private PasswordPolicy(PolicyType type, int minLength, int minCharClasses, Set<CharClass> charClasses, Integer maxRepeatedChars) {
        this.type = type;
        this.minLength = minLength;
        this.minCharClasses = minCharClasses;
        this.charClasses = charClasses;
        this.maxRepeatedChars = maxRepeatedChars;
    }

    public PolicyType getType() {
        return type;
    }

    public int getMinLength() {
        return minLength;
    }

    public Integer getMinCharClasses() {
        return minCharClasses;
    }

    public Set<CharClass> getCharClasses() {
        return Set.copyOf(charClasses);
    }

    public Integer getMaxRepeatedChars() {
        return maxRepeatedChars;
    }

    public enum PolicyType {
        NONE(1), LOW(6), FAIR(8), GOOD(8), EXCELLENT(10);

        // If there's no min length set, then use this default.
        private final int defaultMinPasswordLength;

        PolicyType(int defaultMinPasswordLength) {
            this.defaultMinPasswordLength = defaultMinPasswordLength;
        }

        public int getDefaultMinPasswordLength() {
            return defaultMinPasswordLength;
        }
    }

    public enum CharClass {
        UPPER, LOWER, NUMBER, SPECIAL
    }

    public static class CharClasses {
        public static Set<CharClass> all() {
            return Set.of(CharClass.UPPER, CharClass.LOWER, CharClass.NUMBER, CharClass.SPECIAL);
        }

        public static Set<CharClass> alphanumeric() {
            return Set.of(CharClass.UPPER, CharClass.LOWER, CharClass.NUMBER);
        }

        public static Set<CharClass> empty() {
            return Set.of();
        }

        private CharClasses() {
        }
    }
}
