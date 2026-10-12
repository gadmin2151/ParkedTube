package dev.codex.aaparkedyoutube;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Native navigation and local library, independent of YouTube's page markup. */
final class BrowserControls {
    interface Actions {
        void back();
        void home();
        void navigate(String url);
        void search(String query);
        void carInput(String query);
        void reload();
        void textZoom(int zoom);
        boolean carDisplay();
    }

    private static final int BACKGROUND = Color.rgb(15, 17, 20);
    private static final int SURFACE = Color.rgb(27, 30, 35);
    private static final int SECONDARY = Color.rgb(167, 176, 188);
    private static final int ACCENT = Color.rgb(255, 67, 83);
    private final Context context;
    private final BrowserPreferences preferences;
    private final Actions actions;
    private final LinearLayout toolbar;
    private final FrameLayout panel;
    private final TextView pageTitle;
    private final ImageButton backButton;
    private final ImageButton bookmarkButton;
    private String currentUrl;
    private String currentTitle = "YouTube";
    private EditText searchInput;

    BrowserControls(Context context, BrowserPreferences preferences, Actions actions) {
        this.context = context;
        this.preferences = preferences;
        this.actions = actions;
        toolbar = row();
        toolbar.setPadding(dp(6), 0, dp(6), 0);
        toolbar.setBackgroundColor(BACKGROUND);
        backButton = icon(R.drawable.ic_back, R.string.go_back, actions::back);
        toolbar.addView(backButton);
        toolbar.addView(icon(R.drawable.ic_home, R.string.home, actions::home));
        pageTitle = text("ParkedTube", 16, Color.WHITE);
        pageTitle.setTypeface(Typeface.DEFAULT_BOLD);
        pageTitle.setSingleLine(true);
        pageTitle.setEllipsize(TextUtils.TruncateAt.END);
        pageTitle.setPadding(dp(10), 0, dp(4), 0);
        toolbar.addView(pageTitle, new LinearLayout.LayoutParams(0, dp(56), 1));
        toolbar.addView(icon(R.drawable.ic_search, R.string.search_placeholder, this::showSearch));
        bookmarkButton = icon(R.drawable.ic_bookmark, R.string.bookmarks, this::showBookmarks);
        toolbar.addView(bookmarkButton);
        toolbar.addView(icon(R.drawable.ic_more, R.string.settings, this::showSettings));
        panel = new FrameLayout(context);
        panel.setBackgroundColor(BACKGROUND);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setVisibility(View.GONE);
    }

    View toolbar() { return toolbar; }
    View panel() { return panel; }
    boolean isPanelOpen() { return panel.getVisibility() == View.VISIBLE; }

    void updatePage(String url, String title, boolean canGoBack) {
        currentUrl = url;
        currentTitle = title == null || title.trim().isEmpty() ? "YouTube" : title;
        pageTitle.setText(currentTitle);
        pageTitle.setContentDescription(currentTitle);
        backButton.setEnabled(canGoBack);
        backButton.setAlpha(canGoBack ? 1f : 0.35f);
        bookmarkButton.setColorFilter(preferences.library.isBookmarked(url) ? ACCENT : Color.WHITE);
    }

    void closePanel() {
        InputMethodManager ime = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (ime != null) ime.hideSoftInputFromWindow(panel.getWindowToken(), 0);
        panel.setVisibility(View.GONE);
        searchInput = null;
    }

