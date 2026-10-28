import com.fairycrane.handycode.SwitchMotion;

public final class SwitchMotionTest {
    private static int checks;
    private static void expect(boolean value) { checks++; if (!value) throw new AssertionError("Switch motion case " + checks); }
    private static boolean near(float a, float b) { return Math.abs(a - b) < .001f; }
    public static void main(String[] args) {
        SwitchMotion motion = new SwitchMotion(0);
        motion.drag(0, -140, 400);
        expect(near(motion.position(), .35f)); expect(motion.target(false) == 0);
        expect(motion.selected() == 0); // Drag/animation cannot activate a page.
        motion.drag(0, -200, 400); expect(motion.target(false) == 0);
        motion.drag(0, -201, 400); expect(motion.target(false) == 1);
        expect(motion.selected() == 0);
        expect(motion.target(true) == 0);
        motion.commit(1); expect(motion.selected() == 1 && motion.position() == 1);
        motion.drag(1, 200, 400); expect(motion.target(false) == 1);
        motion.drag(1, 201, 400); expect(motion.target(false) == 0);
        expect(motion.selected() == 1 && motion.target(true) == 1);
        for (int step = 0; step <= 100; step++) {
            motion.position(step / 100f);
            expect(near(motion.offset(1, 400) - motion.offset(0, 400), 400)); // No gap/overlap.
            expect(near(-motion.offset(0, 400) / 400, -motion.offset(0, 40) / 40)); // Header tracks content.
            expect(near(motion.offset(1, 40) / 40, 1 - motion.position())); // Incoming header.
        }
        motion.drag(0, 300, 400); expect(motion.position() == 0);
        motion.drag(1, -300, 400); expect(motion.position() == 1);
        motion.drag(.25f, 100, 0); expect(motion.position() == .25f);
        motion.position(.8f); motion.drag(motion.position(), 180, 400);
        expect(near(motion.position(), .35f) && motion.target(false) == 0); // Interrupt/reverse mid-settle.
        expect(SwitchMotion.direction(4, 3, 8) == 0);
        expect(SwitchMotion.direction(-30, 3, 8) == 1);
        expect(SwitchMotion.direction(3, -30, 8) == 2);
        expect(SwitchMotion.direction(20, 20, 8) == 2);
        expect(SwitchMotion.direction(12, 10, 8) == 0);
        expect(SwitchMotion.duration(1) == 320 && SwitchMotion.duration(.25f) < SwitchMotion.duration(.75f));
        motion.commit(0); motion.position(.2f);
        expect(motion.target(false, -800, -30) == 1); // Short, deliberate fling.
        expect(motion.target(false, -599, -30) == 0); // Slow drag uses half screen.
        expect(motion.target(false, -1200, -10) == 0); // Tiny flick is not enough.
        expect(motion.target(true, -1200, -100) == 0); // System cancel wins.
        motion.commit(1); motion.position(.8f);
        expect(motion.target(false, 800, 30) == 0);
        expect(motion.target(false, 599, 30) == 1);
        expect(motion.target(false, -1000, -30) == 1); // Outer edge stays put.
        motion.commit(0); motion.position(.8f);
        expect(motion.target(false, 1000, -50) == 0); // Fast reversal follows release direction.
        System.out.println("PASS: " + checks + " switch geometry, half-screen threshold, cancellation, lazy-commit and direction cases");
    }
}
