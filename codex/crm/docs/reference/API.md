# CRM API 与运维接口参考

本文记录当前代码真实存在的接口。REST 事实来源是 Java Controller 和 `deploy/nacos/gateway-dev.yaml`；客户端覆盖来自 Web `src/api.ts`、iOS `ApiClient.swift` 和 Android `ApiClient.java`。

## 1. 认证、权限与通用约定

### Base URL

```text
本地：http://127.0.0.1:8780/api
生产：https://<crm-domain>/api
```

客户端调用外部路径时带 `/api`；Java Controller 的 `@RequestMapping` 不带 `/api`，Gateway 会移除第一段路径。

### 请求头

| Header | 值 | 说明 |
| --- | --- | --- |
| `Content-Type` | `application/json` | JSON 请求；OCR 上传改用 `multipart/form-data` |
| `Accept` | `application/json` | 推荐显式发送 |
| `Authorization` | `Bearer <JWT>` | 除登录和公开 Actuator 外均需 |
| `X-Client-Source` | `web/ios/android/curl` | 用于 `sys_api_log.source` |

### 通用响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

注意：

- 成功时 `code=0`。
- `ApiResponse.fail` 使用 `code=500`；业务异常可能仍是 HTTP 200，校验失败/权限失败/未处理异常分别可能返回 HTTP 400/403/500。
- 客户端必须同时检查 HTTP 状态和响应中的 `code`，不能只看 HTTP 200。
- 分页 `data` 通常含 `records`、`total`、`size`、`current`、`pages`。

### 登录示例

```bash
curl -sS -X POST 'http://127.0.0.1:8780/api/auth/login' \
  -H 'Content-Type: application/json' \
  -H 'X-Client-Source: curl' \
  -d '{"username":"admin","password":"admin123"}'
```

本地初始化账号只用于开发。不要把密码写入脚本、终端历史或生产文档。

```bash
export CRM_TEST_TOKEN='<login-response-token>'
curl -sS 'http://127.0.0.1:8780/api/auth/me' \
  -H "Authorization: Bearer ${CRM_TEST_TOKEN}" \
  -H 'X-Client-Source: curl'
```

## 2. System 服务

外部前缀经 Gateway 转到 `system:8782`。

### 认证

| 方法 | 外部路径 | 权限 | 输入 | 输出 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/login` | 公开 | JSON：`username`、`password` | `LoginResponse`：Token、用户信息/权限 |
| GET | `/api/auth/me` | 已登录 | 无 | 当前 `Principal` |

### 用户

| 方法 | 路径 | 权限 | 输入/说明 |
| --- | --- | --- | --- |
| GET | `/api/users?keyword=` | `ROLE_ADMIN` | 用户列表，可按关键词搜索 |
| POST | `/api/users` | `ROLE_ADMIN` | `UserSaveRequest`，新增用户 |
| PUT | `/api/users/{id}` | `ROLE_ADMIN` | `UserSaveRequest`，更新用户 |
| DELETE | `/api/users/{id}` | `ROLE_ADMIN` | 删除用户 |

`UserSaveRequest` 的权威字段以 `backend/system/.../dto/UserSaveRequest.java` 为准；Web 对应类型在 `frontend/src/api.ts` 的 `User`。

### 角色

| 方法 | 路径 | 权限 | 输入/说明 |
| --- | --- | --- | --- |
| GET | `/api/roles` | `ROLE_ADMIN` | 角色列表，返回菜单 ID 集合 |
| POST | `/api/roles` | `ROLE_ADMIN` | `RoleSaveRequest`，新增角色及菜单关联 |
| PUT | `/api/roles/{id}` | `ROLE_ADMIN` | 更新角色及菜单关联 |
| DELETE | `/api/roles/{id}` | `ROLE_ADMIN` | 删除角色 |

### 菜单

| 方法 | 路径 | 权限 | 输入/说明 |
| --- | --- | --- | --- |
| GET | `/api/menus/mine` | 已登录 | 当前用户可见菜单/按钮权限 |
| GET | `/api/menus` | `ROLE_ADMIN` | 全量菜单 |
| POST | `/api/menus` | `ROLE_ADMIN` | `SysMenu` |
| PUT | `/api/menus/{id}` | `ROLE_ADMIN` | `SysMenu` |
| DELETE | `/api/menus/{id}` | `ROLE_ADMIN` | 删除菜单 |

角色或菜单修改后，旧 JWT 不会自动获得新权限；必须重新登录。

## 3. Customer 服务

### 客户

| 方法 | 路径 | 权限 | 输入 | 输出 |
| --- | --- | --- | --- | --- |
| GET | `/api/customers` | `crm:customer:list` | `current=1`、`size=10`、可选 `keyword` | `Page<CrmCustomer>` |
| GET | `/api/customers/{id}` | `crm:customer:list` | 客户 ID | `CrmCustomer` |
| POST | `/api/customers` | `crm:customer:create` | `CrmCustomer` JSON | 空 `data` |
| PUT | `/api/customers/{id}` | `crm:customer:update` | `CrmCustomer` JSON | 空 `data` |
| DELETE | `/api/customers/{id}` | `crm:customer:delete` | 客户 ID | 空 `data` |

保存示例：

```bash
curl -sS -X POST 'http://127.0.0.1:8780/api/customers' \
  -H "Authorization: Bearer ${CRM_TEST_TOKEN}" \
  -H 'Content-Type: application/json' \
  -H 'X-Client-Source: curl' \
  -d '{"customerName":"示例客户","status":"POTENTIAL"}'
