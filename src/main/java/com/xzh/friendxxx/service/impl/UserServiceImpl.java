package com.xzh.friendxxx.service.impl;

import com.alibaba.druid.wall.violation.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.common.utils.AliOssUtil;
import com.xzh.friendxxx.constant.ErrorConstant;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.model.dto.UserDTO;
import com.xzh.friendxxx.model.entity.Tag;
import com.xzh.friendxxx.model.entity.User;
import com.xzh.friendxxx.service.UserService;
import com.xzh.friendxxx.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static com.xzh.friendxxx.exception.ErrorCode.PARAMS_ERROR;

/**
* @author bb
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-07-17 17:48:09
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{
    @Autowired
    AliOssUtil ossUtil;
    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public List<User> findUserByTag() {

        QueryWrapper<User> QueryWrapper = new QueryWrapper<>();
        List<User> userList = userMapper.selectList(QueryWrapper);
        return userList;
    }

    @Override
    public User login(UserDTO userDTO) {
        String userAccount=userDTO.getUserAccount();
        String userPassword=userDTO.getUserpassword();
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        //获取这个用户
        wrapper.eq("userAccount", userAccount);
        User user = this.getOne(wrapper);
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

        return i>0?1:0;
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
}
