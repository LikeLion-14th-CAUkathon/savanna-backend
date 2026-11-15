package com.savanna.backend.invitation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class InvitationUrlFactory {
    private final String template;
    public InvitationUrlFactory(@Value("${app.invitation.url-template:}") String template) {
        this.template=template.isBlank()?"https://savanna-frontend.vercel.app/invite/{token}":template.trim();
        if(!this.template.isEmpty()) {
            URI uri=URI.create(this.template.replace("{token}","placeholder"));
            if(!this.template.contains("{token}") || uri.getHost()==null || uri.getUserInfo()!=null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
                throw new IllegalArgumentException("Invalid app.invitation.url-template: HTTP(S) URL with {token} required");
        }
    }
    public String create(String token) {
        return template.isEmpty()?null:template.replace("{token}",URLEncoder.encode(token,StandardCharsets.UTF_8));
    }
}
