package com.xzh.friendxxx.Handler;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzh.friendxxx.model.entity.Mood;
import org.apache.ibatis.type.JdbcType;

import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
@MappedTypes(Mood.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class MoodTypeHandler implements TypeHandler<List<Mood>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setParameter(PreparedStatement ps, int i, List<Mood> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter != null) {
            try {
                ps.setString(i, objectMapper.writeValueAsString(parameter));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            ps.setNull(i, Types.VARCHAR);
        }
    }

    @Override
    public List<Mood> getResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public List<Mood> getResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public List<Mood> getResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private List<Mood> parseJson(String json) {
        if (json == null || json.isEmpty() || "null".equals(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Mood>>() {});
        } catch (Exception e) {
            System.err.println("解析 JSON 到 List<Mood> 对象失败，原始数据：" + json);
            e.printStackTrace();
            return new ArrayList<>(); // 返回空列表而不是抛出异常
        }
    }
}