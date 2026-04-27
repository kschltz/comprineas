module.exports = async function globalTeardown() {
  console.log('[e2e] Shutting down app server...');
  if (global.__e2eServerProc) {
    global.__e2eServerProc.stdin.end();
    global.__e2eServerProc.kill('SIGTERM');
    // Give it a moment to clean up
    await new Promise(r => setTimeout(r, 2000));
  }
};
