package dev.codex.aaparkedyoutube;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class BrowserPreferences {
    private final SharedPreferences preferences;
    final BrowserLibrary library = new BrowserLibrary();

    BrowserPreferences(Context context) {
        preferences = context.getSharedPreferences("browser", Context.MODE_PRIVATE);
        try {
            JSONArray searches = new JSONArray(preferences.getString("searches", "[]"));
            for (int i = Math.min(searches.length(), BrowserLibrary.MAX_SEARCHES) - 1; i >= 0; i--) {
                library.recordSearch(searches.getString(i));
            }
        } catch (JSONException error) {
            Log.w("ParkedTube", "Discarding invalid local search history");
            preferences.edit().remove("searches").apply();
        }
        try {
            JSONArray bookmarks = new JSONArray(preferences.getString("bookmarks", "[]"));
            for (int i = Math.min(bookmarks.length(), BrowserLibrary.MAX_BOOKMARKS) - 1; i >= 0; i--) {
                JSONObject page = bookmarks.getJSONObject(i);
                library.addBookmark(page.getString("url"), page.getString("title"));
            }
        } catch (JSONException error) {
            Log.w("ParkedTube", "Discarding invalid local bookmarks");
            preferences.edit().remove("bookmarks").apply();
        }
    }

    void saveLibrary() {
        JSONArray bookmarks = new JSONArray();
        for (BrowserLibrary.Page page : library.bookmarks()) {
            JSONObject item = new JSONObject();
            try {
                item.put("url", page.url);
                item.put("title", page.title);
            } catch (JSONException error) {
                throw new IllegalStateException("Cannot encode local bookmark", error);
            }
            bookmarks.put(item);
        }
        preferences.edit().putString("bookmarks", bookmarks.toString())
                .putString("searches", new JSONArray(library.searches()).toString()).apply();
    }

    boolean resumeEnabled() { return preferences.getBoolean("resume", true); }
    void setResumeEnabled(boolean enabled) {
        SharedPreferences.Editor edit = preferences.edit().putBoolean("resume", enabled);
        if (!enabled) edit.remove("last_url");
        edit.apply();
    }
    String startUrl(String fallback) {
        String url = preferences.getString("last_url", fallback);
        return resumeEnabled() && TrustedUrlPolicy.isYouTubeUrl(url) ? url : fallback;
    }
    void rememberPage(String url) {
        if (resumeEnabled() && TrustedUrlPolicy.isYouTubeUrl(url) && url.length() <= 4096) {
            preferences.edit().putString("last_url", url).apply();
        }
    }
    int textZoom() { return Math.max(90, Math.min(130, preferences.getInt("text_zoom", 100))); }
    void setTextZoom(int zoom) { preferences.edit().putInt("text_zoom", zoom).apply(); }
    boolean englishKeyboard() { return preferences.getBoolean("english_keyboard", false); }
    void setEnglishKeyboard(boolean english) { preferences.edit().putBoolean("english_keyboard", english).apply(); }
}