```

### 联系人与跟进

| 方法 | 路径 | 权限 | 输入/说明 |
| --- | --- | --- | --- |
| GET | `/api/contacts?customerId=` | `crm:contact:list` | 可按客户过滤，返回列表 |
| POST | `/api/contacts` | `crm:customer:update` | `CrmContact` JSON |
| GET | `/api/follow-records?customerId=` | `crm:follow:list` | 可按客户过滤，返回列表 |
| POST | `/api/follow-records` | `crm:customer:update` | `CrmFollowRecord` JSON |

当前没有联系人/跟进记录的更新和删除接口，不要在客户端文档中承诺这些能力。

## 4. Fund 服务：基金

### 列表、自选与 CRUD

| 方法 | 路径 | 权限 | 输入/说明 |
| --- | --- | --- | --- |
| GET | `/api/funds` | `fund:list` | `current/size/keyword/fundType/canBuy/sortField/sortOrder` |
| GET | `/api/funds/favorites` | `fund:list` | 与基金列表相同，只返回当前用户自选 |
| POST | `/api/funds/{fundCode}/favorite` | `fund:list` | 加入自选 |
| DELETE | `/api/funds/{fundCode}/favorite` | `fund:list` | 移出自选 |
| GET | `/api/funds/{fundCode}` | `fund:list` | 基金详情及关联数据摘要 |
| POST | `/api/funds` | `fund:create` | `CfgFund` JSON |
| PUT | `/api/funds/{fundCode}` | `fund:update` | `CfgFund` JSON |
| DELETE | `/api/funds/{fundCode}` | `fund:delete` | 删除基金 |

常用排序字段由 `FundServiceImpl` 白名单控制。客户端传入 Ant Design 排序值时使用 `ascend/descend`；任何新增排序项都必须先确认后端白名单。

### 基金关联数据

| 方法 | 路径 | 权限 | 输入 | 输出 |
| --- | --- | --- | --- | --- |
| GET | `/api/funds/{fundCode}/navs` | `fund:list` | `current=1&size=10` | 净值分页 |
| GET | `/api/funds/{fundCode}/holdings` | `fund:list` | `current/size/reportDate?` | 基金股票持仓分页 |
| GET | `/api/funds/{fundCode}/valuations` | `fund:list` | `current=1&size=20` | 每日估值分页 |
| GET | `/api/funds/{fundCode}/features` | `fund:list` | 无 | 标准差、夏普比率列表 |
| GET | `/api/funds/{fundCode}/ratings` | `fund:list` | 无 | 机构评级列表 |

## 5. Fund 服务：评分配置

所有接口需要 `fund:score-config`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/funds/scoring/profiles` | 权重配置列表 |
| POST | `/api/funds/scoring/profiles` | 创建配置，body 含 `profileName` 和 `weights` |
| PUT | `/api/funds/scoring/profiles/{id}` | 更新配置 |
| POST | `/api/funds/scoring/profiles/{id}/backtest` | 入队回测任务 |
| POST | `/api/funds/scoring/profiles/{id}/activate` | 激活已满足门槛的配置 |
| POST | `/api/funds/scoring/recommend` | 入队权重推荐任务 |
| GET | `/api/funds/scoring/profiles/{id}/backtest` | 最近一次回测结果 |
| GET | `/api/funds/scoring/jobs` | 评分任务列表 |

