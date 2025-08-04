package com.xzh.friendxxx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzh.friendxxx.common.utils.AliOssUtil;
import com.xzh.friendxxx.exception.BusinessException;
import com.xzh.friendxxx.model.entity.SocialPost;
import com.xzh.friendxxx.service.SocialPostService;
import com.xzh.friendxxx.mapper.SocialPostMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static com.xzh.friendxxx.exception.ErrorCode.PARAMS_ERROR;

/**
* @author bb
* @description 针对表【social_post(用户动态表)】的数据库操作Service实现
* @createDate 2025-07-25 22:31:35
*/
@Service
public class SocialPostServiceImpl extends ServiceImpl<SocialPostMapper, SocialPost>
    implements SocialPostService{

    @Autowired
    private AliOssUtil ossUtil;

    @Autowired
    private SocialPostMapper socialPostMapper;

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
    public int remove(String deleteTtl) {
        int count = socialPostMapper.removebyttl(deleteTtl);
        return count;
    }

    @Override
    public List<SocialPost> getlist(Long userId) {
        List<SocialPost> list= socialPostMapper.getlist(userId);
        return list;
    }

    @Override
    public List<SocialPost> list() {
        // 使用自定义查询方法，确保类型处理器正确工作
        return socialPostMapper.selectAllWithTypeHandler();
    }
}




