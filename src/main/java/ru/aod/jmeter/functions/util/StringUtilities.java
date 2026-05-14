package ru.aod.jmeter.functions.util;

public class StringUtilities {
    private StringUtilities() {
    }

    public static int count(String input, char ch) {
        if (input.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = input.indexOf(ch, idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }


    public static boolean isBlank(CharSequence cs) {
        if (cs == null) {
            return true;
        }
        if (cs instanceof String s) {
            return s.isBlank(); // fast path
        }
        int strLen = cs.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

   public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

   public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.isEmpty();
    }

  public static boolean isNotEmpty(CharSequence cs) {
        return !isEmpty(cs);
    }

  public static String replaceChars(String str, String searchChars, String replaceChars) {
        if (StringUtilities.isEmpty(str) || StringUtilities.isEmpty(searchChars)) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int index = searchChars.indexOf(c);
            if (index < 0) {
                sb.append(c);
            } else {
                if (replaceChars != null && index < replaceChars.length()) {
                    sb.append(replaceChars.charAt(index));
                }
                // If replaceChars is null or index is out of bounds, character is deleted
            }
        }
        return sb.toString();
    }

    public static String trimToNull(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String capitalize(String str) {
        if (StringUtilities.isEmpty(str)) {
            return str;
        }
        int firstCodepoint = str.codePointAt(0);
        int titleCaseCodepoint = Character.toTitleCase(firstCodepoint);

        if (firstCodepoint == titleCaseCodepoint) {
            // No change needed
            return str;
        }

        StringBuilder sb = new StringBuilder(str.length());
        sb.appendCodePoint(titleCaseCodepoint);
        sb.append(str, Character.charCount(firstCodepoint), str.length());
        return sb.toString();
    }

    public static String strip(String str, String stripChars) {
        if (StringUtilities.isEmpty(str)) {
            return str;
        }
        int start = 0;
        int end = str.length();

        // Strip from start
        while (start < end && stripChars.indexOf(str.charAt(start)) >= 0) {
            start++;
        }

        // Strip from end
        while (start < end && stripChars.indexOf(str.charAt(end - 1)) >= 0) {
            end--;
        }

        return str.substring(start, end);
    }

   public static boolean isNumeric(String str) {
        if (isEmpty(str)) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAllLowerCase(CharSequence string) {
        if (string == null || string.isEmpty()) {
            return false;
        }
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (!Character.isLowerCase(string.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAllUpperCase(CharSequence string) {
        if (string == null || string.isEmpty()) {
            return false;
        }
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (!Character.isUpperCase(string.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}