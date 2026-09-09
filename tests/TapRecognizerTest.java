import kr.toptap.android.core.TapRecognizer;
import static kr.toptap.android.core.TapRecognizer.Result.*;

public final class TapRecognizerTest {
    private static int count;
    private static void eq(Object actual,Object expected,String name){count++;if(!actual.equals(expected))throw new AssertionError(name+": "+actual+" != "+expected);}
    public static void main(String[] args){
        TapRecognizer r=new TapRecognizer(8);
        r.down(50,10,100);eq(r.up(50,10,150),TAP,"ordinary tap");
        r.down(50,10,200);eq(r.up(50,10,600),NONE,"long press ignored");
        r.down(50,10,700);eq(r.move(50,40),DRAG_DOWN,"vertical shade drag");eq(r.up(50,40,800),NONE,"drag never scrolls");
        r.down(50,10,900);r.move(80,10);r.move(50,10);eq(r.up(50,10,950),NONE,"move then return never tap");
        r.down(50,10,1000);r.cancel();eq(r.up(50,10,1050),NONE,"multitouch/cancel ignored");
        r.down(50,10,1100);eq(r.up(50,10,1150),TAP,"first tap never waits for another");
        r.down(50,10,1200);eq(r.up(50,10,1250),TAP,"second tap remains an independent stop or start");
        r.down(50,10,1400);eq(r.up(50,10,1450),TAP,"third tap remains independent");
        r.down(1,10,2000);eq(r.up(1,10,2050),TAP,"left edge tap accepted");
        r.down(1078,10,2100);eq(r.up(1078,10,2150),TAP,"right edge tap accepted");
        r.cancel();r.down(50,10,2300);eq(r.up(55,12,2350),TAP,"jitter accepted");
        r.down(50,10,2400);eq(r.move(50,19),DRAG_DOWN,"recognize shade before system edge takeover");
        System.out.println("PASS "+count+" tap recognition assertions");
    }
}
