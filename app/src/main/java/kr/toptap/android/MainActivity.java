package kr.toptap.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private AppSettings settings;
    private LinearLayout page, tabBar;
    private ScrollView scroll;
    private TextView statusTitle, statusDetail, statusBadge, healthStatus;
    private Button primary;
    private int tab;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { @Override public void run() { updateStatus(); handler.postDelayed(this, 700); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); settings = new AppSettings(this); tab = state == null ? 0 : state.getInt("tab");
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.BG); Ui.insets(this, root);
        LinearLayout header = Ui.row(this); header.setPadding(d(24), d(12), d(24), d(12));
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_launcher); logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(logo, new LinearLayout.LayoutParams(d(32), d(32)));
        TextView name = t("탑탭", 21, Ui.INK, true); LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,-2,1); nameParams.leftMargin=d(10); header.addView(name,nameParams);
        TextView label = t("TOPTAP", 11, Ui.MUTED, true); label.setLetterSpacing(.14f); header.addView(label);
        root.addView(header);
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        page = Ui.column(this); page.setPadding(d(24),d(14),d(24),d(24));
        android.widget.FrameLayout.LayoutParams contentParams = new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        int available = getResources().getDisplayMetrics().widthPixels;
        contentParams.width = Math.min(available, d(600)); frame.addView(page, contentParams); scroll.addView(frame);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        tabBar=Ui.row(this); tabBar.setPadding(d(16),d(8),d(16),d(10)); tabBar.setBackgroundColor(Ui.WHITE); root.addView(tabBar);
        setContentView(root); render();
    }
    private int d(float n) { return Ui.dp(this,n); }
    private TextView t(String text,float size,int color,boolean bold) { return Ui.text(this,text,size,color,bold); }
    private void text(LinearLayout parent,String value,float size,int color,boolean bold) { Ui.full(parent,t(value,size,color,bold)); }
    private void gap(LinearLayout parent,int height) { Ui.gap(parent,height); }
    @Override protected void onResume() { super.onResume(); SessionService.sync(this); handler.removeCallbacks(refresh); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("tab",tab); super.onSaveInstanceState(state); }
    private void render() {
        page.removeAllViews(); tabBar.removeAllViews(); statusTitle=null; statusDetail=null; statusBadge=null; healthStatus=null; primary=null;
        String[] titles={"홈","설정","도움말"};
        for(int i=0;i<titles.length;i++) { final int target=i; Button b=Ui.button(this,titles[i],false,()->{ tab=target; render(); scroll.scrollTo(0,0); });
            b.setTextColor(tab==i?Ui.BLUE:Ui.MUTED); b.setBackground(Ui.shape(this,tab==i?Ui.PALE:Ui.WHITE,14)); b.setSelected(tab==i);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1); params.setMargins(d(3),0,d(3),0); tabBar.addView(b,params); }
        if(tab==0) home(); else if(tab==1) controls(); else help(); updateStatus();
    }
    private void home() {
        text(page,"조금 더 편한 갤럭시",12,Ui.BLUE,true); gap(page,8);
        text(page,"길게 내렸다면,\n가볍게 톡.",31,Ui.INK,true); gap(page,8);
        text(page,"화면 상단을 눌러, 다시 맨 위로.",15,Ui.MUTED,false); gap(page,16);
        LinearLayout art=Ui.card(this,Ui.PALE); art.setPadding(d(8),d(8),d(8),0);
        art.addView(new PhoneIllustration(this),new LinearLayout.LayoutParams(-1,d(164))); Ui.full(page,art); gap(page,18);
        LinearLayout status=Ui.card(this,Ui.INK);
        statusBadge=t("●  시작하기",12,0xffBDCEFC,true); Ui.full(status,statusBadge); gap(status,9);
        statusTitle=t("사용 준비가 필요해요",21,Ui.WHITE,true); Ui.full(status,statusTitle); gap(status,6);
        statusDetail=t("접근성을 연결하면 다른 앱에서도 사용할 수 있어요.",14,0xffD0D9E8,false); Ui.full(status,statusDetail); gap(status,18);
        primary=Ui.button(this,"탑탭 시작하기",true,this::primaryAction); Ui.full(status,primary); Ui.full(page,status); gap(page,22);
        LinearLayout how=Ui.row(this); text(how,"작은 터치, 짧은 동선",17,Ui.INK,true); Ui.full(page,how); gap(page,12);
        info(page,"01", "상태바 어디든 한 번 톡", "왼쪽 시계부터 오른쪽 배터리까지. 터치 영역은 투명하고 색이 바뀌지 않아요.");
        info(page,"02", "한 번 더 누르면 멈춤", "맨 위로 이동 중 같은 방식으로 다시 누르면 추가 스크롤을 멈춰요."); gap(page,10);
        Ui.full(page,Ui.button(this,"긴 목록에서 연습하기  ↗",false,()->startActivity(new Intent(this,DemoActivity.class)))); gap(page,16);
        text(page,"인터넷 연결 없이 · 광고 없이 · 화면 내용 저장 없이",11,Ui.MUTED,false);
    }
    private boolean systemEnabled() {
        AccessibilityManager manager=getSystemService(AccessibilityManager.class);
        for(AccessibilityServiceInfo info: manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if(info.getResolveInfo()!=null && getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                && TopTapService.class.getName().equals(info.getResolveInfo().serviceInfo.name)) return true;
        }
        return false;
    }
    private void updateStatus() {
        if(healthStatus!=null)healthStatus.setText(getString(R.string.health_label,systemEnabled()?"켜짐":"꺼짐",ServiceStatus.connected?"연결됨":"확인 필요",ServiceStatus.sessionActive?"사용 중":settings.enabled()?"연결 확인 필요":"일시정지"));
        if(statusTitle==null) return;
        if(ServiceStatus.connected && settings.enabled() && ServiceStatus.overlayError) {
            statusBadge.setText("○  터치 영역 확인 필요"); statusTitle.setText("터치 영역을 연결해 주세요"); statusDetail.setText(ServiceStatus.message); primary.setText("접근성 연결 확인");
        } else if(ServiceStatus.connected && settings.enabled()) {
            statusBadge.setText("●  연결됨"); statusTitle.setText(ServiceStatus.scrolling?"맨 위로 이동하고 있어요":"톡 누를 준비가 됐어요");
            statusDetail.setText(ServiceStatus.scrolling?"상태바를 한 번 더 누르면 추가 스크롤을 멈춰요.":"다른 앱에서 상태바 어디든 한 번 눌러 보세요.\n"+(ServiceStatus.sessionError?"실행 유지 연결을 확인해 주세요.":ServiceStatus.message));
            primary.setText("잠시 멈추기");
        } else if(ServiceStatus.connected) {
            statusBadge.setText("Ⅱ  일시정지"); statusTitle.setText("잠시 쉬고 있어요"); statusDetail.setText("터치 영역이 숨겨졌어요. 원할 때 다시 켜 주세요."); primary.setText("다시 켜기");
        } else if(systemEnabled()) {
            statusBadge.setText("○  연결 확인 필요"); statusTitle.setText("서비스 연결을 확인해요"); statusDetail.setText("권한은 켜져 있지만 연결이 확인되지 않아요. 접근성에서 탑탭을 껐다 켜 주세요."); primary.setText("접근성 연결 확인");
        } else {
            statusBadge.setText("○  시작하기"); statusTitle.setText("사용 준비가 필요해요"); statusDetail.setText("접근성을 연결하면 다른 앱에서도 사용할 수 있어요."); primary.setText("탑탭 시작하기");
        }
    }
    private void primaryAction() {
        if(ServiceStatus.connected && settings.consented() && ServiceStatus.overlayError) {openAccessibility();return;}
        if(ServiceStatus.connected && settings.consented()) { settings.setEnabled(!settings.enabled()); updateStatus(); return; }
        if(settings.consented()) { settings.setEnabled(true); openAccessibility(); return; }
        new AlertDialog.Builder(this).setTitle("접근성 권한을 사용해요")
            .setMessage("탑탭은 다른 앱의 스크롤 영역을 찾기 위해 현재 화면의 구조에 접근해요. 상태바를 한 번 누르면 앱이 제공하는 맨 위 이동이나 터치 스와이프를 실행해요. 빠른 스크롤이 기본으로 켜져 있어요.\n\n최근 앱을 모두 닫아도 사용할 수 있도록 실행을 유지하며, Android의 실행 중인 앱 목록과 실행 알림으로 확인할 수 있어요.\n\n화면의 글·사진·입력 내용은 저장하거나 전송하지 않아요. 인터넷 권한도 없어요.\n\n다음 설정에서 ‘탑탭’을 직접 켜 주세요. 언제든 일시정지하거나 접근성 권한을 끌 수 있어요.")
            .setNegativeButton("나중에",null).setPositiveButton("동의하고 설정 열기",(dialog,which)->{settings.setConsented(true);settings.setEnabled(true);openAccessibility();}).show();
    }
    private void openAccessibility() {
        Intent detail=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME,new ComponentName(this,TopTapService.class));
        open(detail,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
    private void open(Intent intent, Intent fallback) {
        try { startActivity(intent); } catch(android.content.ActivityNotFoundException | SecurityException e) {
            if(fallback!=null) { try {startActivity(fallback);return;}catch(android.content.ActivityNotFoundException | SecurityException ignored) {} }
            Toast.makeText(this,"이 기기에서는 설정 앱에서 직접 열어 주세요",Toast.LENGTH_LONG).show();
        }
    }
    private void info(LinearLayout parent,String number,String title,String body) {
        LinearLayout row=Ui.row(this); row.setGravity(Gravity.TOP); row.setPadding(0,d(9),0,d(9));
        TextView n=t(number,12,Ui.BLUE,true); n.setGravity(Gravity.CENTER); n.setBackground(Ui.shape(this,Ui.PALE,10)); row.addView(n,new LinearLayout.LayoutParams(d(36),d(36)));
        LinearLayout words=Ui.column(this); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=d(12); row.addView(words,p);
        text(words,title,15,Ui.INK,true);gap(words,3);text(words,body,13,Ui.MUTED,false);Ui.full(parent,row);
    }
    private void section(String title,String subtitle) { text(page,title,27,Ui.INK,true);gap(page,8);text(page,subtitle,14,Ui.MUTED,false);gap(page,24); }
    private void controls() {
        section("내 손에 맞게", "상태바 전체에서, 언제나 한 번 터치.");
        LinearLayout motion=Ui.card(this,Ui.WHITE);
        toggle(motion,"빠르고 부드럽게","앱이 지원하면 한 번에 맨 위로. 그 외에는 빠른 스와이프와 관성으로 올라가요.",settings.smoothScrolling(),settings::setSmoothScrolling);
        text(motion,"다시 누르면 추가 스와이프를 멈춰요. 남은 관성은 화면을 터치하면 멈춰요. 일부 화면에서는 당겨서 새로고침이 생길 수 있어요.",12,Ui.MUTED,false);
        Ui.full(page,motion);gap(page,14);
        LinearLayout zone=Ui.card(this,Ui.WHITE);text(zone,"보이지 않는 전체 터치 영역",18,Ui.INK,true);gap(zone,6);
        text(zone,"상태바의 왼쪽·가운데·오른쪽 어디든 한 번 누르세요. 표시선, 터치 색상, 두 번 누르기 설정이 없어요. 화면을 아래로 쓸어내리면 알림창을 열어요.",13,Ui.MUTED,false);Ui.full(page,zone);gap(page,14);
        LinearLayout gestures=Ui.card(this,Ui.WHITE);
        toggle(gestures,"가벼운 진동","실행할 때 짧게 반응해요",settings.haptic(),settings::setHaptic);Ui.full(page,gestures);gap(page,14);
        LinearLayout compatibility=Ui.card(this,Ui.WHITE);text(compatibility,"앱 호환성",18,Ui.INK,true);gap(compatibility,12);
        toggle(compatibility,"호환 스와이프","기본 스크롤이 안 될 때 사용해요. 앱에 따라 새로고침 등 다른 동작이 생길 수 있어요.",settings.gestureFallback(),settings::setGestureFallback);
        gap(compatibility,12);TextView duration=t(getString(R.string.timeout_label,settings.timeoutSeconds()),14,Ui.INK,true);Ui.full(compatibility,duration);
        SeekBar seconds=new SeekBar(this);seconds.setMax(16);seconds.setProgress(settings.timeoutSeconds()-4);seconds.setContentDescription("최대 스크롤 시간");
        seconds.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){duration.setText(getString(R.string.timeout_label,p+4));}public void onStopTrackingTouch(SeekBar s){settings.setTimeoutSeconds(s.getProgress()+4);}});compatibility.addView(seconds,new LinearLayout.LayoutParams(-1,d(48)));
        text(compatibility,"너무 긴 목록은 제한 시간 후 멈춰요. 다시 누르면 이어서 이동해요.",12,Ui.MUTED,false);gap(compatibility,16);
        Ui.full(compatibility,Ui.button(this,"제외할 앱 선택  ·  "+settings.excludedApps().size()+"개",false,this::chooseExcluded));Ui.full(page,compatibility);gap(page,16);
        text(page,"영역 안에서 아래로 쓸면 알림창을 열어요. 오른쪽 영역은 빠른 설정을 열어요. 기기별 동작은 다를 수 있어요.",12,Ui.MUTED,false);
    }
    interface BooleanSetting { void set(boolean enabled); }
    private void toggle(LinearLayout parent,String title,String body,boolean checked,BooleanSetting setter) {
        LinearLayout row=Ui.row(this);row.setPadding(0,d(10),0,d(10));LinearLayout words=Ui.column(this);
        text(words,title,15,Ui.INK,true);gap(words,3);text(words,body,12,Ui.MUTED,false);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        Switch control=new Switch(this);control.setChecked(checked);control.setContentDescription(title);control.setMinHeight(d(48));control.setPadding(d(12),0,0,0);
        control.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.BLUE,0xff8B97A9}));
        control.setOnCheckedChangeListener((b,on)->setter.set(on));row.addView(control);Ui.full(parent,row);
    }
    private void chooseExcluded() {
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved=getPackageManager().queryIntentActivities(query,0);
        List<ResolveInfo> apps=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(ResolveInfo info:resolved)if(!info.activityInfo.packageName.equals(getPackageName()) && seen.add(info.activityInfo.packageName))apps.add(info);
        apps.sort(Comparator.comparing(a->a.loadLabel(getPackageManager()).toString()));
        String[] labels=new String[apps.size()];boolean[] checks=new boolean[apps.size()];Set<String> selected=settings.excludedApps();
        for(int i=0;i<apps.size();i++){labels[i]=apps.get(i).loadLabel(getPackageManager()).toString();checks[i]=selected.contains(apps.get(i).activityInfo.packageName);}
        new AlertDialog.Builder(this).setTitle("탑탭을 사용하지 않을 앱")
            .setMultiChoiceItems(labels,checks,(dialog,which,checked)->{String pkg=apps.get(which).activityInfo.packageName;if(checked)selected.add(pkg);else selected.remove(pkg);})
            .setNegativeButton("취소",null).setPositiveButton("저장",(dialog,which)->{settings.setExcludedApps(selected);render();}).show();
    }
    private void help() {
        section("오래, 편하게 쓰려면", "연결 상태와 갤럭시 절전 설정을 확인해 주세요.");
        LinearLayout health=Ui.card(this,Ui.WHITE);text(health,"연결 확인",18,Ui.INK,true);gap(health,10);
        healthStatus=t("",14,Ui.MUTED,false);Ui.full(health,healthStatus);gap(health,16);
        Ui.full(health,Ui.button(this,"접근성 설정 열기",false,()->{if(settings.consented())openAccessibility();else primaryAction();}));Ui.full(page,health);gap(page,14);
        LinearLayout battery=Ui.card(this,Ui.PALE);text(battery,"갤럭시 절전 예외",18,Ui.INK,true);gap(battery,10);
        text(battery,"1. 앱 정보 → 배터리 → 제한 없음\n2. 설정 → 배터리 → 백그라운드 사용 제한\n3. 절전 예외 앱에 ‘탑탭’ 추가",14,Ui.INK,false);gap(battery,10);
        text(battery,"One UI 버전에 따라 ‘자동 절전 예외 앱’으로 표시될 수 있어요. 이 설정은 기기에서 직접 적용해야 해요.",12,Ui.MUTED,false);gap(battery,16);
        Ui.full(battery,Ui.button(this,"탑탭 앱 정보 열기",false,()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())),null)));Ui.full(page,battery);gap(page,20);
        info(page,"?","최근 앱을 모두 닫아도 되나요?","네. 탑탭은 접근성이 연결돼 있는 동안 실행 유지 서비스를 사용해요. 최근 앱의 ‘모두 닫기’와 설정의 ‘강제 중지’, 실행 중인 앱의 ‘중지’는 달라요. 직접 중지하거나 접근성을 끄면 다시 연결해야 해요.");
        info(page,"?","설치 후 접근성을 켤 수 없어요","직접 설치한 APK는 앱 정보 오른쪽 위 메뉴에서 ‘제한된 설정 허용’이 필요할 수 있어요. 출처를 확인한 앱에만 허용해 주세요.");
        info(page,"↗","빠르게 켜고 끄기","알림창의 빠른 설정 편집에서 ‘탑탭’ 타일을 추가하면 앱을 열지 않고 일시정지할 수 있어요.");
        info(page,"↻","앱이 멈춘 것 같아요","탑탭을 열고 연결을 확인해 주세요. 필요하면 접근성을 껐다 켜 주세요. 강제 종료 뒤에는 다시 연결해야 할 수 있어요.");
        info(page,"i","모든 화면에서 되나요?","일반 목록·웹·앨범형 화면을 지원하도록 만들었어요. 게임, 보안 화면, 일부 앱의 자체 스크롤은 동작하지 않을 수 있어요. 잠금 화면과 권한 허용 대화상자에서는 쉬어요.");gap(page,18);
        LinearLayout privacy=Ui.card(this,Ui.WHITE);text(privacy,"필요한 동작만, 기기 안에서",16,Ui.INK,true);gap(privacy,8);
        text(privacy,"인터넷·사진·연락처 권한을 요청하지 않아요. 화면 내용을 저장하거나 수집하지 않고, 사용 설정만 기기에 보관해요. 접근성은 현재 화면의 스크롤 위치와 영역을 확인하는 데 사용해요.",13,Ui.MUTED,false);Ui.full(page,privacy);gap(page,20);
        String version="";
        try { version=getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(android.content.pm.PackageManager.NameNotFoundException ignored) { }
        text(page,"탑탭 "+version+"  ·  Made for a lighter scroll",11,Ui.MUTED,false);
    }
}
