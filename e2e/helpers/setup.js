const { spawn } = require('child_process');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '..', '..');

function startApp() {
  return new Promise((resolve, reject) => {
    const proc = spawn('clojure', ['-M:test', '-m', 'comprineas.e2e-server'], {
      cwd: PROJECT_ROOT,
      stdio: ['pipe', 'pipe', 'pipe'],
      env: { ...process.env, PATH: process.env.PATH },
    });

    let resolved = false;
    const timeout = setTimeout(() => {
      if (!resolved) {
        resolved = true;
        reject(new Error('App failed to start within 30s'));
      }
    }, 30000);

    proc.stdout.on('data', (data) => {
      const line = data.toString().trim();
      if (line.startsWith('E2E_READY')) {
        clearTimeout(timeout);
        resolved = true;
        console.log('[e2e] App server ready:', line);
        resolve(proc);
      }
    });

    proc.stderr.on('data', (data) => {
      // Log server stderr for debugging
      const line = data.toString().trim();
      if (line) console.log('[server]', line);
    });

    proc.on('error', (err) => {
      clearTimeout(timeout);
      if (!resolved) { resolved = true; reject(err); }
    });

    proc.on('exit', (code) => {
      clearTimeout(timeout);
      if (!resolved) {
        resolved = true;
        reject(new Error(`App exited with code ${code} before ready`));
      }
    });
  });
}

function waitForServer(url, timeoutMs = 10000) {
  const start = Date.now();
  const http = require('http');
  return new Promise((resolve, reject) => {
    function poll() {
      http.get(url, (res) => { res.resume(); resolve(); })
        .on('error', () => {
          if (Date.now() - start > timeoutMs) reject(new Error('Server not ready'));
          else setTimeout(poll, 200);
        });
    }
    poll();
  });
}

module.exports = async function globalSetup() {
  console.log('[e2e] Starting app server...');
  const proc = await startApp();
  // The app signals E2E_READY but Jetty may not be listening yet — poll for it
  await waitForServer('http://localhost:3001/login');
  console.log('[e2e] Server accepting connections');
  process.env.__E2E_SERVER_PID = String(proc.pid);
  global.__e2eServerProc = proc;
};
