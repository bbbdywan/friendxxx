package com.xzh.friendxxx.controller;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.github.pagehelper.PageInfo;
import com.xzh.friendxxx.common.context.BaseContext;
import com.xzh.friendxxx.common.utils.Result;
import com.xzh.friendxxx.constant.ErrorConstant;
import com.xzh.friendxxx.model.dto.PageDTO;
import com.xzh.friendxxx.model.dto.RecommendRequest;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.GetuUserVO;
import com.xzh.friendxxx.model.vo.RecommendUserVO;
import com.xzh.friendxxx.model.vo.UserVO;
import com.xzh.friendxxx.service.SocialPostService;
import com.xzh.friendxxx.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson2.JSON;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理模块", description = "提供用户相关的接口")
@Slf4j
public class UserController {

    private static final String USER_LIST_CACHE_VERSION_KEY = "user:list:version";

    @Autowired
    UserService userService;

    @Autowired
    RedisTemplate<String, String> redisTemplate;
    @Autowired
    private SocialPostService socialPostService;

    @PostMapping ("/getalluser")
    @Operation(summary = "查询所有用户",description = "条件查询")
    public Result<PageInfo<User>> selectAlluser(@RequestBody PageDTO pageDTO)
    {
        PageInfo<User> pageInfo = userService.selectuser(pageDTO);
        return Result.success(pageInfo);

    }

    @DeleteMapping("deleteuser")
    @Operation(summary = "停用该用户",description = "根据ID停用用户,禁止登录")
    public Result<Integer> deleteuser(@RequestParam long deletedid)
    {
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId.equals(deletedid)) {
            return Result.error("不能停用当前账号");
        }
        if (!userService.removeById(deletedid)) {
            return Result.error("用户不存在或已停用");
        }
        bumpUserListCacheVersion();
        return Result.success(1);
    }

    @GetMapping("/tagsList")
    @Operation(summary = "获取用户标签列表", description = "根据用户标签查询用户信息")
    public Result<PageInfo<User>> tagsList(@RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "10") Integer pageSize){
        pageNum = pageNum == null ? 1 : Math.max(pageNum, 1);
        pageSize = pageSize == null ? 10 : Math.min(Math.max(pageSize, 1), 50);
        String version = redisTemplate.opsForValue().get(USER_LIST_CACHE_VERSION_KEY);
        String RedisKey="user:list:" + (version == null ? "0" : version) + ":" + pageNum + ":" + pageSize;
        String json = redisTemplate.opsForValue().get(RedisKey); // 正常返回 JSON 字符串

        if (StringUtils.isNotBlank(json)) {
            PageInfo<User> users = JSON.parseObject(json, new TypeReference<PageInfo<User>>() {});
            return Result.success(users);
        }
        PageInfo<User> tagsList = userService.findUserByTag(pageNum,pageSize);
       // redisTemplate.opsForValue().set(RedisKey, JSON.toJSONString(tagsList), 30, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(RedisKey, JSON.toJSONString(tagsList), 30, TimeUnit.MINUTES);

        return Result.success(tagsList);
    }

    @PostMapping("/update")
    @Operation(summary = "用户更新", description = "用户更新接口")
    public Result<Integer> update(@RequestBody User user){
        //从threadlocal中获取当前用户id
        long userid= BaseContext.getCurrentId();
        if(user.getId() == null || userid != user.getId())
            return Result.error(ErrorConstant.USER_NOT_AUTH);
        // 只允许更新个人资料字段，禁止通过请求体修改角色、密码和账号状态。
        User profile = new User();
        profile.setId(userid);
        profile.setUsername(user.getUsername());
        profile.setAvatarUrl(user.getAvatarUrl());
        profile.setTags(user.getTags());
        profile.setBackground(user.getBackground());
        profile.setSignature(user.getSignature());
        profile.setAge(user.getAge());
        profile.setGender(user.getGender());
        profile.setHeight(user.getHeight());
        profile.setProfession(user.getProfession());
        profile.setEducation(user.getEducation());
        profile.setZodiac(user.getZodiac());
        profile.setHometown(user.getHometown());
        profile.setRelationshipStatus(user.getRelationshipStatus());
        profile.setPhone(user.getPhone());
        profile.setEmail(user.getEmail());
        int i = userService.updateuser(profile);
        if (i > 0) {
            bumpUserListCacheVersion();
        }
        return Result.success(i);
    }

    @GetMapping("/profile")
    @Operation(summary = "获取用户信息", description = "获取用户信息接口")
    public Result<UserVO> profile()
    {
        long userid = BaseContext.getCurrentId();
        User user = userService.getById(userid);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
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

    @PostMapping("/update/image")
    @Operation(summary = "用户更新头像", description = "用户更新头像接口")
    public Result<String> updateImage(@RequestParam("file") MultipartFile file,@RequestParam("type") String type) {
        //从threadlocal中获取当前用户id
        long userid = BaseContext.getCurrentId();
        if (!"avatar".equals(type) && !"background".equals(type)) {
            return Result.error("无效的图片类型参数");
        }
        
        //上传头像并获取URL
        String avatarUrl = userService.uploadAvatar(file);

        //更新用户头像URL到数据库
        User user = new User();
        user.setId(userid);
        if ("avatar".equals(type)) {
            user.setAvatarUrl(avatarUrl);
            socialPostService.updateAvatarUrl(avatarUrl);
        } else if ("background".equals(type)) {
            user.setBackground(avatarUrl);
        }
        userService.updateById(user);
        
        return Result.success(avatarUrl);
    }
    @PostMapping("/recommend")
    @Operation(summary = "用户推荐", description = "基于用户画像的多维度推荐")
    public Result<List<RecommendUserVO>> recommend(@RequestBody RecommendRequest request) {
        if (request == null) {
            request = new RecommendRequest();
        }
        request.setUserId(BaseContext.getCurrentId());
        List<RecommendUserVO> list = userService.recommend(request);
        return Result.success(list);
    }

    @GetMapping("/{userID}")
    @Operation(summary = "查看用户信息", description = "查看用户信息接口")
    public Result<GetuUserVO> getuser(@PathVariable("userID") Long userID)
    {
        User getone = userService.getById(userID);
        if (getone == null) {
            return Result.error(404, "用户不存在");
        }
        GetuUserVO build=GetuUserVO.builder().id(getone.getId()).userName(getone.getUsername()).
                userAccount(getone.getUserAccount()).avatar(getone.getAvatarUrl()).tags(getone.getTags()).
                background(getone.getBackground()).signature(getone.getSignature()).age(getone.getAge()).
                gender(getone.getGender()).zodiac(getone.getZodiac()).height(getone.getHeight()).
                profession(getone.getProfession()).education(getone.getEducation()).hometown(getone.getHometown())
                .relationshipStatus(getone.getRelationshipStatus()).build();
        return Result.success(build);

    }

    private void bumpUserListCacheVersion() {
        try {
            redisTemplate.opsForValue().increment(USER_LIST_CACHE_VERSION_KEY);
        } catch (RuntimeException e) {
            log.warn("用户列表缓存版本更新失败", e);
        }
    }
}
