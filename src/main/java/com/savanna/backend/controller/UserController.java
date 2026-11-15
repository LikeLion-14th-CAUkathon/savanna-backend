package com.savanna.backend.controller;

import com.savanna.backend.dto.response.UserResponseDto;
import com.savanna.backend.dto.response.DataResponse;
import com.savanna.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.savanna.backend.dto.request.UserUpdateRequestDto;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<DataResponse<UserResponseDto>> getMyInfo(
            @AuthenticationPrincipal Long userId
    ) {
        UserResponseDto result =
                userService.getMyInfo(userId);

        return ResponseEntity.ok(
                new DataResponse<>(200,"내 정보 조회 성공",result)
        );
    }

    @PatchMapping
    public ResponseEntity<DataResponse<UserResponseDto>> updateMyInfo(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserUpdateRequestDto request
    ) {
        UserResponseDto result =
                userService.updateMyInfo(userId, request);

        return ResponseEntity.ok(
                new DataResponse<>(200,"내 정보 수정 성공",result)
        );
    }

}
