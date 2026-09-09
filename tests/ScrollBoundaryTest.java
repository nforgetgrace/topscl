import kr.toptap.android.core.ScrollBoundary;

public final class ScrollBoundaryTest {
    private static int count;
    private static void check(boolean expected,int y,int maxY,int index,int total,boolean aligned,String name) {
        count++;
        if(ScrollBoundary.atStart(y,maxY,index,total,aligned)!=expected)throw new AssertionError(name);
    }
    public static void main(String[] args) {
        check(false,0,10000,240,400,true,"zero y cannot override a positive item index");
        check(false,800,10000,0,400,true,"zero index cannot override positive pixel distance");
        check(false,-1,0,-1,0,true,"unknown offsets are not the top");
        check(false,0,0,0,400,false,"clipped first row is not the top");
        check(false,0,0,-1,0,true,"default zero without an extent is not evidence");
        check(true,0,10000,-1,0,false,"pixel viewport at origin");
        check(true,-1,0,0,400,true,"first collection row fully aligned");
        check(true,0,10000,0,400,true,"consistent origin signals");
        check(false,-1,0,3,400,true,"fully visible nonzero row");
        System.out.println("PASS "+count+" boundary assertions");
    }
}
