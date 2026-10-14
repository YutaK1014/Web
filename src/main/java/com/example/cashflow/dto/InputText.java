package com.example.cashflow.dto;

public final class InputText {
    private InputText() { }

    // String.strip alone excludes non-breaking spaces (U+00A0, U+2007, U+202F).
    public static String stripSpace(String value) {
        if (value == null) return "";
        int start = 0, end = value.length();
        while (start < end && isSpace(value.codePointAt(start)))
            start += Character.charCount(value.codePointAt(start));
        while (end > start && isSpace(value.codePointBefore(end)))
            end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