入队只创建 `fund_score_job`，需要 Python `score --mode jobs` 或 `score --mode pipeline` 执行。不能把“接口返回成功”等同于回测已经完成。

## 6. Fund 服务：用户持仓 OCR

这些接口要求已登录，但 Controller 当前没有更细的 `@PreAuthorize` 权限码。

| 方法 | 路径 | Content-Type | 输入/输出 |
| --- | --- | --- | --- |
| POST | `/api/portfolio/imports/ocr` | multipart | `sourceLabel` 默认 `alipay`；`importType` 默认 `holding`；一个或多个 `images`；返回预览 |
| POST | `/api/portfolio/imports/{importId}/confirm` | JSON | 可选确认/映射请求；返回确认统计 |
| GET | `/api/portfolio/holdings` | - | `current/size/keyword/scope/sortField/sortOrder`，返回用户持仓分页 |
| GET | `/api/portfolio/overview` | - | 当前用户账户汇总 |
| GET | `/api/portfolio/imports` | - | 导入批次分页 |
| GET | `/api/portfolio/imports/{importId}` | - | 导入预览/详情 |

OCR 预览：

```bash
curl -sS -X POST 'http://127.0.0.1:8780/api/portfolio/imports/ocr?sourceLabel=alipay&importType=holding' \
  -H "Authorization: Bearer ${CRM_TEST_TOKEN}" \
  -H 'X-Client-Source: curl' \
  -F 'images=@/path/to/screenshot.png'
```

规则：持仓快照覆盖同平台持仓；交易明细只调整同平台已有基金。预览必须由用户确认，OCR 结果不能直接当作可信账务数据。

## 7. Fund 服务：资讯与股票

### 资讯

| 方法 | 路径 | 权限 | 输入/输出 |
| --- | --- | --- | --- |
| GET | `/api/news` | `fund:list` | `current=1&size=20&keyword=&categoryTag=`；资讯分页 |
| DELETE | `/api/news/{id}` | `fund:delete` | 删除一条资讯 |

### 股票

| 方法 | 路径 | 权限 | 输入/输出 |
| --- | --- | --- | --- |
| GET | `/api/stocks` | `fund:list` | `current/size/keyword/marketCode/sortField/sortOrder`；最多每页 200 |
| GET | `/api/stocks/{stockCode}` | `fund:list` | 股票及最近行情 |
| GET | `/api/stocks/{stockCode}/history` | `fund:list` | `current/size/startDate/endDate`；日期格式 `YYYY-MM-DD` |

股票排序字段白名单：`stockCode`、`stockName`、`latestPrice`、`changeRate`、`changeAmount`、`volume`、`amount`、`amplitude`、`turnoverRate`、`volumeRatio`、`peDynamic`、`pbRatio`、`totalMarketCap`、`floatMarketCap`、`changeRate60d`、`changeRateYtd`。

## 8. Admin 兼容与访问日志接口

`admin:8781` 复制了原单体的认证、用户、角色、菜单、客户、联系人和跟进接口，但默认客户端链路使用 `system/customer/fund`。兼容接口不经过当前 Gateway 业务路由。

Gateway 使用内部地址向 Admin 写访问日志：

| 方法 | 内部路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| POST | `http://127.0.0.1:8781/api/api-logs` | `X-Access-Log-Token` | 写入 `sys_api_log` |

生产必须替换 `CRM_ACCESS_LOG_TOKEN`，并保证 Gateway 与 Admin 两侧一致。该接口不可暴露到公网。

## 9. 客户端接口覆盖

