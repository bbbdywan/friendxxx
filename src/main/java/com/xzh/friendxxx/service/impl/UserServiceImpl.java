package com.xzh.friendxxx.service.impl;

import com.alibaba.druid.wall.violation.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.xzh.friendxxx.common.utils.AliOssUtil;
import com.xzh.friendxxx.constant.ErrorConstant;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.model.dto.PageDTO;
import com.xzh.friendxxx.model.dto.RecommendRequest;
import com.xzh.friendxxx.model.dto.UserDTO;
import com.xzh.friendxxx.model.entity.Tag;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.model.vo.RecommendUserVO;
import com.xzh.friendxxx.service.UserService;
import com.xzh.friendxxx.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.xzh.friendxxx.exception.ErrorCode.PARAMS_ERROR;

/**
* @author bb
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-07-17 17:48:09
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{
    @Autowired
    AliOssUtil ossUtil;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 任务17：用户信息本地缓存
     * 使用 Guava LoadingCache 缓存用户信息
     * 最多缓存 1000 个用户，5 分钟过期
     */
    private final LoadingCache<Long, User> userCache = CacheBuilder.newBuilder()
            .maximumSize(1000)  // 最多缓存 1000 个用户
            .expireAfterWrite(5, TimeUnit.MINUTES)  // 5 分钟过期
            .recordStats()  // 记录缓存统计信息
            .build(new CacheLoader<Long, User>() {
                @Override
                public User load(Long userId) throws Exception {
                    log.debug("从数据库加载用户信息: userId={}", userId);
                    User user = userMapper.selectById(userId);
                    if (user == null) {
                        throw new BusinessException(100001, ErrorConstant.USER_NOT_FOUND);
                    }
                    return user;
                }
            });

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findUserByTag() {

//        QueryWrapper<User> QueryWrapper = new QueryWrapper<>();
//        List<User> userList = userMapper.selectList(QueryWrapper);

        return userMapper.getuser();
    }

    @Override
    public User login(UserDTO userDTO) {
        if (userDTO == null || StringUtils.isBlank(userDTO.getUserAccount())
                || StringUtils.isBlank(userDTO.getUserpassword())) {
            throw new BusinessException(PARAMS_ERROR, "账号和密码不能为空");
        }
        String userAccount=userDTO.getUserAccount();
        String userPassword=userDTO.getUserpassword();
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        //获取这个用户
        wrapper.eq("userAccount", userAccount);
        User user = userMapper.selectOne(wrapper);
        if(user==null)
            throw new BusinessException(100001,ErrorConstant.USER_NOT_FOUND);
        if(Integer.valueOf(1).equals(user.getIsDelete()))
            throw new BusinessException(100001,ErrorConstant.USER_NOT_FOUND);

        String storedPassword = user.getUserPassword();
        boolean encoded = storedPassword != null && storedPassword.startsWith("$2");
        boolean passwordMatches = encoded
                ? passwordEncoder.matches(userPassword, storedPassword)
                : storedPassword != null && MessageDigest.isEqual(
                        storedPassword.getBytes(StandardCharsets.UTF_8),
                        userPassword.getBytes(StandardCharsets.UTF_8));
        if(!passwordMatches)
            throw new BusinessException(100002,ErrorConstant.LOGIN_ERROR);

        // 兼容已有明文账号：首次成功登录后自动升级为 BCrypt。
        if (!encoded) {
            userMapper.update(null, new UpdateWrapper<User>()
                    .set("userPassword", passwordEncoder.encode(userPassword))
                    .eq("id", user.getId()));
        }

        return user;
    }

    @Override
    public int updateuser(User user) {

        int i = userMapper.updateById(user);
        if(i==0)
            throw new BusinessException(100003,ErrorConstant.UPDATE_ERROR);

        // 任务17：更新用户时清除缓存
        if (user.getId() != null) {
            userCache.invalidate(user.getId());
            log.debug("清除用户缓存: userId={}", user.getId());
        }

        return i>0?1:0;
    }

    /**
     * 任务17：从缓存中获取用户信息
     * 优先从本地缓存获取，缓存未命中时从数据库加载
     */
    public User getUserById(Long userId) {
        try {
            return userCache.get(userId);
        } catch (ExecutionException e) {
            log.error("获取用户信息失败: userId={}", userId, e);
            throw new BusinessException(100001, ErrorConstant.USER_NOT_FOUND);
        }
    }

    /**
     * 任务17：获取缓存统计信息（用于监控）
     */
    public String getCacheStats() {
        return "用户缓存统计: " + userCache.stats().toString();
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(PARAMS_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new BusinessException(PARAMS_ERROR, "图片不能超过5MB");
        }
        String extensionName = org.springframework.util.StringUtils
                .getFilenameExtension(file.getOriginalFilename());
        if (extensionName == null || !java.util.Set.of("jpg", "jpeg", "png", "webp")
                .contains(extensionName.toLowerCase(java.util.Locale.ROOT))) {
            throw new BusinessException(PARAMS_ERROR, "仅支持 jpg、jpeg、png、webp 图片");
        }
        
        try {
            byte[] fileBytes = file.getBytes();
            String extension = "." + extensionName.toLowerCase(java.util.Locale.ROOT);
            String objectName = "avatar/" + System.currentTimeMillis() + extension;
            
            return ossUtil.upload(fileBytes, objectName);
        } catch (IOException e) {
            throw new BusinessException(PARAMS_ERROR, "文件读取失败: " + e.getMessage());
        }
    }

    @Override
    public long getid(String userAccount) {

        return userMapper.getid(userAccount);
    }

    @Override
    public void removebyAccount(String account) {
        userMapper.deleteByAccount(account);
    }

    @Override
    public PageInfo<User> findUserByTag(Integer pageNum, Integer pageSize) {
       // PageHelper.startPage(pageNum, pageSize);

        // 任务20：避免深分页（offset > 10000）
        int offset = (pageNum - 1) * pageSize;
        if (offset > 10000) {
            log.warn("深分页警告: offset={}, 建议使用游标分页", offset);
            throw new BusinessException(PARAMS_ERROR, "分页参数过大，请缩小查询范围");
        }

        List<User> users = userMapper.select(offset,pageSize);

        return new PageInfo<>(users);
    }

    @Override
    public PageInfo<User> selectuser(PageDTO pageDTO) {
        // 任务20：避免深分页（offset > 10000）
        long offset = (pageDTO.getPageNum() - 1L) * pageDTO.getPageSize();
        if (offset > 10000) {
            log.warn("深分页警告: offset={}, 建议使用游标分页或缩小查询范围", offset);
            throw new BusinessException(PARAMS_ERROR, "分页参数过大，请缩小查询范围或使用时间范围筛选");
        }

        List<User> users = userMapper.selectUserByCondition(
                pageDTO.getUsername(),
                pageDTO.getCreateTimeBegin(),
                pageDTO.getCreateTimeEnd(),
                offset,
                pageDTO.getPageSize()
        );

        long total = userMapper.countUserByCondition(
                pageDTO.getUsername(),
                pageDTO.getCreateTimeBegin(),
                pageDTO.getCreateTimeEnd()
        );

        PageInfo<User> pageInfo = new PageInfo<>();
        pageInfo.setList(users);
        pageInfo.setPageNum(pageDTO.getPageNum());
        pageInfo.setPageSize(pageDTO.getPageSize());
        pageInfo.setTotal(total);
        pageInfo.setPages((int) Math.ceil((double) total / pageDTO.getPageSize()));

        return pageInfo;
    }

    @Override
    public List<RecommendUserVO> recommend(RecommendRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new BusinessException(PARAMS_ERROR, "缺少当前用户信息");
        }
        int limit = request.getLimit() == null ? 10 : request.getLimit();
        if (limit < 1 || limit > 50) {
            throw new BusinessException(PARAMS_ERROR, "推荐数量必须在1到50之间");
        }
        request.setLimit(limit);
        if (request.getAgeMin() != null && request.getAgeMax() != null
                && request.getAgeMin() > request.getAgeMax()) {
            throw new BusinessException(PARAMS_ERROR, "年龄范围不合法");
        }
        // 1. 获取当前用户画像
        User currentUser = this.getById(request.getUserId());
        if (currentUser == null) {
            throw new BusinessException(100001, ErrorConstant.USER_NOT_FOUND);
        }

        // 2. SQL粗筛候选用户（上限200条，保证性能）
        int candidateLimit = Math.min(request.getLimit() * 20, 200);
        List<User> candidates = userMapper.selectCandidates(
                request.getUserId(),
                request.getGender(),
                request.getAgeMin(),
                request.getAgeMax(),
                request.getHometown(),
                candidateLimit
        );

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 计算匹配分
        List<RecommendUserVO> results = new java.util.ArrayList<>();
        for (User candidate : candidates) {
            int score = calcMatchScore(currentUser, candidate);
            results.add(RecommendUserVO.builder()
                    .id(candidate.getId())
                    .userName(candidate.getUsername())
                    .avatar(candidate.getAvatarUrl())
                    .tags(candidate.getTags())
                    .age(candidate.getAge())
                    .gender(candidate.getGender())
                    .zodiac(candidate.getZodiac())
                    .height(candidate.getHeight())
                    .profession(candidate.getProfession())
                    .education(candidate.getEducation())
                    .hometown(candidate.getHometown())
                    .signature(candidate.getSignature())
                    .matchScore(score)
                    .build());
        }

        // 4. 按分数降序排列，取Top N
        results.sort((a, b) -> b.getMatchScore().compareTo(a.getMatchScore()));
        int topN = Math.min(request.getLimit(), results.size());
        return results.subList(0, topN);
    }

    /** 计算两个用户之间的匹配分数 */
    private int calcMatchScore(User current, User candidate) {
        int score = 0;

        // 标签相似度 (0~40)
        score += tagSimilarity(current.getTags(), candidate.getTags());

        // 同城 (0~15)
        if (current.getHometown() != null && current.getHometown().equals(candidate.getHometown())) {
            score += 15;
        }

        // 星座匹配 (0~10)
        if (current.getZodiac() != null && current.getZodiac().equals(candidate.getZodiac())) {
            score += 10;
        }

        // 学历匹配 (0~10)
        if (current.getEducation() != null && current.getEducation().equals(candidate.getEducation())) {
            score += 10;
        }

        // 职业相似 (0~10)
        if (current.getProfession() != null && current.getProfession().equals(candidate.getProfession())) {
            score += 10;
        }

        // 年龄匹配 (0~15) - 相差5岁内满分，超出递减
        if (current.getAge() != null && candidate.getAge() != null) {
            int ageDiff = Math.abs(current.getAge() - candidate.getAge());
            if (ageDiff <= 3) {
                score += 15;
            } else if (ageDiff <= 5) {
                score += 10;
            } else if (ageDiff <= 8) {
                score += 5;
            }
        }

        return Math.min(score, 100);
    }

    /** Jaccard标签相似度，返回0~40 */
    private int tagSimilarity(String tagsA, String tagsB) {
        if (tagsA == null || tagsA.isBlank() || tagsB == null || tagsB.isBlank()) {
            return 0;
        }
        String[] arrA = tagsA.split(",");
        String[] arrB = tagsB.split(",");

        java.util.Set<String> setA = new java.util.HashSet<>();
        for (String s : arrA) setA.add(s.trim());

        java.util.Set<String> setB = new java.util.HashSet<>();
        for (String s : arrB) setB.add(s.trim());

        int intersection = 0;
        for (String s : setA) {
            if (setB.contains(s)) intersection++;
        }
        int union = setA.size() + setB.size() - intersection;
        if (union == 0) return 0;

        double jaccard = (double) intersection / union;
        return (int) Math.round(jaccard * 40);
    }


}
