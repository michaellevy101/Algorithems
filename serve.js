// Simple static file server for the guides documentation site
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const GUIDES_DIR = path.join(__dirname, 'guides');

const MIME_TYPES = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.json': 'application/json',
    '.md': 'text/plain; charset=utf-8',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.pdf': 'application/pdf',
};

const server = http.createServer((req, res) => {
    // Remove query string and decode URI
    let urlPath = decodeURIComponent(req.url.split('?')[0]);

    // Default to index.html
    if (urlPath === '/') urlPath = '/index.html';

    const filePath = path.join(GUIDES_DIR, urlPath);

    // Prevent directory traversal
    if (!filePath.startsWith(GUIDES_DIR)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    const ext = path.extname(filePath).toLowerCase();

    fs.readFile(filePath, (err, data) => {
        if (err) {
            if (err.code === 'ENOENT') {
                res.writeHead(404);
                res.end('Not found: ' + urlPath);
            } else {
                res.writeHead(500);
                res.end('Server error');
            }
            return;
        }

        const contentType = MIME_TYPES[ext] || 'application/octet-stream';
        res.writeHead(200, {
            'Content-Type': contentType,
            'Cache-Control': 'no-cache',
        });
        res.end(data);
    });
});

server.listen(PORT, () => {
    console.log(`\n  📚 Guides server running at:\n`);
    console.log(`     http://localhost:${PORT}\n`);
    console.log(`  Press Ctrl+C to stop.\n`);
});
