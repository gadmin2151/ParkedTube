package dev.codex.aaparkedyoutube;

import org.junit.Test;

import static org.junit.Assert.*;

public class BrowserLibraryTest {
    @Test public void searchHistoryIsBoundedDeduplicatedAndRecentFirst() {
        BrowserLibrary library = new BrowserLibrary();
        for (int i = 0; i < 20; i++) library.recordSearch("query " + i);
        library.recordSearch("  QUERY   18  ");
        library.recordSearch(" \n ");
        assertEquals(8, library.searches().size());
        assertEquals("QUERY 18", library.searches().get(0));
        assertEquals("query 19", library.searches().get(1));
        library.clearSearches();
        assertTrue(library.searches().isEmpty());
    }

    @Test public void bookmarksRejectExternalAndAuthenticationPages() {
        BrowserLibrary library = new BrowserLibrary();
        assertFalse(library.addBookmark("https://accounts.google.com/", "Login"));
        assertFalse(library.addBookmark("https://youtube.com.attacker.test/", "Fake"));
        assertFalse(library.addBookmark("javascript:alert(1)", "Fake"));
        assertFalse(library.addBookmark(null, "Empty"));
        assertTrue(library.addBookmark("https://www.youtube.com/watch?v=abc", null));
        assertEquals("YouTube", library.bookmarks().get(0).title);
    }

    @Test public void bookmarksCanBeUpdatedRemovedAndDoNotGrowIndefinitely() {
        BrowserLibrary library = new BrowserLibrary();
        for (int i = 0; i < 45; i++) library.addBookmark("https://youtu.be/" + i, "Video " + i);
        assertEquals(40, library.bookmarks().size());
        library.addBookmark("https://youtu.be/44", "Renamed");
        assertEquals(40, library.bookmarks().size());
        assertEquals("Renamed", library.bookmarks().get(0).title);
        library.removeBookmark("https://youtu.be/44");
        assertFalse(library.isBookmarked("https://youtu.be/44"));
        assertEquals(39, library.bookmarks().size());
    }

    @Test public void queryLimitDoesNotSplitSurrogatePair() {
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 199; i++) input.append('a');
        input.appendCodePoint(0x1F600);
        assertEquals(199, BrowserLibrary.cleanQuery(input.toString()).length());
        assertEquals("", BrowserLibrary.cleanQuery(null));
    }
}
