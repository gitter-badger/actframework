package act.util;

import act.TestBase;
import act.app.App;
import act.app.util.AppCrypto;
import act.conf.AppConfig;
import org.junit.Before;
import org.junit.Test;
import org.osgl.http.H;

import static org.mockito.Mockito.*;

/**
 * Test {@link SessionManager}
 */
public class SessionManagerTest extends TestBase {
    App app;
    AppConfig config;
    AppCrypto crypto;
    SessionManager.CookieResolver resolver;
    H.Session session;
    H.Flash flash;

    @Before
    public void prepare() {
        config = mock(AppConfig.class);
        when(config.secret()).thenReturn("secret");
        crypto = new AppCrypto(config);
        app = mock(App.class);
        when(app.config()).thenReturn(config);
        when(app.crypto()).thenReturn(crypto);
        when(app.sign(anyString())).thenCallRealMethod();
        when(app.encrypt(anyString())).thenCallRealMethod();
        when(app.decrypt(anyString())).thenCallRealMethod();
        resolver = new SessionManager.CookieResolver(app);
        session = new H.Session();
        session.put("foo", "bar");
        flash = new H.Flash();
        flash.put("foo", "bar");
    }

    @Test
    public void testSignSessionWithOnePair() {
        String content = resolver.dissolveIntoCookieContent(session, true);
        H.Session session1 = new H.Session();
        resolver.resolveFromCookieContent(session1, content, true);
        eq("bar", session1.get("foo"));
    }

    @Test
    public void testSignSessionWithMultiplePairs() {
        session.put("hello", "world");
        String content = resolver.dissolveIntoCookieContent(session, true);
        H.Session session1 = new H.Session();
        resolver.resolveFromCookieContent(session1, content, true);
        eq("bar", session1.get("foo"));
        eq("world", session1.get("hello"));
    }

    @Test
    public void testCryptoSession() {
        when(config.encryptSession()).thenReturn(true);
        resolver = new SessionManager.CookieResolver(app);
        String content = resolver.dissolveIntoCookieContent(session, true);
        H.Session session1 = new H.Session();
        resolver.resolveFromCookieContent(session1, content, true);
        eq("bar", session1.get("foo"));
    }

}
