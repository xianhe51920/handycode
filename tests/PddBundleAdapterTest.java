import com.fairycrane.handycode.PddBundleAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class PddBundleAdapterTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 3) {
            String patched = PddBundleAdapter.patch(args[0], new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8));
            if (patched == null) throw new AssertionError("Official component anchors changed");
            Files.write(Paths.get(args[2]), patched.getBytes(StandardCharsets.UTF_8));
            System.out.println("PASS: official " + args[0] + " component adapted");
            return;
        }
        // Minimal excerpts from the 2026-11-22 public components, including result gates.
        String identity = "var _i=(0,c.useCallback)(fn,[]);entry_source:Z.idcode_entry_source||\"APP\"});case 2:null!==(e=i.sent)&&void 0!==e&&e.success?ok():bad();(0,b.p)((function(){_i()})),(0,F.jsxs)(\"div\",{className:K()[\"identification-code-container\"]});";
        String legacyIdentity = "var pi=(0,_.useCallback)(fn,[]);entry_source:O.idcode_entry_source||\"APP\"});case 2:null!==(e=i.sent)&&void 0!==e&&e.success?ok():bad();(0,w.p)((function(){pi()})),(0,R.jsxs)(\"div\",{className:V()[\"identification-code-container\"]});";
        String parcels = "queryStationPackageInfo({station_code:s});case 8:if(null===(d=e.sent)||void 0===d||!d.success){fail();}Q=function(){return{refreshing:i,refreshPage:(0,t.useCallback)(fn,[e])}}(),T=Q.refreshing,O=Q.refreshPage;return[4,3].includes(null===v||void 0===v?void 0:v.pick_up_mode)?yes():no();";
        String legacyParcels = "case 3:t=e.sent,rn(!1),null!==t&&void 0!==t&&t.success?ok():bad();return[4,3].includes(null===Y||void 0===Y?void 0:Y.pick_up_mode)?yes():no();";
        for (String[] pair : new String[][]{
            {"identity", identity},
            {"identity", legacyIdentity},
            {"parcels", parcels},
            {"parcels", legacyParcels}
        }) {
            String part = pair[0], original = pair[1];
            String result = PddBundleAdapter.patch(part, original);
            if (result == null || !result.contains("register(\"" + part + "\"") || !result.contains("report(\"" + part + "\"")) {
                throw new AssertionError("Missing scoped callback and boolean result for " + part);
            }
            if (PddBundleAdapter.patch(part, original + original) != null) throw new AssertionError("Ambiguous anchors accepted");
            if (PddBundleAdapter.patch(part, "changed-component") != null) throw new AssertionError("Changed upstream accepted");
            if (PddBundleAdapter.patch(part, result) != null) throw new AssertionError("Already-patched component accepted");
            String auto = PddBundleAdapter.patchNumericChunk(original);
            if (!result.equals(auto)) throw new AssertionError("patchNumericChunk mismatch for " + part);
        }
        if (PddBundleAdapter.patchNumericChunk("other-chunk-content") != null) throw new AssertionError("Unrelated chunk modified");
        if (PddBundleAdapter.patch("other", identity) != null) throw new AssertionError("Unexpected component accepted");
        System.out.println("PASS: scoped PDD component hooks, result reporting and fail-closed anchors");
    }
}
