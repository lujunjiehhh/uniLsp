import json
import socket
import time


class LspClient:
    def __init__(self, host: str, port: int):
        self.host = host
        self.port = port
        self.sock = None
        self.request_id = 0

    def connect(self):
        """Connect to LSP server"""
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.settimeout(30)
            self.sock.connect((self.host, self.port))
            print(f"[OK] Connected to {self.host}:{self.port}")
            return True
        except Exception as e:
            print(f"[ERROR] Failed to connect to {self.host}:{self.port}: {e}")
            return False

    def disconnect(self):
        """Disconnect"""
        if self.sock:
            try:
                self.sock.close()
                print("Disconnected")
            except:
                pass
            self.sock = None

    def send_request(self, method: str, params: dict = None) -> dict:
        """Send JSON-RPC request"""
        if not self.sock:
            raise ConnectionError("Not connected")

        self.request_id += 1
        request = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method,
            "params": params or {}
        }
        self._send_message(request)
        return self._receive_response()

    def send_notification(self, method: str, params: dict = None):
        """Send JSON-RPC notification (no response needed)"""
        if not self.sock:
            raise ConnectionError("Not connected")

        notification = {
            "jsonrpc": "2.0",
            "method": method,
            "params": params or {}
        }
        self._send_message(notification)

    def _send_message(self, message: dict):
        """Send LSP message"""
        content = json.dumps(message)
        header = f"Content-Length: {len(content)}\r\n\r\n"
        full_message = header + content
        self.sock.sendall(full_message.encode('utf-8'))
        # print(f"=> Sent: {message['method']}")

    def _receive_response(self) -> dict:
        """Receive LSP response"""
        # Read header
        header = b""
        while b"\r\n\r\n" not in header:
            chunk = self.sock.recv(1)
            if not chunk:
                raise ConnectionError("Connection closed")
            header += chunk

        # Parse Content-Length
        header_str = header.decode('utf-8')
        content_length = 0
        for line in header_str.split('\r\n'):
            if line.startswith('Content-Length:'):
                content_length = int(line.split(':')[1].strip())
                break

        # Read body
        body = b""
        while len(body) < content_length:
            chunk = self.sock.recv(content_length - len(body))
            if not chunk:
                raise ConnectionError("Connection closed")
            body += chunk

        response = json.loads(body.decode('utf-8'))
        return response
