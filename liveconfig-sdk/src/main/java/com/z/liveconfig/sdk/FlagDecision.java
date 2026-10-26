package com.z.liveconfig.sdk;

/** 一次取值的完整结论，source 说明这个值是从哪条路径来的 */
public record FlagDecision(String key,
                           String type,
                           String value,
                           String hitRule,
                           String reason,
                           Integer bucket,
                           Integer flagVersion,
                           long configVersion,
                           String source) {

    public static final String SOURCE_SERVER = "SERVER";
    public static final String SOURCE_FALLBACK = "FALLBACK";
    public static final String SOURCE_DEFAULT = "CALLER_DEFAULT";

    public boolean booleanValue(boolean def) {
        if (value == null) {
            return def;
        }
        return Boolean.parseBoolean(value);
    }

    public int intValue(int def) {
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double doubleValue(double def) {
        if (value == null) {
            return def;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public String stringValue(String def) {
        return value == null ? def : value;
    }

    static FlagDecision callerDefault(String key, String def, String reason) {
        return new FlagDecision(key, null, def, "CALLER_DEFAULT", reason,
                null, null, -1L, SOURCE_DEFAULT);
    }
}
