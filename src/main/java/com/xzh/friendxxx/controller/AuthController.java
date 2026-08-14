package com.xzh.friendxxx.controller;

import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.model.dto.LoginDTO;
import com.xzh.friendxxx.model.dto.HrLoginDTO;
import com.xzh.friendxxx.model.dto.UserDTO;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.LoginVO;
import com.xzh.friendxxx.model.vo.UserVO;
import com.xzh.friendxxx.service.JwtService;
import com.xzh.friendxxx.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "认证模块", description = "JWT认证接口")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;

    public AuthController(UserService userService, JwtService jwtService,
                          PasswordEncoder passwordEncoder, RabbitTemplate rabbitTemplate) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/guest")
    public Result<LoginVO> guest(@RequestBody HrLoginDTO loginDTO) {
        if (loginDTO == null || !org.springframework.util.StringUtils.hasText(loginDTO.getUsername())) {
            return Result.error("昵称不能为空");
        }
        User guest = new User();
        guest.setUsername(loginDTO.getUsername().trim());
        guest.setUserAccount("guest_" + UUID.randomUUID().toString().replace("-", ""));
        guest.setUserPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        guest.setAvatarUrl("https://mandx.oss-cn-hangzhou.aliyuncs.com/friendxxx/2025-07-31/avatar/1753959433013.jpg");
        guest.setTags("体验用户");
        guest.setPhone("00000000000");
        guest.setEmail(guest.getUserAccount() + "@guest.local");
        if (!userService.save(guest) || guest.getId() == null) {
            return Result.error("体验用户创建失败");
        }
        UserVO user = toUserVO(guest);
        String token = jwtService.createToken(guest.getId());
        rabbitTemplate.convertAndSend(
                MQConfig.EXCHANGE_USER_TTL,
                MQConfig.ROUTINGKEY_USER_REGISTER,
                guest.getUserAccount(),
                message -> {
                    message.getMessageProperties().setExpiration("1800000");
                    return message;
                });
        return Result.success(LoginVO.builder()
                .accessToken(token)
                .expiresIn(jwtService.getTtlSeconds())
                .user(user)
                .build());
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUserAccount(loginDTO == null ? null : loginDTO.getUserAccount());
        userDTO.setUserpassword(loginDTO == null ? null : loginDTO.getUserPassword());
        User user = userService.login(userDTO);
        String token = jwtService.createToken(user.getId());
        return Result.success(LoginVO.builder()
                .accessToken(token)
                .expiresIn(jwtService.getTtlSeconds())
                .user(toUserVO(user))
                .build());
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success("退出成功");
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        User user = userService.getById(com.xzh.friendxxx.common.context.BaseContext.getCurrentId());
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(toUserVO(user));
    }

    private UserVO toUserVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .userName(user.getUsername())
                .userAccount(user.getUserAccount())
                .avatar(user.getAvatarUrl())
                .tags(user.getTags())
                .background(user.getBackground())
                .signature(user.getSignature())
                .age(user.getAge())
                .gender(user.getGender())
                .zodiac(user.getZodiac())
                .height(user.getHeight())
                .profession(user.getProfession())
                .education(user.getEducation())
                .hometown(user.getHometown())
                .relationshipStatus(user.getRelationshipStatus())
                .userRole(user.getUserRole())
                .build();
    }
}
