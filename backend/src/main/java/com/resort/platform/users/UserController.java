package com.resort.platform.users;

import com.resort.platform.common.PageResponse;
import com.resort.platform.users.dto.CreateUserRequest;
import com.resort.platform.users.dto.CreatedUserResponse;
import com.resort.platform.users.dto.TemporaryPasswordResponse;
import com.resort.platform.users.dto.UpdateUserRequest;
import com.resort.platform.users.dto.UpdateUserStatusRequest;
import com.resort.platform.users.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(Pageable pageable) {
        return userService.list(pageable);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PostMapping
    public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(userService.create(request));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.changeStatus(id, request.active());
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<TemporaryPasswordResponse> resetPassword(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TemporaryPasswordResponse(userService.resetPassword(id)));
    }
}
