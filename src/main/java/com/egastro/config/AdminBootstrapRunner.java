package com.egastro.config;

import com.egastro.config.properties.AdminProperties;
import com.egastro.entity.User;
import com.egastro.enums.Role;
import com.egastro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(properties.email())) {
            return;
        }
        var user = new User();
        user.setEmail(properties.email());
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setRole(Role.ADMIN);
        userRepository.save(user);
    }
}
