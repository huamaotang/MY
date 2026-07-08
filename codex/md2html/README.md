# md2html-cli

一个用 TypeScript 编写的 Markdown 转 HTML 命令行工具。

## 功能

- 支持标题、段落、无序列表、有序列表、代码块、链接、图片等基本 Markdown 语法。
- 生成完整 HTML 文档，并内置一套简洁的默认样式。
- 支持通过 `-o` / `--output` 指定输出文件。
- 未指定输出文件时，默认使用输入文件名并替换为 `.html` 后缀。
- 支持 `--watch` 监听输入文件变化并自动重新转换。

## 主要文件

- `src/cli.ts`：命令行入口，负责参数解析、默认输出路径、文件读写和 `--watch` 监听。
- `src/markdown.ts`：Markdown 到 HTML 的转换逻辑，以及 HTML 文档模板和默认样式。
- `package.json`：项目元信息、CLI `bin` 配置、构建脚本和开发依赖。
- `tsconfig.json`：TypeScript 编译配置。
- `.gitignore`：忽略 `node_modules`、`dist`、日志等生成文件。

## 安装依赖

```bash
npm install
```

## 构建

```bash
npm run build
```

## 使用

构建后可以直接用 Node 运行编译产物：

```bash
node dist/cli.js input.md -o output.html
```

也可以通过 `npm link` 注册本地命令：

```bash
npm link
md2html input.md -o output.html
```

命令格式：

```bash
md2html input.md -o output.html
```

如果不指定 `-o` / `--output`，会生成同名 `.html` 文件：

```bash
md2html input.md
```

监听文件变化并自动重新转换：

```bash
md2html input.md -o output.html --watch
```

开发时也可以直接运行 TypeScript：

```bash
npm run dev -- input.md -o output.html
```

查看帮助：

```bash
md2html --help
```

## 支持的语法示例

````markdown
# 标题

一段带有 [链接](https://example.com) 的文字。

- 无序列表项
- ![图片](image.png)

1. 有序列表项
2. 另一个有序列表项

```js
console.log("ok");
```
````

## 验证

当前项目已验证以下命令和功能：

```bash
npm install
npm run build
node dist/cli.js /private/tmp/md2html-test.md -o /private/tmp/md2html-test.html
node dist/cli.js /private/tmp/md2html-test.md
node dist/cli.js /private/tmp/md2html-test.md --watch
```

验证结果：

- TypeScript 构建通过。
- 可以把 Markdown 文件转换成带默认样式的 HTML 文件。
- 标题、段落、列表、代码块、链接、图片均已在样例中转换成功。
- 未指定 `-o` 时，可以自动生成同名 `.html` 文件。
- `--watch` 可以在输入文件变化后自动重新转换。
