export function markdownToHtml(markdown: string): string {
  const normalized = markdown.replace(/\r\n?/g, "\n");
  const lines = normalized.split("\n");
  const html: string[] = [];
  let paragraph: string[] = [];
  let list: { type: "ul" | "ol"; items: string[] } | null = null;
  let codeBlock: { lang: string; lines: string[] } | null = null;

  const flushParagraph = (): void => {
    if (paragraph.length === 0) {
      return;
    }

    html.push(`<p>${renderInline(paragraph.join(" "))}</p>`);
    paragraph = [];
  };

  const flushList = (): void => {
    if (!list) {
      return;
    }

    html.push(`<${list.type}>`);
    for (const item of list.items) {
      html.push(`<li>${renderInline(item)}</li>`);
    }
    html.push(`</${list.type}>`);
    list = null;
  };

  const closeBlocks = (): void => {
    flushParagraph();
    flushList();
  };

  for (const line of lines) {
    const fenceMatch = line.match(/^```([\w-]*)\s*$/);
    if (fenceMatch) {
      if (codeBlock) {
        const langClass = codeBlock.lang ? ` class="language-${escapeAttribute(codeBlock.lang)}"` : "";
        html.push(`<pre><code${langClass}>${escapeHtml(codeBlock.lines.join("\n"))}</code></pre>`);
        codeBlock = null;
      } else {
        closeBlocks();
        codeBlock = { lang: fenceMatch[1] ?? "", lines: [] };
      }
      continue;
    }

    if (codeBlock) {
      codeBlock.lines.push(line);
      continue;
    }

    if (line.trim() === "") {
      closeBlocks();
      continue;
    }

    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      closeBlocks();
      const level = headingMatch[1].length;
      html.push(`<h${level}>${renderInline(headingMatch[2].trim())}</h${level}>`);
      continue;
    }

    const unorderedMatch = line.match(/^\s*[-*+]\s+(.+)$/);
    if (unorderedMatch) {
      flushParagraph();
      if (!list || list.type !== "ul") {
        flushList();
        list = { type: "ul", items: [] };
      }
      list.items.push(unorderedMatch[1].trim());
      continue;
    }

    const orderedMatch = line.match(/^\s*\d+\.\s+(.+)$/);
    if (orderedMatch) {
      flushParagraph();
      if (!list || list.type !== "ol") {
        flushList();
        list = { type: "ol", items: [] };
      }
      list.items.push(orderedMatch[1].trim());
      continue;
    }

    flushList();
    paragraph.push(line.trim());
  }

  if (codeBlock) {
    const langClass = codeBlock.lang ? ` class="language-${escapeAttribute(codeBlock.lang)}"` : "";
    html.push(`<pre><code${langClass}>${escapeHtml(codeBlock.lines.join("\n"))}</code></pre>`);
  }

  closeBlocks();

  return html.join("\n");
}

export function createHtmlDocument(body: string, title = "Markdown Document"): string {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)}</title>
  <style>
    :root {
      color-scheme: light;
      --text: #202124;
      --muted: #5f6368;
      --border: #dadce0;
      --surface: #ffffff;
      --code-bg: #f6f8fa;
      --link: #0b57d0;
    }

    body {
      margin: 0;
      background: #f8fafd;
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      line-height: 1.65;
    }

    main {
      box-sizing: border-box;
      width: min(860px, calc(100% - 32px));
      margin: 40px auto;
      padding: 40px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 8px;
    }

    h1, h2, h3, h4, h5, h6 {
      margin: 1.5em 0 0.55em;
      line-height: 1.25;
    }

    h1 {
      padding-bottom: 0.35em;
      border-bottom: 1px solid var(--border);
      font-size: 2rem;
    }

    p, ul, ol, pre {
      margin: 1em 0;
    }

    a {
      color: var(--link);
      text-decoration-thickness: 0.08em;
      text-underline-offset: 0.15em;
    }

    img {
      display: block;
      max-width: 100%;
      height: auto;
      margin: 1.25em 0;
      border-radius: 6px;
    }

    code {
      padding: 0.15em 0.35em;
      background: var(--code-bg);
      border-radius: 4px;
      font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
      font-size: 0.92em;
    }

    pre {
      overflow-x: auto;
      padding: 16px;
      background: var(--code-bg);
      border: 1px solid var(--border);
      border-radius: 6px;
    }

    pre code {
      padding: 0;
      background: transparent;
      border-radius: 0;
    }

    blockquote {
      margin: 1em 0;
      padding-left: 1em;
      color: var(--muted);
      border-left: 4px solid var(--border);
    }

    @media (max-width: 640px) {
      main {
        width: 100%;
        margin: 0;
        padding: 24px;
        border-width: 0;
        border-radius: 0;
      }
    }
  </style>
</head>
<body>
  <main>
${indent(body, 4)}
  </main>
</body>
</html>
`;
}

function renderInline(text: string): string {
  const escaped = escapeHtml(text);

  return escaped
    .replace(/!\[([^\]]*)\]\(([^)\s]+)(?:\s+"([^"]*)")?\)/g, (_match, alt: string, src: string, title: string | undefined) => {
      const titleAttribute = title ? ` title="${escapeAttribute(title)}"` : "";
      return `<img src="${escapeAttribute(src)}" alt="${escapeAttribute(alt)}"${titleAttribute}>`;
    })
    .replace(/\[([^\]]+)\]\(([^)\s]+)(?:\s+"([^"]*)")?\)/g, (_match, label: string, href: string, title: string | undefined) => {
      const titleAttribute = title ? ` title="${escapeAttribute(title)}"` : "";
      return `<a href="${escapeAttribute(href)}"${titleAttribute}>${label}</a>`;
    })
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>");
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function escapeAttribute(value: string): string {
  return escapeHtml(value).replace(/`/g, "&#96;");
}

function indent(value: string, spaces: number): string {
  const prefix = " ".repeat(spaces);
  return value
    .split("\n")
    .map((line) => `${prefix}${line}`)
    .join("\n");
}
