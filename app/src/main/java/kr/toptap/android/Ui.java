package kr.toptap.android;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = 0xffF7F8FA, INK = 0xff17243A, MUTED = 0xff59677B, BLUE = 0xff315BEE,
        PALE = 0xffE9EFFE, WHITE = 0xffFFFFFF, LINE = 0xffE2E7EF, GREEN = 0xff18724C;
    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(c, radius)); return drawable;
    }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    static TextView text(Context c, String value, float size, int color, boolean bold) {
        TextView v = new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setFontFeatureSettings("kern");
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setLineSpacing(dp(c, 2), 1f); v.setIncludeFontPadding(true); return v;
    }
    static Button button(Context c, String text, boolean primary, Runnable action) {
        Button b = new Button(c); b.setText(text); b.setAllCaps(false); b.setTextSize(15); b.setTextColor(primary ? WHITE : BLUE);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22315bee), shape(c, primary ? BLUE : PALE, 14), null));
        b.setMinHeight(dp(c, 52)); b.setMinimumHeight(dp(c, 52)); b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        b.setStateListAnimator(null); b.setOnClickListener(v -> action.run()); return b;
    }
    static void gap(LinearLayout parent, int dp) { View spacer = new View(parent.getContext()); parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(parent.getContext(), dp))); }
    static void full(LinearLayout parent, View child) { parent.addView(child, new LinearLayout.LayoutParams(-1, -2)); }
    static LinearLayout card(Context c, int color) {
        LinearLayout card = column(c); card.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
        GradientDrawable bg = shape(c, color, 22); if (color == WHITE) bg.setStroke(dp(c, 1), LINE);
        card.setBackground(bg); return card;
    }
    static void insets(Activity activity, View root) {
        if (Build.VERSION.SDK_INT >= 30) activity.getWindow().setDecorFitsSystemWindows(false);
        else activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
}
