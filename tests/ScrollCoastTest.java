import kr.toptap.android.core.ScrollCoast;

public final class ScrollCoastTest {
    private static int count;
    private static void eq(long actual,long expected,String name) {
        count++; if(actual!=expected)throw new AssertionError(name+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        ScrollCoast coast=new ScrollCoast();
        coast.begin(1000,false);
        eq(coast.remaining(1000),450,"allow inertia and edge settling to start");
        eq(coast.remaining(1449),1,"do not restart during a delayed edge rebound");
        eq(coast.remaining(1450),0,"quiet viewport can advance");
        coast.motion(1400);
        eq(coast.remaining(1500),60,"active fling delays next gesture");
        coast.motion(1540);
        eq(coast.remaining(1560),140,"continued movement extends the wait");
        eq(coast.remaining(1700),0,"settled fling can advance");
        eq(coast.remaining(9000),0,"elapsed delay never goes negative");
        coast.begin(10000,false);
        eq(coast.remaining(10010),440,"new run discards prior motion");
        coast.begin(20000,true);
        eq(coast.remaining(20449),1,"physical edge settling still has a minimum window");
        coast.motion(21000);
        coast.motion(20900);
        eq(coast.remaining(21060),20,"batched physical movement retains a margin");
        eq(coast.remaining(21080),0,"physical handoff avoids the longer native-end pause");
        System.out.println("PASS "+count+" scroll pacing assertions");
    }
}
