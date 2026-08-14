package dev.codex.aaparkedyoutube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrustedUrlPolicyTest {
    @Test
    public void acceptsYouTubeAndGoogleHttpsPages() {
        assertTrue(TrustedUrlPolicy.isTrustedUrl("https://www.youtube.com/watch?v=123"));
        assertTrue(TrustedUrlPolicy.isTrustedUrl("https://m.youtube.com/"));
        assertTrue(TrustedUrlPolicy.isTrustedUrl("https://youtu.be/123"));
        assertTrue(TrustedUrlPolicy.isTrustedUrl("https://accounts.google.com/signin"));
        assertTrue(TrustedUrlPolicy.isTrustedUrl("about:blank"));
    }

    @Test
    public void rejectsCleartextExternalAndLookalikePages() {
        assertFalse(TrustedUrlPolicy.isTrustedUrl("http://youtube.com/"));
        assertFalse(TrustedUrlPolicy.isTrustedUrl("https://youtube.com.example.org/"));
        assertFalse(TrustedUrlPolicy.isTrustedUrl("https://example.org/"));
        assertFalse(TrustedUrlPolicy.isTrustedUrl("intent://watch"));
        assertFalse(TrustedUrlPolicy.isTrustedUrl("javascript:alert(1)"));
        assertFalse(TrustedUrlPolicy.isTrustedUrl(null));
    }

    @Test
    public void injectsKeyboardOnlyOnYouTubePages() {
        assertTrue(TrustedUrlPolicy.isYouTubeUrl("https://music.youtube.com/"));
        assertTrue(TrustedUrlPolicy.isYouTubeUrl("https://youtu.be/123"));
        assertFalse(TrustedUrlPolicy.isYouTubeUrl("https://accounts.google.com/"));
        assertFalse(TrustedUrlPolicy.isYouTubeUrl("http://www.youtube.com/"));
    }
}
