package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.TextView;

/**
 * 应用主题：统一颜色、圆角、间距与文字样式。
 *
 * <p>所有页面都通过这里取色，切换深浅色时只需调用 {@link #apply(boolean)} 并重建页面，
 * 不会在组件里散落硬编码颜色。</p>
 *
 * @author zhenghq
 */
public final class Theme {

    /** 是否深色模式。 */
    public static boolean dark = true;

    /** 是否使用衬线字体显示单词。 */
    public static boolean serifWord = false;

    private Theme() {
    }

    /**
     * 切换主题。
     *
     * @param isDark 是否深色
     */
    public static void apply(boolean isDark) {
        dark = isDark;
    }

    /**
     * 按当前主题设置系统栏配色与图标明暗，需在每个 Activity 创建时调用。
     *
     * @param activity 宿主 Activity
     */
    public static void bars(Activity activity) {
        Window window = activity.getWindow();
        int barColor = dark ? 0xFF0D1120 : 0xFFF7F4EE;
        window.setBackgroundDrawable(new ColorDrawable(barColor));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.setStatusBarColor(barColor);
            window.setNavigationBarColor(barColor);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final View decor = window.getDecorView();
            decor.post(() -> {
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller != null) {
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
                }
            });
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(dark ? (flags & ~light) : (flags | light));
        }
    }

    /**
     * 当前主题对应的弹窗样式，保证浅色主题下弹窗也是浅色。
     *
     * @return 样式资源 id
     */
    public static int dialogTheme() {
        return dark ? tech.lei.wordatlas.R.style.AppDialog
                : tech.lei.wordatlas.R.style.AppDialogLight;
    }

    /**
     * 背景色。
     *
     * @return 颜色值
     */
    public static int bg() {
        return dark ? 0xFF0D1120 : 0xFFF7F4EE;
    }

    /**
     * 卡片背景色。
     *
     * @return 颜色值
     */
    public static int surface() {
        return dark ? 0xFF151B2E : 0xFFFFFFFF;
    }

    /**
     * 次级背景色。
     *
     * @return 颜色值
     */
    public static int surfaceAlt() {
        return dark ? 0xFF1C2438 : 0xFFEFEAE1;
    }

    /**
     * 主文字色。
     *
     * @return 颜色值
     */
    public static int text() {
        return dark ? 0xFFF2EDE4 : 0xFF1A1F2E;
    }

    /**
     * 次要文字色。
     *
     * @return 颜色值
     */
    public static int textDim() {
        return dark ? 0xFF9AA3B8 : 0xFF6B7280;
    }

    /**
     * 强调色（金色）。
     *
     * @return 颜色值
     */
    public static int accent() {
        return dark ? 0xFFE3B96B : 0xFFB8862F;
    }

    /**
     * 强调色的柔和底。
     *
     * @return 颜色值
     */
    public static int accentSoft() {
        return dark ? 0x33E3B96B : 0x22B8862F;
    }

    /**
     * 分割线颜色。
     *
     * @return 颜色值
     */
    public static int border() {
        return dark ? 0x1FFFFFFF : 0x14000000;
    }

    /**
     * 错误色。
     *
     * @return 颜色值
     */
    public static int danger() {
        return 0xFFE06C6C;
    }

    /**
     * 成功色。
     *
     * @return 颜色值
     */
    public static int success() {
        return dark ? 0xFF7BC49A : 0xFF3F8F65;
    }

    /**
     * 常用字号转像素。
     *
     * @param ctx 上下文
     * @param sp 字号
     * @return 像素值
     */
    public static float sp(Context ctx, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, ctx.getResources().getDisplayMetrics());
    }

    /**
     * 常用间距转像素。
     *
     * @param ctx 上下文
     * @param dp 间距
     * @return 像素值
     */
    public static int dp(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    /**
     * 生成纯色圆角背景。
     *
     * @param color 填充色
     * @param radiusDp 圆角半径
     * @param ctx 上下文
     * @return drawable
     */
    public static GradientDrawable round(int color, float radiusDp, Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(ctx, radiusDp));
        return d;
    }

    /**
     * 生成带描边的圆角背景。
     *
     * @param fill 填充色
     * @param strokeColor 描边色
     * @param radiusDp 圆角半径
     * @param strokeDp 描边宽度
     * @param ctx 上下文
     * @return drawable
     */
    public static GradientDrawable roundStroke(int fill, int strokeColor, float radiusDp, float strokeDp, Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(ctx, radiusDp));
        d.setStroke(dp(ctx, strokeDp), strokeColor);
        return d;
    }

    /**
     * 创建标准文字视图。
     *
     * @param ctx 上下文
     * @param text 文本
     * @param sizeSp 字号
     * @param color 颜色
     * @param bold 是否加粗
     * @return 文本视图
     */
    public static TextView text(Context ctx, CharSequence text, float sizeSp, int color, boolean bold) {
        TextView v = new TextView(ctx);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        v.setTextColor(color);
        if (bold) {
            v.setTypeface(Typeface.DEFAULT_BOLD);
        }
        v.setIncludeFontPadding(false);
        return v;
    }

    /**
     * 创建单词展示文字，可切换衬线字体。
     *
     * @param ctx 上下文
     * @param text 文本
     * @param sizeSp 字号
     * @param color 颜色
     * @return 文本视图
     */
    public static TextView word(Context ctx, CharSequence text, float sizeSp, int color) {
        TextView v = text(ctx, text, sizeSp, color, true);
        v.setTypeface(serifWord ? Typeface.SERIF : Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    /**
     * 设置视图的点击缩放反馈。
     *
     * @param view 目标视图
     */
    public static void pressable(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /**
     * 把颜色按比例降低不透明度。
     *
     * @param color 原色
     * @param alpha 0-255
     * @return 新颜色
     */
    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
