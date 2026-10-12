// Copies the DAP page into the Android app, with Capacitor's bridge script added in front of the page's own script.
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '..'), www = path.join(root, 'www');
fs.mkdirSync(www, { recursive: true });
let html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const at = html.indexOf('<script>');
if (at < 0) throw new Error('no <script> in index.html');
html = html.slice(0, at) + '<script src="capacitor.js"></script>\n' + html.slice(at);
fs.writeFileSync(path.join(www, 'index.html'), html);
fs.copyFileSync(path.join(root, 'node_modules/@capacitor/core/dist/capacitor.js'), path.join(www, 'capacitor.js'));
console.log('www ready');
