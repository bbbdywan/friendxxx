package com.xzh.friendxxx.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.config.MQConfig;
import com.xzh.friendxxx.constant.ErrorConstant;
import com.xzh.friendxxx.constant.SessionConstant;
import com.xzh.friendxxx.model.dto.HrLoginDTO;
import com.xzh.friendxxx.model.dto.UserDTO;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.GetuUserVO;
import com.xzh.friendxxx.model.vo.UserVO;
import com.xzh.friendxxx.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson2.JSON;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理模块", description = "提供用户相关的接口")
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    RedisTemplate<String, String> redisTemplate;
    @GetMapping("/tagsList")
    @Operation(summary = "获取用户标签列表", description = "根据用户标签查询用户信息")
    public Result<List<User>> tagsList(){
        String json = redisTemplate.opsForValue().get("user:list"); // 正常返回 JSON 字符串

        if (StringUtils.isNotBlank(json)) {
            return Result.success(JSON.parseArray(json, User.class));
        }
        List<User> tagsList = userService.findUserByTag();
        redisTemplate.opsForValue().set("user:list", JSON.toJSONString(tagsList), 30, TimeUnit.MINUTES);
        return Result.success(tagsList);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录接口")
    public Result<UserVO> login(@RequestBody UserDTO userDTO, HttpServletRequest request){
        User user = userService.login(userDTO);

        // 获取或创建Session
        HttpSession session = request.getSession(true);

        // 将用户信息存入Session
        session.setAttribute(SessionConstant.USER_ID, user.getId());
        session.setAttribute(SessionConstant.USER_NAME, user.getUsername());
        session.setAttribute(SessionConstant.USER_ACCOUNT, user.getUserAccount());
        session.setAttribute(SessionConstant.USER_AVATAR, user.getAvatarUrl());
        session.setAttribute(SessionConstant.USER_TAGS, user.getTags());

        // 设置Session超时时间（30分钟）
        session.setMaxInactiveInterval(1800);

        UserVO build = UserVO.builder()
                .id(user.getId())
                .userName(user.getUsername())
                .userAccount(user.getUserAccount())
                .avatar(user.getAvatarUrl())
                .tags(user.getTags())
                .build();
        return Result.success(build);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出接口")
    public Result<String> logout(HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate(); // 销毁Session
        }
        return Result.success("登出成功");
    }


    @PostMapping("/hrlogin")
    @Operation(summary = "HR登录", description = "HR登录接口")
    public Result<UserVO> hrlogin(@RequestBody HrLoginDTO hrLoginDTO, HttpServletRequest request)
    {
        String username = hrLoginDTO.getUsername();
        String AvatarUrl="http://mandx.oss-cn-hangzhou.aliyuncs.com/friendxxx/2025-07-31/avatar/1753959433013.jpg";
        hrLoginDTO.setAvatarUrl(AvatarUrl);
        String Tags="HR";
        hrLoginDTO.setTags(Tags);
        String UserAccount = "user" + System.currentTimeMillis();
        hrLoginDTO.setUserAccount(UserAccount);
        hrLoginDTO.setUserPassword("123456");

        User user = new User();
        user.setUsername(hrLoginDTO.getUsername());
        user.setUserAccount(hrLoginDTO.getUserAccount());
        user.setUserPassword(hrLoginDTO.getUserPassword());
        user.setAvatarUrl(hrLoginDTO.getAvatarUrl());
        user.setTags(hrLoginDTO.getTags());
        user.setPhone("12345678901");
        user.setEmail(hrLoginDTO.getUsername());
        userService.save(user);

        //使用用户名去查id
        long hrid  = user.getId();

        log.info("HR登录 - 用户ID: {}, 账号: {}", hrid, UserAccount);
        // 获取或创建Session
        HttpSession session = request.getSession(true);

        // 将用户信息存入Session
        session.setAttribute(SessionConstant.USER_ID, hrid);
        session.setAttribute(SessionConstant.USER_NAME,username);
        session.setAttribute(SessionConstant.USER_ACCOUNT, UserAccount);
        session.setAttribute(SessionConstant.USER_AVATAR, AvatarUrl);
        session.setAttribute(SessionConstant.USER_TAGS, Tags);

        // 设置Session超时时间（30分钟）
        session.setMaxInactiveInterval(1800);

        UserVO build = UserVO.builder()
                .id(hrid)
                .userName(username)
                .userAccount(UserAccount)
                .avatar(AvatarUrl)
                .tags(Tags)
                .build();


            rabbitTemplate.convertAndSend(
                    MQConfig.EXCHANGE_USER_TTL,                      // 新的交换机
                    MQConfig.ROUTINGKEY_USER_REGISTER,
                    "限时时间唯一标识-"+UserAccount,
                    message -> {
                        // 设置过期时间（比如 5 分钟 = 300000 毫秒）
                        message.getMessageProperties().setExpiration("1800000");
                        return message;
                    }
            );
        return Result.success(build);
    }


    @PostMapping("/update")
    @Operation(summary = "用户更新", description = "用户更新接口")
    public Result<Integer> update(@RequestBody User user){
        //从threadlocal中获取当前用户id
        long userid= BaseContext.getCurrentId();
        if(userid!=user.getId())
            return Result.error(ErrorConstant.USER_NOT_AUTH);
        //更新用户信息
        int i = userService.updateuser(user);
        redisTemplate.delete("user:list");
        return Result.success(i);
    }

    @GetMapping("/profile")
    @Operation(summary = "获取用户信息", description = "获取用户信息接口")
    public Result<UserVO> profile()
    {
        long userid = BaseContext.getCurrentId();
        User user = userService.getById(userid);
        UserVO build = UserVO.builder()
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
                .build();
        return Result.success(build);
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前登录用户信息", description = "从Session中获取当前登录用户信息")
    public Result<UserVO> getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Result.error("用户未登录");
        }

        Long userId = (Long) session.getAttribute(SessionConstant.USER_ID);
        String userName = (String) session.getAttribute(SessionConstant.USER_NAME);
        String userAccount = (String) session.getAttribute(SessionConstant.USER_ACCOUNT);
        String avatar = (String) session.getAttribute(SessionConstant.USER_AVATAR);
        String tags = (String) session.getAttribute(SessionConstant.USER_TAGS);

        UserVO userVO = UserVO.builder()
                .id(userId)
                .userName(userName)
                .userAccount(userAccount)
                .avatar(avatar)
                .tags(tags)
                .build();

        return Result.success(userVO);
    }




    @PostMapping("/update/image")
    @Operation(summary = "用户更新头像", description = "用户更新头像接口")
    public Result<String> updateImage(@RequestParam("file") MultipartFile file,@RequestParam("type") String type) {
        //从threadlocal中获取当前用户id
        long userid = BaseContext.getCurrentId();
        
        //上传头像并获取URL
        String avatarUrl = userService.uploadAvatar(file);

        //更新用户头像URL到数据库
        User user = new User();
        user.setId(userid);
        if ("avatar".equals(type)) {
            user.setAvatarUrl(avatarUrl);
        } else if ("background".equals(type)) {
            user.setBackground(avatarUrl);
        } else {
            return Result.error("无效的图片类型参数");
        }
        userService.updateById(user);
        
        return Result.success(avatarUrl);
    }
    @GetMapping("/{userID}")
    @Operation(summary = "查看用户信息", description = "查看用户信息接口")
    public Result<GetuUserVO> getuser(@PathVariable("userID") Long userID)
    {
        User getone = userService.getById(userID);
        GetuUserVO build=GetuUserVO.builder().id(getone.getId()).userName(getone.getUsername()).
                userAccount(getone.getUserAccount()).avatar(getone.getAvatarUrl()).tags(getone.getTags()).
                background(getone.getBackground()).signature(getone.getSignature()).age(getone.getAge()).
                gender(getone.getGender()).zodiac(getone.getZodiac()).height(getone.getHeight()).
                profession(getone.getProfession()).education(getone.getEducation()).hometown(getone.getHometown())
                .relationshipStatus(getone.getRelationshipStatus()).build();
        return Result.success(build);

    }

    @RabbitListener(queues = MQConfig.QUEUE_USER_DELETE)
    public void handler(String message){
        try {
            String[] split = message.split("-");
            if (split.length >= 2) {
                String Account = split[1];
                userService.removebyAccount(Account);
            } else {
                System.err.println("消息格式错误，期望格式: 'prefix-deleteTtl'，实际: " + message);
            }
        } catch (Exception e) {
            System.err.println("处理延迟消息失败: " + message + ", 错误: " + e.getMessage());
        }
    }
}
