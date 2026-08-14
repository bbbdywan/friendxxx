package com.xzh.friendxxx.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.mapper.UserMapper;
import com.xzh.friendxxx.model.dto.UserDTO;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private PasswordEncoder passwordEncoder;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        userService = new UserServiceImpl(userMapper, passwordEncoder);
    }

    @Test
    void missingUserReturnsBusinessErrorInsteadOfNullPointer() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("missing", "password")));
    }

    @Test
    void legacyPlaintextPasswordIsUpgradedAfterSuccessfulLogin() {
        User user = user(1L, "legacy-password");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertDoesNotThrow(() -> userService.login(loginRequest("legacy", "legacy-password")));

        verify(userMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void bcryptPasswordDoesNotTriggerAnotherUpgrade() {
        User user = user(2L, passwordEncoder.encode("secret"));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertDoesNotThrow(() -> userService.login(loginRequest("encoded", "secret")));

        verify(userMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void wrongPasswordIsRejected() {
        User user = user(3L, passwordEncoder.encode("correct"));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("user", "wrong")));
    }

    private UserDTO loginRequest(String account, String password) {
        UserDTO request = new UserDTO();
        request.setUserAccount(account);
        request.setUserpassword(password);
        return request;
    }

    private User user(Long id, String password) {
        User user = new User();
        user.setId(id);
        user.setUserPassword(password);
        user.setIsDelete(0);
        return user;
    }
}
