package com.xzh.friendxxx.service.impl;

import com.alibaba.druid.wall.violation.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.xzh.friendxxx.model.dto.UserDTO;
import com.xzh.friendxxx.model.entity.Tag;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.service.UserService;
import com.xzh.friendxxx.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public List<User> findUserByTag() {

//        QueryWrapper<User> QueryWrapper = new QueryWrapper<>();
//        List<User> userList = userMapper.selectList(QueryWrapper);

        return userMapper.getuser();
    }

    @Override
    public User login(UserDTO userDTO) {
        String userAccount=userDTO.getUserAccount();
        String userPassword=userDTO.getUserpassword();
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        //获取这个用户
        wrapper.eq("userAccount", userAccount);
        User user = this.getOne(wrapper);
        if(user.getIsDelete()==1)
            throw new BusinessException(100001,ErrorConstant.USER_NOT_FOUND);
        if(user==null)
            throw new BusinessException(100001,ErrorConstant.USER_NOT_FOUND);
        if(!user.getUserPassword().equals(userPassword))
            throw new BusinessException(100002,ErrorConstant.LOGIN_ERROR);

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
        
        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
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


}
