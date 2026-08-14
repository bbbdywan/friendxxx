package com.xzh.friendxxx.Handler;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzh.friendxxx.model.entity.Mood;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;

import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
@MappedTypes(Mood.class)
@MappedJdbcTypes(JdbcType.OTHER)
@Slf4j
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
            log.warn("解析心情JSON失败，按空列表处理", e);
            return new ArrayList<>();
        }
    }
}
