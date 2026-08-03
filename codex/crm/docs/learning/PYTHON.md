# Python 基础与进阶：结合数据任务

目标：从能读 Python 代码，进阶到能安全修改 `fund_spider/` 的采集、数据库、评分和 Prefect Flow。

## 1. 从一条命令认识项目

```bash
cd fund_spider
python cli.py feature --fund-code 519674
```

链路：

```text
cli.main -> build_parser -> apply_env_overrides -> run_command
-> jobs.crawl_feature_data -> FeatureSpider
-> db.upsert_feature_data -> fund_feature_data
```

学习时先用 `rg` 搜函数定义和调用，再从入口逐层阅读，避免从 1500 行的 `db.py` 随机开始。

## 2. Python 环境

### 解释器与虚拟环境

Python 项目必须隔离依赖：

```bash
cd fund_spider
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
which python
```

`python -m pip` 确保 pip 属于当前解释器。不要把 `.venv` 提交仓库或在生产手工混装系统 Python 包。

### 模块与导入

- 文件是模块，目录可成为 package。
- `if __name__ == "__main__"` 只在直接执行时运行。
- 当前 CLI 从 `fund_spider` 目录运行，使用本地模块导入。
- 导入时不要做网络请求或数据库写入，保持测试可控。

## 3. 基础语法

### 类型和值

常见：`str/int/float/bool/None/list/tuple/dict/set`。Python 动态类型不代表可以忽略类型；项目使用注解表达契约。

```python
def parse_number(value: object) -> float | None:
    ...
```

`None` 与 0、空字符串不同。数据库 NULL、源端 `--` 和真实 0 必须分别处理。

### 可变与不可变

- list/dict/set 可变；str/tuple 不可变。
- 默认参数不要写可变对象：`def f(rows=[])` 会跨调用共享。
- 把输入 list 传给函数前明确是否允许原地修改。

### 控制流与推导式

掌握 `if/elif/else`、`for/while`、`break/continue`、列表/字典推导。推导式只用于简单转换；带多层条件和副作用时用普通循环更易维护。

## 4. 函数、作用域与数据类

函数应有单一职责：解析、选择批次、写库分开。参数/返回注解和清晰名称比大段注释更可靠。

项目用 `@dataclass` 表示数据库/解析行。优势：字段明确、自动构造/比较、测试方便。

```python
@dataclass(frozen=True)
class ExampleRow:
    code: str
    value: Decimal | None
```

需要不可变时用 `frozen=True`，防止解析后被意外修改。

## 5. 异常与资源

### 异常

- 只捕获能处理的异常。
- 重新抛出保留上下文：`raise ... from exc`。
- 不用 `except Exception: pass` 隐藏数据缺失。
- CLI 最终以非零退出让 Prefect 知道失败。

### 上下文管理器

文件、连接、Cursor、锁需要可靠释放：

```python
with open(path, encoding="utf-8") as handle:
    text = handle.read()
```

数据库连接/事务按 PyMySQL 语义显式 commit/rollback/close。子进程锁无论成功失败都释放。

## 6. 类型注解

必须能读：

- `str | None`：字符串或 None。
- `list[FundNavHistory]`：元素固定的列表。
- `dict[str, Any]`：字符串 key，但 value 尚未结构化。
- `Iterable[T]`：可遍历，不承诺能重复遍历/取长度。
- `Sequence[T]`：有顺序，可索引。

减少 `Any`：外部 JSON 边界可以是 `Any`，解析后尽快转成数据类。类型注解不会在运行时自动校验，仍需输入验证和测试。

## 7. HTTP 请求

Requests 基本点：URL、query/header、timeout、status、response text/json、异常。

项目请求必须：

- 同时设置连接/读取超时（按现有封装）。
- 有界重试和随机间隔。
- 识别可重试状态与永久错误。
- 使用合法 User-Agent/授权。
- 日志脱敏 Header/Cookie。
- 避免无限并发和无节制全量抓取。

### 解析与采集分离

```text
fetch(): 网络副作用
parse(text): 纯函数
job(): 批次/事务/统计
```

纯解析函数可以用固定 fixture 测试，数据源不可用时仍能回归。

## 8. 文本、JSON、日期和 Decimal

- JSON 字段可能缺失、为 null 或类型变化；显式校验。
- HTML/JSONP 先定位稳定结构，再解析；不要只靠脆弱下标。
- 日期进入数据库前统一 `YYYYMMDD` 或 SQL DATE，见表定义。
- 金额/净值使用 `Decimal`/字符串到 Decimal，避免 float 误差。
- 百分号、中文单位“亿”和空占位符要集中转换并测试。

练习：阅读 `db._parse_scale_yi` 与一个 Spider 的日期清洗，列出输入/输出/异常样例。

## 9. 数据库基础

必须掌握：

- 参数化 SQL，不能 f-string 拼用户值。
- 主键、唯一键、索引和自然键。
- 事务、commit/rollback、批量写入。
- Upsert/幂等。
- `SELECT ... LIMIT/OFFSET` 与大分页代价。

当前 `db.py` 用标识符白名单函数处理动态表/列名；普通值一律绑定参数。

### 幂等

相同任务运行两次，业务结果应与运行一次一致。常见设计：

```text
业务自然键 -> UNIQUE KEY -> INSERT ... ON DUPLICATE KEY UPDATE
```

幂等不等于“永不更新”：源数据修正时 Upsert 应更新允许变化的字段。

## 10. 事务与批量

事务要小而完整：

1. 网络请求在事务外。
2. 解析/校验全部完成。
3. 打开事务，批量写相关表。
4. 全成功 commit；异常 rollback。
5. 成功后更新刷新状态/游标。

批次过大导致锁/日志/内存压力，过小导致频繁往返。通过配置和基准选择，不拍脑袋。

