# Bruno .bru 文件格式与 Collection 结构规范

> 调研日期: 2026-08-05
> 来源: Bruno 官方文档 (docs.usebruno.com)、GitHub 仓库示例 (usebruno/bruno)

## 1. 目录结构

```
bruno-collection/
├── bruno.json              # Collection 元数据
├── environments/
│   └── dev.bru             # 环境变量定义
├── auth/
│   ├── login.bru           # HTTP 请求
│   └── register.bru        # HTTP 请求
├── supplier/
│   └── ...
└── flows/
    └── ...
```

## 2. bruno.json — Collection 元数据

```json
{
  "version": "1",
  "name": "Collection Name",
  "type": "collection",
  "ignore": ["node_modules", ".git"]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `version` | string | 固定 `"1"` |
| `name` | string | Collection 名称，显示在 Bruno 侧边栏 |
| `type` | string | 固定 `"collection"` |
| `ignore` | string[] | 要忽略的文件/目录模式 |

## 3. .bru 请求文件格式

### 3.1 元数据块 (meta)

```bru
meta {
  name: 用户登录
  type: http
  seq: 1
}
```

| 字段 | 说明 |
|------|------|
| `name` | 请求名称 |
| `type` | 固定 `http` |
| `seq` | 在所属文件夹内的排序序号 |

### 3.2 HTTP 方法块

支持的 HTTP 方法: `get`, `post`, `put`, `patch`, `delete`, `head`, `options`

```bru
get {
  url: {{baseUrl}}/api/v1/suppliers
  body: none
  auth: inherit
}
```

```bru
post {
  url: {{baseUrl}}/api/v1/auth/login
  body: json
  auth: none
}
```

| 字段 | 说明 |
|------|------|
| `url` | 请求 URL，支持 `{{var}}` 变量插值 |
| `body` | `none` / `json` / `multipartForm` / `formUrlEncoded` |
| `auth` | `none` / `inherit` / `basic` / `bearer` / `awsv4` |

### 3.3 请求头

```bru
headers {
  Content-Type: application/json
  Authorization: Bearer {{token}}
}
```

### 3.4 JSON 请求体

```bru
body:json {
  {
    "username": "admin",
    "password": "pass123"
  }
}
```

JSON 体直接写在花括号中，支持 `{{var}}` 变量插值。

### 3.5 Query 参数

```bru
params:query {
  page: 1
  size: 20
}
```

### 3.6 路径变量

在 URL 中使用 `{{var}}` 配合 path 变量：

```bru
get {
  url: {{baseUrl}}/api/v1/suppliers/{{supplierId}}
  body: none
  auth: inherit
}
```

## 4. 环境变量文件

文件名格式: `environments/<name>.bru`

```bru
vars {
  baseUrl: http://localhost:8080
  token:
}
```

- 变量名后跟冒号和值
- 空值变量（如 `token:`）在运行时动态填充
- 支持 `vars:secret` 块存放敏感变量（加密存储）

## 5. 脚本 API

### 5.1 Pre-request 脚本

在请求发送前执行，可用于动态设置变量或做前置认证：

```bru
script:pre-request {
  if (!bru.getEnvVar("token")) {
    const res = await bru.request({
      method: "POST",
      url: bru.getEnvVar("baseUrl") + "/api/v1/auth/login",
      body: JSON.stringify({
        username: "admin",
        password: "pass123"
      }),
      headers: {
        "Content-Type": "application/json"
      }
    });
    bru.setEnvVar("token", res.data.token);
  }
}
```

### 5.2 Post-response 脚本（断言/测试）

在收到响应后执行，用于写断言：

```bru
script:post-response {
  bru.test("Status code is 200", () => {
    expect(res.status).to.equal(200);
  });

  bru.test("Response contains token", () => {
    expect(res.data.token).to.not.be.undefined;
  });
}
```

### 5.3 脚本 API 参考

| API | 说明 |
|-----|------|
| `bru.getEnvVar(name)` | 获取环境变量 |
| `bru.setEnvVar(name, value)` | 设置环境变量（运行时生效） |
| `bru.getVar(name)` | 获取集合变量 |
| `bru.setVar(name, value)` | 设置集合变量 |
| `bru.test(name, fn)` | 注册一个测试用例 |
| `bru.request(opts)` | 在脚本中发起 HTTP 请求 |
| `expect(value)` | 断言入口（基于 chai） |
| `res.status` | 响应状态码 |
| `res.statusText` | 响应状态文本 |
| `res.headers` | 响应头对象 |
| `res.data` | 解析后的响应体（JSON 自动解析） |
| `res.body` | 原始响应体字符串 |
| `res.responseTime` | 响应时间（毫秒） |

## 6. Token 自动化模板

以下为每个需要认证的请求使用的标准 pre-request 模板：

```bru
script:pre-request {
  if (!bru.getEnvVar("token")) {
    const res = await bru.request({
      method: "POST",
      url: bru.getEnvVar("baseUrl") + "/api/v1/auth/login",
      body: JSON.stringify({
        username: "admin",
        password: "pass123"
      }),
      headers: {
        "Content-Type": "application/json"
      }
    });
    bru.setEnvVar("token", res.data.token);
  }
}
```

在 `headers` 块中添加：

```bru
headers {
  Authorization: Bearer {{token}}
  Content-Type: application/json
}
```

## 7. 断言模板

### 正向用例

```bru
script:post-response {
  bru.test("Status is 200", () => {
    expect(res.status).to.equal(200);
  });

  bru.test("Response has code=200", () => {
    expect(res.data.code).to.equal(200);
  });

  bru.test("Data contains id", () => {
    expect(res.data.data.id).to.not.be.undefined;
  });
}
```

### 错误用例

```bru
script:post-response {
  bru.test("Status is 400", () => {
    expect(res.status).to.equal(400);
  });

  bru.test("Response has error code", () => {
    expect(res.data.code).to.not.equal(200);
  });
}
```

## 8. 变量插值

- 环境变量: `{{varName}}`
- 在 URL、header、body、query params 中均支持
- pre-request 脚本中通过 `bru.getEnvVar()` / `bru.setEnvVar()` 读写

## 9. 注意事项

1. `.bru` 文件使用 Bruno 自定义的 DSL 语法，不是标准 JSON/YAML
2. JSON body 直接写在 `body:json { ... }` 中，不需要转义
3. `auth: none` 表示该请求不需要认证（如 login/register）
4. `auth: inherit` 表示继承父级或 collection 级别的认证配置
5. 环境变量为空值时只写变量名和冒号（如 `token:`），运行时动态填充
6. 所有 `{{var}}` 在运行时进行字符串替换
7. 脚本中的 `bru.request()` 是异步的，必须使用 `await`
