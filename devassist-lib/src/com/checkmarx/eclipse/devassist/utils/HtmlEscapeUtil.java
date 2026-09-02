package com.checkmarx.eclipse.devassist.utils;

public final class HtmlEscapeUtil {

    private HtmlEscapeUtil() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