    void showSearch() {
        LinearLayout body = openPanel(R.string.search_placeholder);
        LinearLayout inputRow = row();
        inputRow.setBackground(shape(SURFACE));
        searchInput = new EditText(context);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(20);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(SECONDARY);
        searchInput.setHint(R.string.search_placeholder);
        searchInput.setContentDescription(context.getString(R.string.search_placeholder));
        searchInput.setPadding(dp(14), 0, dp(8), 0);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(BrowserLibrary.MAX_QUERY_LENGTH)});
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { submitSearch(); return true; }
            return false;
        });
        inputRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(56), 1));
        inputRow.addView(icon(R.drawable.ic_close, R.string.clear_text, () -> searchInput.setText("")));
        inputRow.addView(icon(R.drawable.ic_search, R.string.search, this::submitSearch));
        body.addView(inputRow);
        if (actions.carDisplay()) {
            searchInput.setFocusable(false);
            searchInput.setCursorVisible(false);
            searchInput.setOnClickListener(view -> actions.carInput(searchInput.getText().toString()));
        }
        if (!preferences.library.searches().isEmpty()) {
            section(body, R.string.recent_searches);
            for (String query : preferences.library.searches()) {
                body.addView(action(query, () -> actions.search(query)));
            }
            body.addView(action(context.getString(R.string.clear_searches), () ->
                    new AlertDialog.Builder(context).setTitle(R.string.clear_searches)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.clear, (dialog, which) -> {
                                preferences.library.clearSearches();
                                preferences.saveLibrary();
                                showSearch();
                            }).show()));
        }
        if (!actions.carDisplay()) {
            searchInput.requestFocus();
            searchInput.post(() -> {
                if (searchInput == null || !isPanelOpen()) return;
                InputMethodManager ime = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (ime != null) ime.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            });
        }
    }

    private void submitSearch() {
        if (searchInput != null) actions.search(searchInput.getText().toString());
    }

    void updateSearchText(String query) {
        if (searchInput != null) searchInput.setText(query);
    }

    void showBookmarks() {
        LinearLayout body = openPanel(R.string.bookmarks);
        boolean saved = preferences.library.isBookmarked(currentUrl);
        Button save = action(context.getString(saved ? R.string.remove_current_bookmark : R.string.save_current_page), () -> {
            if (preferences.library.isBookmarked(currentUrl)) preferences.library.removeBookmark(currentUrl);
            else preferences.library.addBookmark(currentUrl, currentTitle);
            preferences.saveLibrary();
            updatePage(currentUrl, currentTitle, backButton.isEnabled());
            showBookmarks();
        });
        save.setEnabled(TrustedUrlPolicy.isYouTubeUrl(currentUrl));
        save.setTextColor(ACCENT);
        body.addView(save);
        if (preferences.library.bookmarks().isEmpty()) {
            TextView empty = text(context.getString(R.string.no_bookmarks), 18, SECONDARY);
            empty.setPadding(dp(12), dp(32), dp(12), dp(24));
            body.addView(empty);
        }
        for (BrowserLibrary.Page page : preferences.library.bookmarks()) {
            LinearLayout entry = row();
            Button open = action(page.title, () -> actions.navigate(page.url));
            entry.addView(open, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            ImageButton remove = icon(R.drawable.ic_delete, R.string.remove_bookmark, () -> {
                preferences.library.removeBookmark(page.url);
                preferences.saveLibrary();
                updatePage(currentUrl, currentTitle, backButton.isEnabled());
                showBookmarks();
            });
            remove.setContentDescription(context.getString(R.string.remove_named_bookmark, page.title));
            entry.addView(remove);
            body.addView(entry);
        }
    }

    private void showSettings() {
        LinearLayout body = openPanel(R.string.settings);
        body.addView(action(context.getString(R.string.refresh_page), () -> { closePanel(); actions.reload(); }));
        section(body, R.string.readability);
        RadioGroup zoom = new RadioGroup(context);
        zoom.setOrientation(LinearLayout.HORIZONTAL);
        int[] sizes = {90, 100, 115, 130};
        for (int size : sizes) {
            RadioButton option = new RadioButton(context);
            option.setId(View.generateViewId());
            option.setText(context.getString(R.string.zoom_percent, size));
            option.setTextColor(Color.WHITE);
            option.setButtonTintList(ColorStateList.valueOf(ACCENT));
            option.setMinHeight(dp(52));
            option.setTextSize(14);
            option.setTag(size);
            zoom.addView(option, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (size == preferences.textZoom()) zoom.check(option.getId());
        }
        zoom.setOnCheckedChangeListener((group, id) -> {
            RadioButton selected = group.findViewById(id);
            if (selected != null) {
                int value = (Integer) selected.getTag();
                preferences.setTextZoom(value);
                actions.textZoom(value);
            }
        });
        body.addView(zoom);
        CheckBox resume = new CheckBox(context);
        resume.setText(R.string.resume_last_page);
        resume.setTextColor(Color.WHITE);
        resume.setTextSize(17);
        resume.setMinHeight(dp(64));
        resume.setButtonTintList(ColorStateList.valueOf(ACCENT));
        resume.setChecked(preferences.resumeEnabled());
        resume.setOnCheckedChangeListener((button, checked) -> preferences.setResumeEnabled(checked));
        body.addView(resume);
        body.addView(action(context.getString(R.string.clear_searches), () -> {
            new AlertDialog.Builder(context).setTitle(R.string.clear_searches)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.clear, (dialog, which) -> {
                        preferences.library.clearSearches();
                        preferences.saveLibrary();
                        Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show();
                    }).show();
        }));
    }

    private LinearLayout openPanel(int title) {
        closePanel();
        panel.removeAllViews();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = row();
        header.setPadding(dp(16), dp(4), dp(8), dp(4));
        TextView heading = text(context.getString(title), 22, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(56), 1));
        header.addView(icon(R.drawable.ic_close, R.string.close, this::closePanel));
        layout.addView(header);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(body);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.addView(layout, new FrameLayout.LayoutParams(-1, -1));
        panel.setVisibility(View.VISIBLE);
        return body;
    }

    private void section(LinearLayout body, int title) {
        TextView label = text(context.getString(title), 14, SECONDARY);
        label.setPadding(dp(12), dp(24), dp(12), dp(10));
        body.addView(label);
    }

    private Button action(String title, Runnable click) {
        Button button = new Button(context);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(56));
        button.setMaxLines(2);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setBackground(ripple());
        button.setOnClickListener(view -> click.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private ImageButton icon(int drawable, int label, Runnable click) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawable);
        button.setColorFilter(Color.WHITE);
        button.setContentDescription(context.getString(label));
        button.setTooltipText(context.getString(label));
        button.setPadding(dp(13), dp(13), dp(13), dp(13));
        button.setBackground(ripple());
        button.setOnClickListener(view -> click.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private RippleDrawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, shape(Color.WHITE));
    }

    private GradientDrawable shape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(8));
        return shape;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
