
package com.savanna.backend.service;

import com.savanna.backend.domain.User;
import com.savanna.backend.domain.UserRegion;
import com.savanna.backend.dto.request.UserUpdateRequestDto;
import com.savanna.backend.dto.response.UserResponseDto;
import com.savanna.backend.repository.UserRepository;
import com.savanna.backend.repository.UserRegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRegionRepository userRegionRepository;

    @Transactional(readOnly = true)
    public UserResponseDto getMyInfo(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "사용자를 찾을 수 없습니다."
                        )
                );

        List<String> regions = userRegionRepository
                .findAllByUser_UserId(userId)
                .stream()
                .map(UserRegion::getRegionCode)
                .toList();

        return UserResponseDto.from(user, regions);
    }

    @Transactional
    public UserResponseDto updateMyInfo(
            Long userId,
            UserUpdateRequestDto request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "사용자를 찾을 수 없습니다."
                        )
                );

        // 1. 사용자 기본 정보 수정
        user.updateProfile(
                request.name().trim(),
                request.universityName(),
                request.enrollmentStatus()
        );

        // 2. 동물 캐릭터 수정
        // 요청에서 생략하면 기존 캐릭터 유지
        user.updateCharacter(request.characterId());

        // 3. 지역 정보 수정
        List<String> regions;

        if (request.regions() != null) {

            // 전달된 지역의 중복 제거
            regions = request.regions()
                    .stream()
                    .map(String::trim)
                    .distinct()
                    .toList();

            // 기존 지역 삭제
            var oldRegions = userRegionRepository
                    .findAllByUser_UserId(userId);

            userRegionRepository.deleteAll(oldRegions);
            userRegionRepository.flush();

            // 새로운 지역 저장
            var newRegions = regions.stream()
                    .map(regionCode ->
                            new UserRegion(user, regionCode)
                    )
                    .toList();

            userRegionRepository.saveAll(newRegions);

        } else {

            // 지역 필드가 없으면 기존 지역 유지
            regions = userRegionRepository
                    .findAllByUser_UserId(userId)
                    .stream()
                    .map(UserRegion::getRegionCode)
                    .toList();
        }

        // 4. 수정 결과 반환
        return UserResponseDto.from(user, regions);
    }
}