# Nginx 部署说明

统一的生产拓扑、发布顺序、备份、回滚和巡检要求见
[`docs/manuals/DEPLOYMENT.md`](../docs/manuals/DEPLOYMENT.md)。本文保留仓库内
Nginx、Jar 重启和 Prefect 的具体操作入口。

CentOS 上的数据抓取和基金计算任务由自托管 Prefect Server + Process
Worker 统一管理。安装、验证以及从旧 `crontab`/`systemd timer` 切换的步骤见
[`centos/README.md`](centos/README.md)。
Prefect 专用 PostgreSQL 配置见
[`prefect/README.md`](prefect/README.md)。

## 构建前端

```bash
cd frontend
npm install
npm run build
```

前端默认使用同源 `/api` 作为接口地址，通过 Vite 或 Nginx 反向代理转发到网关。如需在构建产物中固定其他网关地址，构建前设置：

```bash
VITE_API_BASE=http://你的网关地址/api npm run build
```

将 `frontend/dist` 上传到服务器：

```text
/usr/share/nginx/html/crm
```

## 启动后端

```bash
cd backend
mvn -DskipTests package
java -jar system/target/system-0.1.0.jar
java -jar customer/target/customer-0.1.0.jar
java -jar gateway/target/gateway-0.1.0.jar
java -jar fund/target/fund-0.1.0.jar

# 可选：原单体迁移后的 admin 服务
java -jar admin/target/admin-0.1.0.jar
```

网关默认监听：

```text
http://127.0.0.1:8780/api
```

## 平滑重启微服务

Nacos 配置已启用 Spring Boot graceful shutdown，并暴露 `serviceregistry` Actuator 端点。重启脚本会先把实例标记为 `DOWN`，等待摘流传播，再发送 `SIGTERM`，由 Spring Boot 等待存量请求完成后退出。

先打包：

```bash
cd backend
mvn -DskipTests package
cd ..
```

重启单个实例：

```bash
deploy/graceful-restart.sh system backend/system/target/system-0.1.0.jar 8782
deploy/graceful-restart.sh customer backend/customer/target/customer-0.1.0.jar 8783
deploy/graceful-restart.sh gateway backend/gateway/target/gateway-0.1.0.jar 8780
deploy/graceful-restart.sh fund backend/fund/target/fund-0.1.0.jar 8784

# 可选：admin 服务
ACTUATOR_BASE=http://127.0.0.1:8781/api/actuator deploy/graceful-restart.sh admin backend/admin/target/admin-0.1.0.jar 8781
```

多个实例滚动重启时，对每个实例分别执行脚本，并确保同一个服务至少保留一个健康实例承接流量。可通过环境变量调整摘流和停机等待时间：

```bash
DRAIN_SECONDS=15 STOP_TIMEOUT=60 deploy/graceful-restart.sh system backend/system/target/system-0.1.0.jar 8782
```

## 安装 Nginx 配置

将 [nginx.conf](nginx.conf) 放到 Nginx 站点配置目录，例如：

```bash
sudo cp deploy/nginx.conf /etc/nginx/conf.d/crm.conf
sudo nginx -t
sudo nginx -s reload
```

## 本机 Homebrew Nginx

本机 Nginx 使用的是 Homebrew 配置路径：

```text
/opt/homebrew/etc/nginx/nginx.conf
```

已写入本机站点配置：

```text
/opt/homebrew/etc/nginx/servers/crm.conf
```

访问地址：

```text
http://127.0.0.1:8090/
```

默认示例站点端口已从 `8080` 调整为 `8089`，避免和网关服务 `8780` 冲突。原配置已备份到：

```text
/opt/homebrew/etc/nginx/nginx.conf.bak.crm
```

重新加载配置：

```bash
nginx -t
nginx -s reload
```

如果后端部署在另一台机器，把配置里的这一行：

```nginx
proxy_pass http://127.0.0.1:8780;
```

改成实际后端地址，例如：

```nginx
proxy_pass http://10.0.0.12:8780;
```

如果使用 Docker Compose，并且网关服务名是 `gateway`，则改成：

```nginx
proxy_pass http://gateway:8780;
```
