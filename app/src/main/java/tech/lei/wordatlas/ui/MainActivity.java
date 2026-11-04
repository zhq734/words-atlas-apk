package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;

/**
 * 应用主框架：五个标签页 + 底部导航。
 *
 * <p>每个标签页首次进入时才构建，切换时仅改变可见性，因此筛选条件、滚动位置等
 * 页面状态在切换后仍然保留。</p>
 *
 * @author zhenghq
 */
public class MainActivity extends Activity {

    private FrameLayout content;
    private LinearLayout navBar;
    private Page[] pages;
    private View[] navItems;
    private int current = -1;

    /** 页面基类。 */
    public abstract static class Page extends LinearLayout {
        protected final MainActivity host;
        private boolean built;

        /**
         * @param host 主界面
         */
        public Page(MainActivity host) {
            super(host);
            this.host = host;
            setOrientation(LinearLayout.VERTICAL);
            setBackgroundColor(Theme.bg());
        }

        /** 首次显示时构建界面。 */
        public final void ensureBuilt() {
            if (!built) {
                built = true;
                build();
            }
        }

        /** 由子类实现具体界面构建。 */
        protected abstract void build();

        /**
         * 处理返回键。
         *
         * @return 已消费返回 true
         */
        public boolean onBack() {
            return false;
        }

        /** 页面显示时回调。 */
        public void onShown() {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.apply(!"light".equals(Store.get(this).theme()));
        rebuildUi();
        Repo.get(this).checkUpdate(() -> {
            if (pages != null && current >= 0) {
                pages[current].invalidate();
            }
        });
    }

    /**
     * 重建整个界面，主题切换后调用。
     */
    public void rebuildUi() {
        Theme.apply(!"light".equals(Store.get(this).theme()));
        Theme.bars(this);
        int keep = current;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg());

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        navBar = buildNavBar();
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 62), Gravity.BOTTOM);
        root.addView(navBar, navLp);

        setContentView(root);
        applyInsets(root, navBar);

        pages = new Page[]{new TodayPage(this), new AtlasPage(this), new BooksPage(this),
                new PracticePage(this), new MePage(this)};
        for (Page p : pages) {
            content.addView(p, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            p.setVisibility(View.GONE);
        }
        current = -1;
        select(keep < 0 ? 0 : keep);
    }

    /**
     * 构建底部导航栏。
     *
     * @return 导航栏视图
     */
    private LinearLayout buildNavBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(Theme.surface());
        bar.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));

        int[] icons = {R.drawable.ic_today, R.drawable.ic_atlas, R.drawable.ic_books,
                R.drawable.ic_practice, R.drawable.ic_me};
        String[] labels = {"今日", "图鉴", "书房", "练习", "我的"};
        navItems = new View[icons.length];
        for (int i = 0; i < icons.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(Theme.textDim());
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    Theme.dp(this, 22), Theme.dp(this, 22));
            item.addView(icon, iconLp);

            TextView label = Theme.text(this, labels[i], 10, Theme.textDim(), false);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setSingleLine(true);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = Theme.dp(this, 3);
            item.addView(label, labelLp);

            item.setOnClickListener(v -> select(index));
            Theme.pressable(item);
            navItems[i] = item;
            bar.addView(item);
        }
        return bar;
    }

    /**
     * 切换标签页。
     *
     * @param index 目标下标
     */
    public void select(int index) {
        if (pages == null || index < 0 || index >= pages.length) {
            return;
        }
        pages[index].ensureBuilt();
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        current = index;
        for (int i = 0; i < navItems.length; i++) {
            View item = navItems[i];
            ImageView icon = (ImageView) ((ViewGroup) item).getChildAt(0);
            TextView label = (TextView) ((ViewGroup) item).getChildAt(1);
            boolean on = i == index;
            icon.setColorFilter(on ? Theme.accent() : Theme.textDim());
            label.setTextColor(on ? Theme.accent() : Theme.textDim());
        }
        pages[index].onShown();
    }

    /**
     * 让内容避开系统栏，导航栏贴住底部安全区。
     *
     * @param root 根视图
     * @param nav 导航栏
     */
    private void applyInsets(View root, View nav) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            content.setPadding(left, top, right, bottom + Theme.dp(this, 62));
            ((FrameLayout.LayoutParams) nav.getLayoutParams()).bottomMargin = bottom;
            nav.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));
            view.requestApplyInsets();
            return insets;
        });
    }

    @Override
    public void onBackPressed() {
        if (current >= 0 && pages[current].onBack()) {
            return;
        }
        if (current != 0) {
            select(0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从词条详情等页面返回时重新读一遍当前页数据，收藏、已学等统计才不会停留在旧值
        if (pages != null && current >= 0) {
            pages[current].onShown();
        }
    }

    /**
     * 打开词条详情页。
     *
     * @param id 词 id
     */
    public void openWord(String id) {
        Intent intent = new Intent(this, WordDetailActivity.class);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    /**
     * 打开学习页。
     *
     * @param mode 学习模式：new 新词、review 复习、fav 收藏
     */
    public void openLearn(String mode) {
        Intent intent = new Intent(this, LearnActivity.class);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

    /**
     * 修改设置后刷新所有页面（主题切换时重建）。
     */
    public void onSettingsChanged() {
        rebuildUi();
    }

    /**
     * 让当前页面重新读取数据。
     */
    public void refreshPages() {
        if (pages != null && current >= 0) {
            pages[current].invalidate();
        }
    }
}
