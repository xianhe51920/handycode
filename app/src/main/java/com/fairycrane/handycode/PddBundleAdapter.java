package com.fairycrane.handycode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds lifecycle-scoped access to two existing official read-only callbacks. */
public final class PddBundleAdapter {
    static final int VERSION = 3;

    private static final Pattern IDENTITY_HOOK = Pattern.compile(
        "(\\(0,[\\w$]+\\.p\\)\\(\\(function\\(\\)\\{([\\w$]+)\\(\\)\\}\\)\\))(,\\(0,[\\w$]+\\.jsxs\\)\\(\"div\",\\{className:[\\w$]+\\(\\)\\[\"identification-code-container\"\\])");
    private static final Pattern IDENTITY_OUTCOME = Pattern.compile(
        "(entry_source:[\\w$]+\\.idcode_entry_source\\|\\|\"APP\"\\}\\);case \\d+:)null!==\\(([\\w$]+)=([\\w$]+\\.sent)\\)(&&void 0!==\\2&&\\2\\.success\\?)");
    private static final Pattern REACT_HOOK_ALIAS = Pattern.compile(
        "\\(0,([\\w$]+)\\.(?:useCallback|useEffect|useState|useRef)\\)");

    private static final Pattern PARCELS_META = Pattern.compile(
        "refreshPage:\\(0,([\\w$]+)\\.useCallback\\)\\([\\s\\S]{0,400}?\\}\\(\\),[\\w$]+=[\\w$]+\\.refreshing,([\\w$]+)=[\\w$]+\\.refreshPage");
    private static final Pattern PARCELS_RETURN = Pattern.compile(
        "return\\[4,3\\]\\.includes\\([^)]+\\)\\?");
    private static final Pattern PARCELS_OUTCOME = Pattern.compile(
        "(queryStationPackageInfo\\(\\{[^}]+\\}\\);case \\d+:if\\()null===\\(([\\w$]+)=([\\w$]+\\.sent)\\)(\\|\\|void 0===\\2\\|\\|!\\2\\.success\\)\\{)");

    private static final String LEGACY_PARCELS_HOOK = "return[4,3].includes(null===Y||void 0===Y?void 0:Y.pick_up_mode)?";
    private static final String LEGACY_PARCELS_OUTCOME = "case 3:t=e.sent,rn(!1),null!==t&&void 0!==t&&t.success?";

    private PddBundleAdapter() {}

    /** Patches any numeric Next.js chunk if it uniquely matches identity or parcels anchors. */
    public static String patchNumericChunk(String source) {
        if (source == null || source.isEmpty()) return null;
        String identity = patch("identity", source);
        if (identity != null) return identity;
        return patch("parcels", source);
    }

    public static String patch(String part, String source) {
        if (source == null || source.isEmpty()) return null;
        if (source.contains("window.HandyPddRefresh.register(\"" + part + "\"")
                || source.contains("window.HandyPddRefresh.report(\"" + part + "\"")) {
            return null;
        }
        // A changed upstream component must fail closed, never silently bind
        // the wrong callback or fall back to a whole-page refresh.
        if ("identity".equals(part)) return patchIdentity(source);
        if ("parcels".equals(part)) return patchParcels(source);
        return null;
    }

    private static String patchIdentity(String source) {
        Matcher hook = uniqueMatch(IDENTITY_HOOK, source);
        Matcher outcome = uniqueMatch(IDENTITY_OUTCOME, source);
        if (hook == null || outcome == null) return null;
        String reactVar = uniqueReactAlias(source, hook.start());
        if (reactVar == null) return null;

        String callback = hook.group(2);
        String hookReplacement = hook.group(1)
            + ",(0," + reactVar + ".useEffect)((function(){return window.HandyPddRefresh?window.HandyPddRefresh.register(\"identity\","
            + callback + "):void 0}),[" + callback + "])"
            + hook.group(3);
        String varName = outcome.group(2);
        String outcomeReplacement = outcome.group(1)
            + varName + "=" + outcome.group(3)
            + ",window.HandyPddRefresh&&window.HandyPddRefresh.report(\"identity\",!!" + varName + "&&!!" + varName + ".success);"
            + "null!==" + varName + outcome.group(4);

        return replaceBoth(source, hook.start(), hook.end(), hookReplacement,
            outcome.start(), outcome.end(), outcomeReplacement);
    }

