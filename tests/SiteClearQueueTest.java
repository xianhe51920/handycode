import com.fairycrane.handycode.LogoutSites;
import com.fairycrane.handycode.SiteClearQueue;
import java.util.*;

public class SiteClearQueueTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        List<String> calls = new ArrayList<>();
        List<Runnable> callbacks = new ArrayList<>();
        int[] success = {0}, failure = {0};
        String[] input = {"first.invalid", "second.invalid"};
        SiteClearQueue.run(input, (site, cb) -> { calls.add(site); callbacks.add(cb); },
            () -> success[0]++, e -> failure[0]++);
        input[1] = "mutated.invalid";
        check(calls.equals(Arrays.asList("first.invalid")));
        check(success[0] == 0);
        callbacks.get(0).run();
        callbacks.get(0).run();
        check(calls.equals(Arrays.asList("first.invalid", "second.invalid")));
        check(success[0] == 0);
        callbacks.get(1).run(); callbacks.get(1).run();
        check(success[0] == 1 && failure[0] == 0);
        SiteClearQueue.run(new String[]{"failure.invalid", "never.invalid"}, (site, cb) -> {
            throw new IllegalStateException("fixture");
        }, () -> success[0]++, e -> failure[0]++);
        check(success[0] == 1 && failure[0] == 1);
        SiteClearQueue.run(new String[]{}, (site, cb) -> { throw new AssertionError(); },
            () -> success[0]++, e -> failure[0]++);
        check(success[0] == 2 && failure[0] == 1);
        String[] taobao = LogoutSites.forPage(0), pdd = LogoutSites.forPage(1);
        check(taobao.length == 5 && pdd.length == 1);
        for (String site : taobao) check(!Arrays.asList(pdd).contains(site));
        taobao[0] = "mutated.invalid";
        check(LogoutSites.forPage(0)[0].equals("taobao.com"));
        check(LogoutSites.forPage(1)[0].equals("pinduoduo.net"));
        boolean rejected = false;
        try { LogoutSites.forPage(2); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected);
        System.out.println("Site clearing queue/policy: " + checks + " checks passed");
    }
}
