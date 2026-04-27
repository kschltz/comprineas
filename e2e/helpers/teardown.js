module.exports = async function globalTeardown() {
  console.log('[e2e] Shutting down app server...');
  if (global.__e2eServerProc) {
    global.__e2eServerProc.kill('SIGKILL');
    await new Promise(r => setTimeout(r, 1000));
  }
};
