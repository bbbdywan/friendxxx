# 用户自定义提示词 API 接口文档

> Base URL: `/api/prompt`
> 所有接口需要登录（Bearer Token 认证）

---

## 1. 保存/更新提示词

**POST** `/api/prompt/save`

### 请求头
| 参数 | 值 |
|------|-----|
| Content-Type | application/json |
| Authorization | Bearer {token} |

### 请求体
```json
{
  "title": "温柔女友",
  "content": "人设定位：\n你是用户的贴心年轻女生朋友，性格积极、活泼、温柔又有力量..."
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 否 | 传了则为更新，不传则为新增 |
| title | String | 否 | 提示词标题，方便管理 |
| content | String | 是 | 提示词内容（System Prompt） |

### 响应
```json
{
  "code": 200,
  "message": "保存成功",
  "data": "保存成功"
}
```

---

## 2. 获取提示词列表

**GET** `/api/prompt/list`

### 请求头
| 参数 | 值 |
|------|-----|
| Authorization | Bearer {token} |

### 响应
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "userId": 10,
      "title": "温柔女友",
      "content": "人设定位：\n你是用户的贴心年轻女生朋友...",
      "isActive": 1,
      "createTime": "2026-03-18T10:00:00",
      "updateTime": "2026-03-18T10:00:00"
    },
    {
      "id": 2,
      "userId": 10,
      "title": "编程助手",
      "content": "你是一个资深编程专家...",
      "isActive": 0,
      "createTime": "2026-03-18T11:00:00",
      "updateTime": "2026-03-18T11:00:00"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 提示词ID |
| userId | Long | 用户ID |
| title | String | 提示词标题 |
| content | String | 提示词内容 |
| isActive | Integer | 1=当前使用，0=未使用 |
| createTime | DateTime | 创建时间 |
| updateTime | DateTime | 更新时间 |

---

## 3. 获取当前激活的提示词

**GET** `/api/prompt/active`

### 请求头
| 参数 | 值 |
|------|-----|
| Authorization | Bearer {token} |

### 响应
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "userId": 10,
    "title": "温柔女友",
    "content": "人设定位：\n你是用户的贴心年轻女生朋友...",
    "isActive": 1,
    "createTime": "2026-03-18T10:00:00",
    "updateTime": "2026-03-18T10:00:00"
  }
}
```

> 如果用户没有设置自定义提示词，`data` 为 `null`，聊天时会使用系统默认提示词。

---

## 4. 设置提示词为当前使用

**POST** `/api/prompt/setActive/{id}`

### 路径参数
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 要激活的提示词ID |

### 请求头
| 参数 | 值 |
|------|-----|
| Authorization | Bearer {token} |

### 响应
```json
{
  "code": 200,
  "message": "设置成功",
  "data": "设置成功"
}
```

> 同一用户只能有一个激活的提示词，切换时会自动将其他提示词设为未使用。

---

## 5. 删除提示词

**DELETE** `/api/prompt/delete/{id}`

### 路径参数
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 要删除的提示词ID |

### 请求头
| 参数 | 值 |
|------|-----|
| Authorization | Bearer {token} |

### 响应
```json
{
  "code": 200,
  "message": "删除成功",
  "data": "删除成功"
}
```

> 软删除，不会真正从数据库移除。

---

## 前端对接流程

```
1. 用户进入「提示词设置」页面
   └─ 调用 GET /api/prompt/list 展示所有提示词

2. 用户新建/编辑提示词
   └─ 调用 POST /api/prompt/save（textarea 填写 content）

3. 用户选择某个提示词为当前使用
   └─ 调用 POST /api/prompt/setActive/{id}

4. 用户删除某个提示词
   └─ 调用 DELETE /api/prompt/delete/{id}

5. 用户正常聊天
   └─ 调用 /helloworld/simple/chat 或 /helloworld/stream/chat
   └─ 后端自动读取用户激活的提示词，无需前端额外传参
```

---

## 错误响应格式

```json
{
  "code": 500,
  "message": "保存失败",
  "data": null
}
```
