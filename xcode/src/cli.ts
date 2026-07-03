#!/usr/bin/env node
import { stat, writeFile } from "node:fs/promises";
import { watch } from "node:fs";
import path from "node:path";
import process from "node:process";
import { markdownToHtml, createHtmlDocument } from "./markdown.js";
import { readFile } from "node:fs/promises";

type CliOptions = {
  input?: string;
  output?: string;
  watch: boolean;
  help: boolean;
};

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));

  if (options.help || !options.input) {
    printHelp();
    process.exit(options.help ? 0 : 1);
  }

  const input = path.resolve(options.input);
  const output = path.resolve(options.output ?? defaultOutputPath(input));

  await ensureFile(input);
  await convert(input, output);

  if (options.watch) {
    watchInput(input, output);
  }
}

function parseArgs(args: string[]): CliOptions {
  const options: CliOptions = {
    watch: false,
    help: false
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];

    if (arg === "-h" || arg === "--help") {
      options.help = true;
      continue;
    }

    if (arg === "--watch") {
      options.watch = true;
      continue;
    }

    if (arg === "-o" || arg === "--output") {
      const value = args[index + 1];
      if (!value || value.startsWith("-")) {
        throw new Error(`Missing value for ${arg}`);
      }
      options.output = value;
      index += 1;
      continue;
    }

    if (arg.startsWith("-")) {
      throw new Error(`Unknown option: ${arg}`);
    }

    if (options.input) {
      throw new Error(`Unexpected argument: ${arg}`);
    }

    options.input = arg;
  }

  return options;
}

async function convert(input: string, output: string): Promise<void> {
  const markdown = await readFile(input, "utf8");
  const body = markdownToHtml(markdown);
  const title = path.basename(input);
  const html = createHtmlDocument(body, title);

  await writeFile(output, html, "utf8");
  console.log(`Converted ${path.relative(process.cwd(), input)} -> ${path.relative(process.cwd(), output)}`);
}

function defaultOutputPath(input: string): string {
  const parsed = path.parse(input);
  return path.join(parsed.dir, `${parsed.name}.html`);
}

async function ensureFile(filePath: string): Promise<void> {
  const stats = await stat(filePath);
  if (!stats.isFile()) {
    throw new Error(`Input is not a file: ${filePath}`);
  }
}

function watchInput(input: string, output: string): void {
  console.log(`Watching ${path.relative(process.cwd(), input)} for changes...`);

  let timer: NodeJS.Timeout | undefined;

  const watcher = watch(input, () => {
    if (timer) {
      clearTimeout(timer);
    }

    timer = setTimeout(() => {
      convert(input, output).catch((error: unknown) => {
        console.error(formatError(error));
      });
    }, 100);
  });

  const stop = (): void => {
    watcher.close();
    process.exit(0);
  };

  process.on("SIGINT", stop);
  process.on("SIGTERM", stop);
}

function printHelp(): void {
  console.log(`Usage:
  md2html input.md -o output.html
  md2html input.md --watch

Options:
  -o, --output <file>  Output HTML file. Defaults to the input name with .html.
      --watch          Rebuild when the input file changes.
  -h, --help           Show this help message.`);
}

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

main().catch((error: unknown) => {
  console.error(formatError(error));
  process.exit(1);
});
