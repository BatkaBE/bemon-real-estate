import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
const root = resolve('domain-mobile/dist');
const types = { '.js': 'text/javascript', '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.ttf': 'font/ttf', '.woff': 'font/woff', '.svg': 'image/svg+xml' };
/** Serves the exported mobile web preview locally, including the registered OAuth callback path. */
http.createServer(async (request, response) => {
  try {
    const path = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const file = resolve(root, `.${path}`);
    if (file !== root && !file.startsWith(`${root}/`)) { response.writeHead(403).end(); return; }
    const target = path === '/' || path === '/oauth' ? resolve(root, 'index.html') : file;
    const body = await readFile(target); response.writeHead(200, { 'Content-Type': types[extname(target)] || 'application/octet-stream' }); response.end(body);
  } catch { response.writeHead(404).end(); }
}).listen(8082, '127.0.0.1', () => console.info('Mobile web preview: http://localhost:8082'));
