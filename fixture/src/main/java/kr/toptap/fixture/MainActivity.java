package kr.toptap.fixture;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Separate process/package proves cross-app behavior; deterministic offline fixtures only. */
public final class MainActivity extends Activity {
    private TextView status;
    private final android.os.Handler traceHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final org.json.JSONArray motionTrace=new org.json.JSONArray(),touchTrace=new org.json.JSONArray();
    private int lastPixel=Integer.MIN_VALUE,touchCount;
    private final Runnable saveTrace=()->{
        try(java.io.FileOutputStream out=openFileOutput("motion.json",MODE_PRIVATE)) {
            out.write(new org.json.JSONObject().put("motion",motionTrace).put("touch",touchTrace).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(java.io.IOException | org.json.JSONException e){android.util.Log.e("TopTapFixture","Unable to save synthetic motion trace",e);}
    };
    private void recordMotion(int pixel) {
        if(pixel==lastPixel)return;lastPixel=pixel;
        if(motionTrace.length()<2000)motionTrace.put(new org.json.JSONArray().put(android.os.SystemClock.uptimeMillis()).put(pixel));
        traceHandler.removeCallbacks(saveTrace);traceHandler.postDelayed(saveTrace,350);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) {
            touchTrace.put(new org.json.JSONArray().put(event.getEventTime()).put(action));
            if(action==MotionEvent.ACTION_DOWN)getSharedPreferences("qa",MODE_PRIVATE).edit().putInt("touch_count",++touchCount).apply();
            traceHandler.removeCallbacks(saveTrace);traceHandler.postDelayed(saveTrace,350);
        }
        return super.dispatchTouchEvent(event);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void position(int value){status.setText("POS="+value+(value==0?" TOP_REACHED":""));getSharedPreferences("qa",MODE_PRIVATE).edit().putInt("position",value).apply();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);String mode=getIntent().getStringExtra("mode");if(mode==null)mode="list";final String kind=mode;
        int offset=getIntent().getIntExtra("offset",120);
        getSharedPreferences("qa",MODE_PRIVATE).edit().putBoolean("ready",false).putString("mode",mode).commit();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff5f7fb);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        status=new TextView(this);status.setTextSize(16);status.setPadding(dp(16),dp(12),dp(16),dp(12));root.addView(status);position(offset);
        TextView title=new TextView(this);title.setText("External QA · "+mode);title.setTextSize(20);title.setPadding(dp(16),dp(8),dp(16),dp(12));root.addView(title);
        if(mode.equals("range")) {
            android.widget.SeekBar range=new android.widget.SeekBar(this);range.setMax(200);range.setProgress(offset);
            range.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(android.widget.SeekBar v){}public void onStopTrackingTouch(android.widget.SeekBar v){}public void onProgressChanged(android.widget.SeekBar v,int value,boolean fromUser){position(value);}});
            root.addView(range,new LinearLayout.LayoutParams(-1,dp(180)));range.postDelayed(()->getSharedPreferences("qa",MODE_PRIVATE).edit().putBoolean("ready",true).apply(),300);
        }else if(mode.equals("gesture")) {
            android.widget.ScrollView scroller=new android.widget.ScrollView(this){@Override protected void onScrollChanged(int x,int y,int ox,int oy){super.onScrollChanged(x,y,ox,oy);position(y);}};
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
            for(int i=0;i<100;i++){TextView row=new TextView(this);row.setText("Gesture fixture "+i);row.setMinHeight(dp(80));content.addView(row);}
            scroller.addView(content);scroller.setAccessibilityDelegate(new View.AccessibilityDelegate(){
                @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setScrollable(true);info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP);info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
                @Override public boolean performAccessibilityAction(View host,int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD || action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId())return false;return super.performAccessibilityAction(host,action,args);}});
            root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));scroller.postDelayed(()->{scroller.scrollTo(0,offset*dp(80));position(scroller.getScrollY());getSharedPreferences("qa",MODE_PRIVATE).edit().putBoolean("ready",true).apply();},300);
        }else if(mode.equals("web")){
            WebView web=new WebView(this){@Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);position(y);recordMotion(y);}};
            // The harness scrolls this with real touch input, matching the user's
            // workflow and keeping native/virtual accessibility state synchronized.
            web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){view.postDelayed(()->{position(view.getScrollY());getSharedPreferences("qa",MODE_PRIVATE).edit().putBoolean("ready",true).apply();},300);}});
            StringBuilder html=new StringBuilder("<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='font:20px sans-serif;margin:24px'><h1>TOP_REACHED</h1>");
            for(int i=1;i<300;i++)html.append("<section style='height:110px;border-bottom:1px solid #ccd'>Article section ").append(i).append("<p>Offline scrolling test</p></section>");html.append("</body></html>");
            root.addView(web,new LinearLayout.LayoutParams(-1,0,1));web.loadDataWithBaseURL("https://fixture.invalid/",html.toString(),"text/html","UTF-8",null);
        }else if(mode.equals("horizontal")){
            HorizontalScrollView horizontal=new HorizontalScrollView(this);LinearLayout items=new LinearLayout(this);
            for(int i=0;i<40;i++){TextView item=new TextView(this);item.setText("Card "+i);items.addView(item,new LinearLayout.LayoutParams(dp(200),dp(300)));}horizontal.addView(items);root.addView(horizontal);horizontal.post(()->{horizontal.scrollTo(dp(1000),0);position(horizontal.getScrollX());});
            horizontal.setOnScrollChangeListener((v,x,y,ox,oy)->position(x));
            horizontal.postDelayed(()->getSharedPreferences("qa",MODE_PRIVATE).edit().putBoolean("ready",true).apply(),300);
        }else{
            AbsListView list;
            if(mode.equals("grid")){GridView grid=new GridView(this);grid.setNumColumns(3);grid.setVerticalSpacing(dp(8));grid.setHorizontalSpacing(dp(8));list=grid;}else list=new ListView(this);
            list.setAdapter(new BaseAdapter(){public int getCount(){return 400;}public Object getItem(int p){return p;}public long getItemId(int p){return p;}
                public View getView(int p,View old,ViewGroup parent){TextView row=new TextView(MainActivity.this);row.setText(p==0?"TOP_REACHED":"Item "+p);row.setTextSize(18);row.setPadding(dp(16),dp(22),dp(16),dp(22));row.setMinHeight(dp(kind.equals("grid")?110:64));row.setBackgroundColor(p%2==0?0xffe9effe:0xffffffff);return row;}});
            list.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int s){}public void onScroll(AbsListView v,int first,int visible,int total){position(first);if(v.getChildCount()>0){
                View child=v.getChildAt(0);int offsetPx=child.getTop()-v.getPaddingTop();
                getSharedPreferences("qa",MODE_PRIVATE).edit().putInt("offset_px",offsetPx).apply();
                int spacing=v instanceof ListView?((ListView)v).getDividerHeight():((GridView)v).getVerticalSpacing();
                recordMotion((kind.equals("grid")?first/3:first)*(child.getHeight()+spacing)-offsetPx);
            }}});
            if(mode.equals("direct"))list.setAccessibilityDelegate(new View.AccessibilityDelegate(){
                @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION);}
                @Override public boolean performAccessibilityAction(View host,int action,Bundle args){if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId()){list.setSelection(0);return true;}return super.performAccessibilityAction(host,action,args);}});
            if(mode.equals("granular") && Build.VERSION.SDK_INT>=35)list.setAccessibilityDelegate(new View.AccessibilityDelegate(){
                @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION);info.setGranularScrollingSupported(true);}
                @Override public boolean performAccessibilityAction(View host,int action,Bundle args){if((action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD || action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()) && args!=null && args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT)==Float.POSITIVE_INFINITY){list.setSelection(0);getSharedPreferences("qa",MODE_PRIVATE).edit().putBoolean("granular_used",true).apply();return true;}return super.performAccessibilityAction(host,action,args);}});
            root.addView(list,new LinearLayout.LayoutParams(-1,0,1));list.post(()->list.setSelection(offset));
            list.postDelayed(()->getSharedPreferences("qa",MODE_PRIVATE).edit().putBoolean("ready",true).apply(),300);
        }
        setContentView(root);
    }
}
