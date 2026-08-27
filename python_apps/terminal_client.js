/**
 * Terminal WebSocket Client
 * 连接 Python 后端实现真正的终端命令执行
 */

export class TerminalClient {
  constructor(wsUrl = 'ws://localhost:8765') {
    this.wsUrl = wsUrl;
    this.ws = null;
    this.connected = false;
    this.requestId = 0;
    this.pendingRequests = new Map();
    this.onOutputCallback = null;
    this.onErrorCallback = null;
    this.onConnectedCallback = null;
  }

  connect() {
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.wsUrl);
        
        this.ws.onopen = () => {
          this.connected = true;
          console.log('[Terminal] Connected to Python backend');
          if (this.onConnectedCallback) this.onConnectedCallback();
          resolve();
        };

        this.ws.onerror = (error) => {
          console.error('[Terminal] WebSocket error:', error);
          this.connected = false;
          if (this.onErrorCallback) this.onErrorCallback(error);
          reject(error);
        };

        this.ws.onclose = () => {
          this.connected = false;
          console.log('[Terminal] Disconnected from Python backend');
        };

        this.ws.onmessage = (event) => {
          this.handleMessage(JSON.parse(event.data));
        };
      } catch (error) {
        reject(error);
      }
    });
  }

  handleMessage(data) {
    if (data.type === 'response') {
      const { app_id, action, result } = data;
      const requestId = `${app_id}:${action}`;
      
      if (this.pendingRequests.has(requestId)) {
        const { resolve } = this.pendingRequests.get(requestId);
        this.pendingRequests.delete(requestId);
        resolve(result);
      } else if (this.onOutputCallback && result) {
        // 处理异步输出（如终端命令结果）
        this.onOutputCallback(result);
      }
    } else if (data.type === 'reload') {
      // HMR 重载通知
      console.log('[HMR] Reloading application...', data);
      if (window.location) {
        window.location.reload();
      }
    }
  }

  sendRequest(appId, action, params = {}) {
    return new Promise((resolve, reject) => {
      if (!this.connected) {
        reject(new Error('Not connected'));
        return;
      }

      const requestId = `${appId}:${action}`;
      this.pendingRequests.set(requestId, { resolve, reject });

      this.ws.send(JSON.stringify({
        app_id: appId,
        action: action,
        params: params
      }));

      // 超时处理
      setTimeout(() => {
        if (this.pendingRequests.has(requestId)) {
          this.pendingRequests.delete(requestId);
          reject(new Error('Request timeout'));
        }
      }, 30000);
    });
  }

  async executeCommand(command, args = []) {
    return this.sendRequest('terminal', 'execute', { command, args });
  }

  async listDirectory(path = '/') {
    return this.sendRequest('terminal', 'list_dir', { path });
  }

  async readFile(path) {
    return this.sendRequest('terminal', 'read_file', { path });
  }

  async changeDirectory(path) {
    return this.sendRequest('terminal', 'change_dir', { path });
  }

  async getCwd() {
    return this.sendRequest('terminal', 'getcwd', {});
  }

  setOnOutput(callback) {
    this.onOutputCallback = callback;
  }

  setOnError(callback) {
    this.onErrorCallback = callback;
  }

  setOnConnected(callback) {
    this.onConnectedCallback = callback;
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.connected = false;
  }
}

// 导出单例实例
export const terminalClient = new TerminalClient();
