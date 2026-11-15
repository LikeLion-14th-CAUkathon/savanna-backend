package com.savanna.backend.announcement;
import com.savanna.backend.invitation.InvitationUrlFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InvitationUrlFactoryTest {
    @Test void usesCanonicalDomainAndSupportsExplicitLocalTemplate() {
        assertEquals("https://savanna-frontend.vercel.app/invite/test.token",new InvitationUrlFactory("").create("test.token"));
        assertEquals("http://localhost:5173/invite/a%2Fb",new InvitationUrlFactory("http://localhost:5173/invite/{token}").create("a/b"));
        assertThrows(IllegalArgumentException.class,()->new InvitationUrlFactory("javascript:{token}"));
        assertThrows(IllegalArgumentException.class,()->new InvitationUrlFactory("https://example.invalid/invite"));
    }
}