    private static String patchParcels(String source) {
        Matcher meta = uniqueMatch(PARCELS_META, source);
        Matcher ret = uniqueMatch(PARCELS_RETURN, source);
        Matcher outcome = uniqueMatch(PARCELS_OUTCOME, source);
        if (meta != null && ret != null && outcome != null
                && meta.end() < ret.start() && ret.start() - meta.end() < 30000) {
            String reactVar = meta.group(1);
            String refreshFn = meta.group(2);
            String hookReplacement = "(0," + reactVar + ".useEffect)((function(){return window.HandyPddRefresh?window.HandyPddRefresh.register(\"parcels\","
                + refreshFn + "):void 0}),[" + refreshFn + "]);" + ret.group(0);
            String varName = outcome.group(2);
            String outcomeReplacement = outcome.group(1)
                + varName + "=" + outcome.group(3)
                + ",window.HandyPddRefresh&&window.HandyPddRefresh.report(\"parcels\",!!" + varName + "&&!!" + varName + ".success),"
                + "null===" + varName + outcome.group(4);
            return replaceBoth(source, ret.start(), ret.end(), hookReplacement,
                outcome.start(), outcome.end(), outcomeReplacement);
        }
        if (unique(source, LEGACY_PARCELS_HOOK) && unique(source, LEGACY_PARCELS_OUTCOME)) {
            String hookReplacement = "(0,c.useEffect)((function(){return window.HandyPddRefresh?window.HandyPddRefresh.register(\"parcels\",function(){return kn(!0)}):void 0}),[kn]);"
                + LEGACY_PARCELS_HOOK;
            String outcomeReplacement = "case 3:t=e.sent,window.HandyPddRefresh&&window.HandyPddRefresh.report(\"parcels\",!!t&&!!t.success),rn(!1),null!==t&&void 0!==t&&t.success?";
            return source.replace(LEGACY_PARCELS_HOOK, hookReplacement).replace(LEGACY_PARCELS_OUTCOME, outcomeReplacement);
        }
        return null;
    }

    private static String uniqueReactAlias(String source, int beforeIndex) {
        int start = Math.max(0, beforeIndex - 12000);
        Matcher m = REACT_HOOK_ALIAS.matcher(source.substring(start, beforeIndex));
        String found = null;
        while (m.find()) {
            String candidate = m.group(1);
            if (found == null) found = candidate;
            else if (!found.equals(candidate)) return null;
        }
        return found;
    }

    private static Matcher uniqueMatch(Pattern pattern, String source) {
        Matcher first = pattern.matcher(source);
        if (!first.find()) return null;
        int s = first.start(), e = first.end();
        if (first.find()) return null;
        Matcher exact = pattern.matcher(source);
        exact.find(s);
        if (exact.start() != s || exact.end() != e) return null;
        return exact;
    }

    private static String replaceBoth(String source,
                                      int s1, int e1, String r1,
                                      int s2, int e2, String r2) {
        if (s1 > s2) {
            return replaceBoth(source, s2, e2, r2, s1, e1, r1);
        }
        return new StringBuilder(source.length() + r1.length() + r2.length())
            .append(source, 0, s1)
            .append(r1)
            .append(source, e1, s2)
            .append(r2)
            .append(source, e2, source.length())
            .toString();
    }

    private static boolean unique(String source, String target) {
        int index = source.indexOf(target);
        return index >= 0 && source.indexOf(target, index + target.length()) < 0;
    }
}