| 能力 | Web | iOS | Android |
| --- | :---: | :---: | :---: |
| 登录 | ✓ | ✓ | ✓ |
| 客户列表/详情 | ✓ | ✓ | ✓ |
| 客户新增/更新/删除 | ✓ | - | - |
| 用户/角色/菜单管理 | ✓ | - | - |
| 基金列表/详情/净值 | ✓ | ✓ | ✓ |
| 基金自选 | ✓ | - | - |
| 基金管理/评分配置 | ✓ | 只读评分展示 | 只读评分展示 |
| 资讯列表 | ✓ | ✓ | ✓ |
| 资讯删除 | ✓ | - | - |
| 股票列表/历史 | ✓ | ✓ | ✓ |
| 持仓 OCR/确认/列表 | ✓ | ✓ | ✓ |

“未覆盖”表示当前客户端没有对应调用，不表示后端一定没有接口。

## 10. Python CLI 接口

统一入口：

```bash
cd fund_spider
python cli.py <command> [options]
```

| 命令 | 作用 | 主要写表 | 自动调度 |
| --- | --- | --- | --- |
| `basic` | 基金列表与基础资料 | `fund_detail` | 否 |
| `nav-performance` | 当前净值与阶段收益 | `fund_nav_history`、`fund_performance_history` | 08:00、21:00 |
| `nav-history` | 历史净值 | `fund_nav_history` | 否 |
| `feature` | 标准差、夏普比率 | `fund_feature_data` | 08:00 串行执行 |
| `rating` | 当前/历史评级 | `fund_rating` | 否 |
| `holdings` | 基金股票持仓 | `fund_stock_holding` | 否 |
| `news` | 养基宝资讯 | `yangjibao_news` | 当前 Prefect 未注册 |
| `sina-news` | 新浪财经资讯 | `sina_finance_news` | 每 120 秒 |
| `stock` | A/H 股行情 | `stock_detail`、`stock_daily_history` | 交易窗口每 5 分钟 |
| `score` | 快照、评分、回测、任务队列 | `fund_score_*` | 22:30 pipeline |

完整参数、环境变量优先级和安全限制见 [Python 手册](../manuals/PYTHON.md)。

## 11. Prefect 运维接口

| Deployment | Flow | 默认计划 | 默认状态 |
| --- | --- | --- | --- |
| `morning-fund-refresh` | `fund-morning-refresh` | 每日 08:00 | 已启用 |
| `evening-nav-performance` | `fund-nav-performance` | 每日 21:00 | 已启用 |
| `feature-refresh-manual` | `fund-feature-refresh` | 无 | 可手动 |
| `score-pipeline` | `fund-score-pipeline` | 每日 22:30 | 已启用 |
| `sina-news` | `sina-news-refresh` | 每 120 秒 | 已启用 |
| `stock-cn` | `stock-cn-refresh` | A 股交易窗口 | 已启用 |
| `stock-hk` | `stock-hk-refresh` | 港股交易窗口 | 已启用 |

所有时间使用 `Asia/Shanghai`。维护期间可以在 UI 临时暂停；需要长期暂停时必须同步修改 `prefect.yaml`。

```bash
cd fund_spider
PREFECT_API_URL=http://127.0.0.1:4200/api .venv/bin/prefect deployment ls
PREFECT_API_URL=http://127.0.0.1:4200/api \
  .venv/bin/prefect deployment run \
  'fund-feature-refresh/feature-refresh-manual' \
  --param dry_run=true --watch
```

## 12. 接口变更检查表

- [ ] Controller 路径、Gateway 路由和外部 `/api` 路径一致。
- [ ] 权限码已进入 `sys_menu` 增量 SQL，并用普通角色验证 403/成功两条路径。
- [ ] DTO/Entity 字段与 Web TypeScript、iOS Codable、Android JSON 解析一致。
- [ ] 分页、空值、枚举、日期和 Decimal 格式有实际样例。
- [ ] 新增 Python 命令同步 `.env.example`、Shell wrapper、Flow/Deployment（如需）和本文。
- [ ] curl 示例从 Gateway 验证，不只直连下游服务。
- [ ] 未记录真实 Token、Cookie、签名或生产地址。
