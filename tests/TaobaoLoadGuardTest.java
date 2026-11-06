import com.fairycrane.handycode.TaobaoLoadGuard;

public final class TaobaoLoadGuardTest {
    private static void equal(long expected, long actual, String scenario) {
        if (expected != actual) throw new AssertionError(scenario + ": " + actual);
    }

    public static void main(String[] args) {
        TaobaoLoadGuard guard = new TaobaoLoadGuard();
        guard.begin();
        equal(-1, guard.delay(0), "Loading before Activity resumes");
        guard.resume(100);
        guard.begin();
        guard.resume(10_100);
        equal(20_000, guard.delay(10_100), "Duplicate navigation must not restart loading");
        guard.pause(10_100);
        equal(-1, guard.delay(70_100), "Background time does not count");
        guard.resume(70_100);
        equal(1, guard.delay(90_099), "Remaining foreground time survives backgrounding");
        equal(0, guard.delay(90_100), "Deadline does not need a WebView callback");
        guard.stop();
        equal(-1, guard.delay(120_100), "Login form or a finished attempt cancels the deadline");
        guard.begin();
        guard.resume(120_100);
        equal(30_000, guard.delay(120_100), "Next load gets a fresh deadline");
        System.out.println("PASS: Taobao foreground loading deadline");
    }
}
