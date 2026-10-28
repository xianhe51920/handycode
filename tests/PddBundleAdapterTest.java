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
        // Minimal excerpts from the public components, including result gates.
        String identity = "entry_source:O.idcode_entry_source||\"APP\"});case 2:null!==(e=i.sent)&&void 0!==e&&e.success?ok():bad();(0,w.p)((function(){pi()})),(0,R.jsxs)(\"div\",{className:V()[\"identification-code-container\"]});";
        String parcels = "case 3:t=e.sent,rn(!1),null!==t&&void 0!==t&&t.success?ok():bad();return[4,3].includes(null===Y||void 0===Y?void 0:Y.pick_up_mode)?yes():no();";
        for (String part : new String[]{"identity", "parcels"}) {
            String original = "identity".equals(part) ? identity : parcels;
            String result = PddBundleAdapter.patch(part, original);
            if (result == null || !result.contains("register(\"" + part + "\"") || !result.contains("report(\"" + part + "\"")) throw new AssertionError("Missing scoped callback and boolean result");
            if (PddBundleAdapter.patch(part, original + original) != null) throw new AssertionError("Ambiguous anchors accepted");
            if (PddBundleAdapter.patch(part, "changed-component") != null) throw new AssertionError("Changed upstream accepted");
            if (PddBundleAdapter.patch(part, result) != null) throw new AssertionError("Already-patched component accepted");
        }
        if (PddBundleAdapter.patch("other", identity) != null) throw new AssertionError("Unexpected component accepted");
        System.out.println("PASS: scoped PDD component hooks, result reporting and fail-closed anchors");
    }
}
