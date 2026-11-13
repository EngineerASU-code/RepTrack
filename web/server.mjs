import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';

const page = new URL('./index.html', import.meta.url);
createServer(async (_request, response) => {
  try {
    response.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
    response.end(await readFile(page));
  } catch {
    response.writeHead(500);
    response.end('RepTrack could not load the app.');
  }
}).listen(4173, '127.0.0.1', () => console.log('RepTrack: http://127.0.0.1:4173'));
