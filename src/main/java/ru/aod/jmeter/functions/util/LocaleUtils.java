package ru.aod.jmeter.functions.util;


import java.util.Locale;

public class LocaleUtils {

    private static final char UNDERSCORE = '_';

    private static final char DASH = '-';

    private LocaleUtils() {}


    private static boolean isISO639LanguageCode(final String str) {
        return StringUtilities.isAllLowerCase(str) && (str.length() == 2 || str.length() == 3);
    }

    private static boolean isISO3166CountryCode(final String str) {
        return str.length() == 2 && StringUtilities.isAllUpperCase(str);
    }


    private static boolean isNumericAreaCode(final String str) {
        return str.length() == 3 && StringUtilities.isNumeric(str);
    }


    private static Locale parseLocale(final String str) {
        if (isISO639LanguageCode(str)) {
            return new Locale.Builder().setLanguage(str).build();
        }
        final int limit = 3;
        final char separator = str.indexOf(UNDERSCORE) != -1 ? UNDERSCORE : DASH;
        final String[] segments = str.split(String.valueOf(separator), 3);
        final String language = segments[0];
        if (segments.length == 2) {
            final String country = segments[1];
            if ((isISO639LanguageCode(language) && isISO3166CountryCode(country))
                    || isNumericAreaCode(country)) {
                return new Locale.Builder().setLanguage(language).setRegion(country).build();
            }
        } else if (segments.length == limit) {
            final String country = segments[1];
            final String variant = segments[2];
            if (isISO639LanguageCode(language)
                    && (country.isEmpty() || isISO3166CountryCode(country) || isNumericAreaCode(country))
                    && !variant.isEmpty()) {
                return new Locale.Builder()
                        .setLanguage(language)
                        .setRegion(country)
                        .setVariant(variant)
                        .build();
            }
        }
        throw new IllegalArgumentException("Invalid locale format: " + str);
    }

    public static Locale toLocale(final String str) {
        if (str == null) {
            // TODO Should this return the default locale?
            return null;
        }
        if (str.isEmpty()) {
            return Locale.ROOT;
        }
        if (str.contains("#")) { // LANG-879 - Cannot handle Java 7 script & extensions
            throw new IllegalArgumentException("Invalid locale format: " + str);
        }
        final int len = str.length();
        if (len < 2) {
            throw new IllegalArgumentException("Invalid locale format: " + str);
        }
        final char ch0 = str.charAt(0);
        if (ch0 == UNDERSCORE || ch0 == DASH) {
            if (len < 3) {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            }
            final char ch1 = str.charAt(1);
            final char ch2 = str.charAt(2);
            if (!Character.isUpperCase(ch1) || !Character.isUpperCase(ch2)) {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            }
            if (len == 3) {
                return new Locale.Builder().setRegion(str.substring(1, 3)).build();
            }
            if (len < 5) {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            }
            if (str.charAt(3) != ch0) {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            }
            return new Locale.Builder()
                    .setRegion(str.substring(1, 3))
                    .setVariant(str.substring(4))
                    .build();
        }

        return parseLocale(str);
    }
}