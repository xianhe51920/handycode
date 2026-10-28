package com.fairycrane.handycode;

/** Adds lifecycle-scoped access to two existing official read-only callbacks. */
public final class PddBundleAdapter {
    private PddBundleAdapter() {}

    public static String patch(String part, String source) {
        String hook, replacement, outcome, outcomeReplacement;
        if ("identity".equals(part)) {
            hook = "(0,w.p)((function(){pi()})),(0,R.jsxs)(\"div\",{className:V()[\"identification-code-container\"]";
            replacement = "(0,w.p)((function(){pi()})),(0,_.useEffect)((function(){return window.HandyPddRefresh?window.HandyPddRefresh.register(\"identity\",pi):void 0}),[pi]),(0,R.jsxs)(\"div\",{className:V()[\"identification-code-container\"]";
            outcome = "entry_source:O.idcode_entry_source||\"APP\"});case 2:null!==(e=i.sent)&&void 0!==e&&e.success?";
            outcomeReplacement = "entry_source:O.idcode_entry_source||\"APP\"});case 2:e=i.sent,window.HandyPddRefresh&&window.HandyPddRefresh.report(\"identity\",!!e&&!!e.success);null!==e&&void 0!==e&&e.success?";
        } else if ("parcels".equals(part)) {
            hook = "return[4,3].includes(null===Y||void 0===Y?void 0:Y.pick_up_mode)?";
            replacement = "(0,c.useEffect)((function(){return window.HandyPddRefresh?window.HandyPddRefresh.register(\"parcels\",function(){return kn(!0)}):void 0}),[kn]);" + hook;
            outcome = "case 3:t=e.sent,rn(!1),null!==t&&void 0!==t&&t.success?";
            outcomeReplacement = "case 3:t=e.sent,window.HandyPddRefresh&&window.HandyPddRefresh.report(\"parcels\",!!t&&!!t.success),rn(!1),null!==t&&void 0!==t&&t.success?";
        } else return null;
        // A changed upstream component must fail closed, never silently bind
        // the wrong callback or fall back to a whole-page refresh.
        if (!unique(source, hook) || !unique(source, outcome)) return null;
        return source.replace(hook, replacement).replace(outcome, outcomeReplacement);
    }

    private static boolean unique(String source, String target) {
        int index = source.indexOf(target);
        return index >= 0 && source.indexOf(target, index + target.length()) < 0;
    }
}