## 11. 并发

历史净值可以并发抓页面。必须理解：

- I/O bound 与 CPU bound。
- ThreadPool 的 worker 数、Future、异常传播。
- 结果顺序和确定性。
- Requests/连接对象是否线程安全。
- 数据库写入集中/分批，不让多线程随意共享连接。
- 并发放大第三方限流与本机资源。

排错先设 `NAV_PAGE_WORKERS=1`，确认逻辑正确，再逐步并发。

多进程调度时 Python 线程锁不够，项目用文件锁防止多个 Flow 同时执行；分布式多机仍需更强协调策略。

## 12. 单元测试

项目使用 `unittest`：

```bash
cd fund_spider
source .venv/bin/activate
python -m unittest discover -s tests -p 'test_*.py'
```

典型 Fake：

- FakeResponse：固定 HTTP text/json/status。
- FakeSpider：记录参数并返回固定行。
- FakeConnection：记录 execute/commit/rollback。
- patch 环境变量/时间/随机数，使测试确定。

一个解析器至少测试：正常、空、缺字段、非法日期/数字、重复、源结构变化提示。

## 13. CLI 设计

`argparse` 应提供：命令 help、默认值、choices、别名和明确错误。参数映射为环境变量使 CLI/Prefect/Shell 共享配置，但要防止旧环境值污染测试。

新增命令：

1. 在 `build_parser` 注册。
2. 在 `run_command` 分派。
3. 成功输出摘要，部分失败最终抛异常。
4. 增加 CLI 参数/失败测试。
5. 更新 API/Python 手册。

## 14. Prefect

### 概念

| 概念         | 本项目                          |
| ---------- | ---------------------------- |
| Task       | `run_business_job`，带 retries |
| Flow       | 一组串行业务任务                     |
| Deployment | Flow + 计划 + 参数 + Work Pool   |
| Worker     | 从 Pool 获取 run，启动本地进程         |
| Server     | API/UI/状态编排                  |
| PostgreSQL | Prefect 元数据，不是业务数据           |

### 失败语义

子进程非零退出必须让 Task/Flow 失败，不能只写 ERROR 日志后返回成功。重试仅适合临时故障；逻辑/认证/结构错误应停止并报警。

### 调度

Cron 使用 `Asia/Shanghai`。交易时间 Cron 不等同交易日历，Spider/Job 仍需处理非交易日。当前自动 Deployment 在 `prefect.yaml` 中是启用状态；维护时可临时暂停，长期变更必须回写 YAML。

### dry-run

`dry_run=true` 验证选择、环境和进程链路，不写业务表。它不能证明外部源/数据库真实写入成功，之后还需小批次集成验证。

## 15. 评分进阶

### 数据泄漏

预测快照日只能使用当日已知信息。把未来收益、事后评级或最新规模用于历史训练，会得到虚假好成绩。

### 因子与百分位

同类基金内比较，方向（越大越好/越小越好）、缺失值和小样本回退必须明确。权重总和校验属于算法边界，不只靠 UI。

### 回测

- 按时间滚动切分，不随机打乱。
- train/test 间保留 12 个月 embargo。
- 同时看 AUC、Brier 和 top-20% 提升，不只看单指标。
- 样本未成熟不写盈利标签。

### 概率校准

分数用于排序，概率用于解释未来事件，二者不可互换。未验证 profile 的概率为 null 是正确安全行为。

## 16. 可观测性与数据质量

任务成功至少记录：开始/结束、选择数量、抓取数量、写入数量、跳过/失败数量、耗时、游标和最新数据日期。

数据质量检查：

- 唯一键是否重复。
- 日期是否倒退/跳跃。
- 行数是否突然归零或爆增。
- null 比例和数值范围是否异常。
- 源端总数与落库数是否匹配。
- 当前数据是否超过新鲜度阈值。

Prefect success 只代表代码未抛异常，不自动证明数据正确。

## 17. 安全

- 不抓取无授权数据，不绕过访问控制。
- `.env`、Cookie、Authorization、数据库密码不入库。
- 日志/测试 fixture 脱敏。
- SQL 使用参数绑定和标识符白名单。
- OCR/用户持仓按用户隔离。
- Prefect UI 不裸露公网。
- 依赖升级查看安全公告并跑完整回归。

## 18. 项目练习

### 练习 A：解析器

给一个脱敏固定响应新增一个可空字段解析，补正常/缺失/非法值测试，不访问公网。

验收：单测确定通过，旧 fixture 不受影响。

### 练习 B：幂等

阅读一个 Upsert，说明自然键、哪些字段更新、重复运行结果。使用 Fake Connection 写测试，不连生产库。

验收：能解释为什么唯一键与业务键一致。

### 练习 C：CLI dry-run

追踪 Prefect `feature-refresh-manual` 如何选择 `feature`，运行 dry-run 并在日志中证明没有写库。

验收：能区分 Deployment 名、Flow 名和 CLI 命令。

### 练习 D：数据质量

在本地库写只读 SQL，检查净值最大日期、唯一键重复和近期写入量。

验收：不给“有数据”这种模糊结论，而给日期、数量和阈值。

## 19. 独立维护完成标准

- [ ] 能从 CLI/Flow 追到 Spider、Job、DB 和表。
- [ ] 能写纯解析函数和确定性单元测试。
- [ ] 能设计超时、重试、限速、幂等、事务和游标。
- [ ] 能解释线程并发和多进程锁的边界。
- [ ] 能部署/检查 Prefect Server、Worker、Pool 和 Deployment。
- [ ] 能识别评分时间泄漏、未成熟标签和概率校准风险。
- [ ] 能用日志和数据质量 SQL证明任务真的成功。
- [ ] 能给出数据库备份、任务暂停和版本回滚步骤。
