package com.egastro.mapper;

import com.egastro.dto.response.UserResponse;
import com.egastro.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }
}
