
package com.savanna.backend.service;

import com.savanna.backend.domain.User;
import com.savanna.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService
        implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserRepository userRepository;
    private final OidcUserService oidcUserService =
            new OidcUserService();

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) {

        // Google에서 인증된 사용자 정보 가져오기
        OidcUser oidcUser =
                oidcUserService.loadUser(userRequest);

        String googleSub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String googleName = oidcUser.getFullName();

        // 기존 회원 조회, 없으면 신규 USER 생성
        userRepository.findByGoogleSub(googleSub)
                .orElseGet(() ->
                        userRepository.save(
                                new User(email, googleSub, googleName)
                        )
                );

        return oidcUser;
    }
}