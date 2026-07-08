# Nginx 部署说明

## 构建前端

```bash
cd frontend
npm install
npm run build
```

将 `frontend/dist` 上传到服务器：

```text
/usr/share/nginx/html/crm
```

## 启动后端

```bash
cd backend
mvn -DskipTests package
java -jar target/crm-backend-0.1.0.jar
```

后端默认监听：

```text
http://127.0.0.1:8080/api
```

## 安装 Nginx 配置

将 [nginx.conf](/Users/thm/MY/codex/crm/deploy/nginx.conf) 放到 Nginx 站点配置目录，例如：

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

默认示例站点端口已从 `8080` 调整为 `8089`，避免和后端服务 `8080` 冲突。原配置已备份到：

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
proxy_pass http://127.0.0.1:8080;
```

改成实际后端地址，例如：

```nginx
proxy_pass http://10.0.0.12:8080;
```

如果使用 Docker Compose，并且后端服务名是 `backend`，则改成：

```nginx
proxy_pass http://backend:8080;
```
