package dev.codex.aaparkedyoutube;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small bounded library. Most recently used entries come first. */
final class BrowserLibrary {
    static final int MAX_SEARCHES = 8;
    static final int MAX_BOOKMARKS = 40;
    static final int MAX_QUERY_LENGTH = 200;
    private final List<String> searches = new ArrayList<>();
    private final List<Page> bookmarks = new ArrayList<>();

    static final class Page {
        final String url;
        final String title;

        Page(String url, String title) {
            this.url = url;
            String label = title == null ? "" : title.trim();
            this.title = label.isEmpty() ? "YouTube" : label.substring(0, Math.min(160, label.length()));
        }
    }

    static String cleanQuery(String query) {
        String value = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (value.length() > MAX_QUERY_LENGTH) {
            int end = MAX_QUERY_LENGTH;
            if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
            value = value.substring(0, end);
        }
        return value;
    }

    void recordSearch(String query) {
        String clean = cleanQuery(query);
        if (clean.isEmpty()) return;
        searches.removeIf(item -> item.equalsIgnoreCase(clean));
        searches.add(0, clean);
        if (searches.size() > MAX_SEARCHES) searches.remove(searches.size() - 1);
    }

    void clearSearches() { searches.clear(); }

    List<String> searches() { return Collections.unmodifiableList(new ArrayList<>(searches)); }

    boolean isBookmarked(String url) {
        for (Page page : bookmarks) if (page.url.equals(url)) return true;
        return false;
    }

    boolean addBookmark(String url, String title) {
        if (!TrustedUrlPolicy.isYouTubeUrl(url) || url.length() > 4096) return false;
        removeBookmark(url);
        bookmarks.add(0, new Page(url, title));
        if (bookmarks.size() > MAX_BOOKMARKS) bookmarks.remove(bookmarks.size() - 1);
        return true;
    }

    void removeBookmark(String url) { bookmarks.removeIf(page -> page.url.equals(url)); }

    List<Page> bookmarks() { return Collections.unmodifiableList(new ArrayList<>(bookmarks)); }
}
