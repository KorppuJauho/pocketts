// The development page on the home network (scripts/dev.sh devserver up): a phone opens it to install the latest test
// build, and test builds send their log here (dist/phone.log). Only for testing; nothing else is served.
import { createServer } from "node:http";
import { appendFileSync, createReadStream, readdirSync, statSync } from "node:fs";
import path from "node:path";

const DIST = "/dist";
const LOG = path.join(DIST, "phone.log");
const MAX_LOG_POST = 1024 * 1024;

const escape = (s) => s.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

function apks() {
  return readdirSync(DIST)
    .filter((f) => f.endsWith(".apk"))
    .map((f) => ({ name: f, stat: statSync(path.join(DIST, f)) }))
    .sort((a, b) => b.stat.mtimeMs - a.stat.mtimeMs);
}

createServer((req, res) => {
  const url = new URL(req.url, "http://localhost");

  if (req.method === "POST" && url.pathname === "/log") {
    let body = "";
    req.setEncoding("utf8");
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > MAX_LOG_POST) req.destroy();
    });
    req.on("end", () => {
      appendFileSync(LOG, body.endsWith("\n") ? body : body + "\n");
      res.writeHead(204).end();
    });
    return;
  }

  if (req.method === "GET" && url.pathname.startsWith("/apk/")) {
    const name = path.basename(decodeURIComponent(url.pathname.slice(5)));
    const file = apks().find((a) => a.name === name);
    if (!file) return res.writeHead(404).end("Not found");
    res.writeHead(200, {
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Length": file.stat.size,
      "Content-Disposition": `attachment; filename="${name}"`,
    });
    return createReadStream(path.join(DIST, name)).pipe(res);
  }

  if (req.method === "GET" && url.pathname === "/") {
    const list = apks()
      .map((a) => `<li><a href="/apk/${encodeURIComponent(a.name)}">${escape(a.name)}</a>
        <small>${(a.stat.size / 1048576).toFixed(1)} MB, ${a.stat.mtime.toISOString().slice(0, 16).replace("T", " ")} UTC</small></li>`)
      .join("");
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" });
    return res.end(`<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<title>MobileTS6 test builds</title>
<style>body{font:16px system-ui,sans-serif;margin:24px;line-height:1.5}a{font-size:1.2em}li{margin:12px 0}</style>
<h1>MobileTS6 test builds</h1><p>Tap the newest one to download, then open it to install.</p>
<ul>${list || "<li>No builds yet</li>"}</ul>`);
  }

  res.writeHead(404).end("Not found");
}).listen(8686, () => console.log("Dev page on port 8686"));
