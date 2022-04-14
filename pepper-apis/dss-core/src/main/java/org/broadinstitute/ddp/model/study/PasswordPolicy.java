package org.broadinstitute.ddp.model.study;

import java.util.HashSet;
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

    public int getMinCharClasses() {
        return minCharClasses;
    }

    public Set<CharClass> getCharClasses() {
        return Set.copyOf(charClasses);
    }

    public Integer getMaxRepeatedChars() {
        return maxRepeatedChars;
    }

    /**
     * Check if given password satisfies this password policy.
     *
     * @param password the password
     * @return true if satisfied, otherwise false
     */
    public boolean checkPassword(String password) {
        if (password.length() < minLength) {
            return false;
        }
        if (minCharClasses > 0) {
            Set<CharClass> pwdCharClasses = classifyPassword(password);
            pwdCharClasses.retainAll(charClasses); // Set intersection
            int numClasses = pwdCharClasses.size();
            if (numClasses < minCharClasses) {
                return false;
            }
        }
        if (maxRepeatedChars != null) {
            int maxRepeated = countMaxConsecutiveRepeatedChars(password);
            if (maxRepeated > maxRepeatedChars) {
                return false;
            }
        }
        return true;
    }

    private Set<CharClass> classifyPassword(String password) {
        int max = CharClass.values().length;
        Set<CharClass> charClasses = new HashSet<>();
        for (var ch : password.toCharArray()) {
            if (Character.isDigit(ch)) {
                charClasses.add(CharClass.NUMBER);
            } else if (Character.isUpperCase(ch)) {
                charClasses.add(CharClass.UPPER);
            } else if (Character.isLowerCase(ch)) {
                charClasses.add(CharClass.LOWER);
            } else {
                // Auth0 doesn't say what special chars are, so assume that's what is left.
                charClasses.add(CharClass.SPECIAL);
            }
            if (charClasses.size() == max) {
                break; // Already have all char classes, no need to classify the rest.
            }
        }
        return charClasses;
    }

    private int countMaxConsecutiveRepeatedChars(String password) {
        char[] chars = password.toCharArray();
        int maxLen = 0;
        int curr = 0;
        while (curr < chars.length) {
            char ch = chars[curr];
            int j = curr + 1;
            while (j < chars.length && chars[j] == ch) {
                j++;
            }
            int len = j - curr;
            if (len > maxLen) {
                maxLen = len;
            }
            curr = j; // Can skip over contiguous sequence since any further counts will be less.
        }
        return maxLen;
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
