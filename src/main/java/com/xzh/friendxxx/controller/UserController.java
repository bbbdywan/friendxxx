package com.xzh.friendxxx.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.JwtUtil;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.constant.ErrorConstant;
import com.xzh.friendxxx.constant.JwtClaimsConstant;
import com.xzh.friendxxx.model.dto.UserDTO;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.GetuUserVO;
import com.xzh.friendxxx.model.vo.UserVO;
import com.xzh.friendxxx.properties.JwtProperties;
import com.xzh.friendxxx.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson.JSON;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
@Api(tags = "用户管理模块", description = "提供用户相关的接口")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    RedisTemplate<String, String> redisTemplate;
    @Autowired
    JwtProperties jwtProperties;
    @GetMapping("/tagsList")
    @ApiOperation(value = "获取用户标签列表", notes = "根据用户标签查询用户信息")
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
    @ApiOperation(value = "用户登录", notes = "用户登录接口")
    public Result<UserVO> login(@RequestBody UserDTO userDTO){
        User user = userService.login(userDTO);
        //jwt
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);
        UserVO build = UserVO.builder().
                id(user.getId()).userName(user.getUsername()).userAccount(user.getUserAccount()).avatar(user.getAvatarUrl()).tags(user.getTags()).token(token).build();
        return Result.success(build);
    }
    @PostMapping("/update")
    @ApiOperation(value = "用户更新", notes = "用户更新接口")
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
    @ApiOperation(value = "获取用户信息", notes = "获取用户信息接口")
    public Result<UserVO> profile()
    {
        long userid = BaseContext.getCurrentId();
        User user = userService.getById(userid);
        UserVO build = UserVO.builder().
                id(user.getId()).userName(user.getUsername()).userAccount(user.getUserAccount()).avatar(user.getAvatarUrl()).tags(user.getTags()).background(user.getBackground()).signature(user.getSignature()).age(user.getAge()).gender(user.getGender()).build();
        return Result.success(build);
    }

    @GetMapping("/{userID}")
    @ApiOperation(value = "查看用户信息", notes = "查看用户信息接口")
    public Result<GetuUserVO> getuser(@PathVariable Long userID)
    {
        User getone = userService.getById(userID);
        GetuUserVO build=GetuUserVO.builder().id(getone.getId()).userName(getone.getUsername()).userAccount(getone.getUserAccount()).avatar(getone.getAvatarUrl()).tags(getone.getTags()).background(getone.getBackground()).signature(getone.getSignature()).age(getone.getAge()).gender(getone.getGender()).build();
        return Result.success(build);

    }


    @PostMapping("/update/image")
    @ApiOperation(value = "用户更新头像", notes = "用户更新头像接口")
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
}
