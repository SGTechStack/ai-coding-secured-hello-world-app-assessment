// Answers GET /range/{prefix} like the Pwned Passwords API, reporting no password as breached.
import { createServer } from 'node:http';

const port = Number(process.argv[2]);

createServer((request, response) => {
  if (request.method === 'GET' && request.url?.startsWith('/range/')) {
    response.writeHead(200, { 'Content-Type': 'text/plain' });
    response.end('0000000000000000000000000000000000A:1\r\n');
  } else {
    response.writeHead(404);
    response.end();
  }
}).listen(port, '127.0.0.1');
