import kr.toptap.android.core.ScrollCoast;

public final class ScrollCoastTest {
    private static int count;
    private static void eq(long actual,long expected,String name) {
        count++; if(actual!=expected)throw new AssertionError(name+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        ScrollCoast coast=new ScrollCoast();
        coast.begin(1000);
        eq(coast.remaining(1000),280,"allow inertia to start");
        eq(coast.remaining(1279),1,"do not restart early when events are delayed");
        eq(coast.remaining(1280),0,"quiet viewport can advance");
        coast.motion(1400);
        eq(coast.remaining(1500),60,"active fling delays next gesture");
        coast.motion(1540);
        eq(coast.remaining(1560),140,"continued movement extends the wait");
        eq(coast.remaining(1700),0,"settled fling can advance");
        eq(coast.remaining(9000),0,"elapsed delay never goes negative");
        coast.begin(10000);
        eq(coast.remaining(10010),270,"new run discards prior motion");
        System.out.println("PASS "+count+" scroll pacing assertions");
    }
}
