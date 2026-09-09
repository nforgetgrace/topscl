package kr.toptap.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public final class DemoActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);Ui.insets(this,root);
        LinearLayout header=Ui.column(this);header.setPadding(Ui.dp(this,24),Ui.dp(this,16),Ui.dp(this,24),Ui.dp(this,16));
        Ui.full(header,Ui.button(this,"‹  돌아가기",false,this::finish));Ui.gap(header,16);
        Ui.full(header,Ui.text(this,"맨 위로 돌아오는 연습",22,Ui.INK,true));Ui.gap(header,6);
        Ui.full(header,Ui.text(this,"왼쪽 상단의 파란 선을 톡 눌러 보세요.\n접근성 연결과 탑탭 켜기가 필요해요.",13,Ui.MUTED,false));root.addView(header);
        ListView list=new ListView(this);list.setDividerHeight(Ui.dp(this,1));list.setAdapter(new BaseAdapter(){
            public int getCount(){return 200;}public Object getItem(int p){return p;}public long getItemId(int p){return p;}
            public View getView(int p,View recycled,ViewGroup parent){TextView row=Ui.text(DemoActivity.this,p==0?"↑  맨 위에 도착했어요!":"연습 목록  "+p,17,p==0?Ui.BLUE:Ui.INK,p==0);row.setPadding(Ui.dp(DemoActivity.this,24),Ui.dp(DemoActivity.this,24),Ui.dp(DemoActivity.this,24),Ui.dp(DemoActivity.this,24));return row;}
        });root.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);if(state==null)list.post(()->list.setSelection(120));
    }
}
