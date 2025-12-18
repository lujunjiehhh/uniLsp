#!/usr/bin/env python3
"""
测试调用层次和类型层次功能
验证 incomingCalls/outgoingCalls 和 supertypes/subtypes 是否返回空结果
"""

import json
import socket
import sys


class LspTestClient:
    def __init__(self, host: str = "localhost", port: int = 2087):
        self.host = host
        self.port = port
        self.sock = None
        self.request_id = 0

    def connect(self):
        """连接 LSP 服务器"""
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.settimeout(30)
            self.sock.connect((self.host, self.port))
            print(f"[OK] 已连接到 {self.host}:{self.port}")
            return True
        except Exception as e:
            print(f"[ERROR] 连接失败 {self.host}:{self.port}: {e}")
            return False

    def disconnect(self):
        if self.sock:
            self.sock.close()
            self.sock = None

    def send_request(self, method: str, params: dict = None) -> dict:
        """发送 JSON-RPC 请求"""
        if not self.sock:
            raise ConnectionError("未连接")

        self.request_id += 1
        request = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method,
            "params": params or {},
        }
        self._send_message(request)
        return self._receive_response()

    def _send_message(self, message: dict):
        content = json.dumps(message)
        header = f"Content-Length: {len(content)}\r\n\r\n"
        full_message = header + content
        self.sock.sendall(full_message.encode("utf-8"))
        print(f"=> 发送: {message['method']}")

    def _receive_response(self) -> dict:
        header = b""
        while b"\r\n\r\n" not in header:
            chunk = self.sock.recv(1)
            if not chunk:
                raise ConnectionError("连接已关闭")
            header += chunk

        header_str = header.decode("utf-8")
        content_length = 0
        for line in header_str.split("\r\n"):
            if line.startswith("Content-Length:"):
                content_length = int(line.split(":")[1].strip())
                break

        body = b""
        while len(body) < content_length:
            chunk = self.sock.recv(content_length - len(body))
            if not chunk:
                raise ConnectionError("连接已关闭")
            body += chunk

        return json.loads(body.decode("utf-8"))


def test_call_hierarchy(
        client: LspTestClient, file_uri: str, line: int, character: int
):
    """测试调用层次"""
    print("\n" + "=" * 60)
    print("测试调用层次 (Call Hierarchy)")
    print("=" * 60)

    # 1. prepareCallHierarchy
    print(f"\n1. prepareCallHierarchy at {file_uri}:{line}:{character}")
    result = client.send_request(
        "textDocument/prepareCallHierarchy",
        {
            "textDocument": {"uri": file_uri},
            "position": {"line": line, "character": character},
        },
    )

    if "error" in result:
        print(f"   [ERROR] {result['error']}")
        return

    items = result.get("result", [])
    print(f"   [结果] 找到 {len(items)} 个方法")

    if not items:
        print("   [警告] prepareCallHierarchy 返回空，无法继续测试")
        return

    for i, item in enumerate(items):
        print(f"   [{i}] {item.get('name')} - {item.get('detail', 'N/A')}")

    # 使用第一个 item 进行 incoming/outgoing 测试
    test_item = items[0]

    # 2. incomingCalls
    print(f"\n2. callHierarchy/incomingCalls for '{test_item['name']}'")
    result = client.send_request("callHierarchy/incomingCalls", {"item": test_item})

    if "error" in result:
        print(f"   [ERROR] {result['error']}")
    else:
        calls = result.get("result", [])
        print(f"   [结果] 找到 {len(calls)} 个调用者")
        for i, call in enumerate(calls[:5]):  # 只显示前5个
            from_item = call.get("from", {})
            print(
                f"   [{i}] {from_item.get('name')} ({from_item.get('detail', 'N/A')})"
            )

    # 3. outgoingCalls
    print(f"\n3. callHierarchy/outgoingCalls for '{test_item['name']}'")
    result = client.send_request("callHierarchy/outgoingCalls", {"item": test_item})

    if "error" in result:
        print(f"   [ERROR] {result['error']}")
    else:
        calls = result.get("result", [])
        print(f"   [结果] 找到 {len(calls)} 个被调用方法")
        for i, call in enumerate(calls[:5]):
            to_item = call.get("to", {})
            print(f"   [{i}] {to_item.get('name')} ({to_item.get('detail', 'N/A')})")


def test_type_hierarchy(
        client: LspTestClient, file_uri: str, line: int, character: int
):
    """测试类型层次"""
    print("\n" + "=" * 60)
    print("测试类型层次 (Type Hierarchy)")
    print("=" * 60)

    # 1. prepareTypeHierarchy
    print(f"\n1. prepareTypeHierarchy at {file_uri}:{line}:{character}")
    result = client.send_request(
        "textDocument/prepareTypeHierarchy",
        {
            "textDocument": {"uri": file_uri},
            "position": {"line": line, "character": character},
        },
    )

    if "error" in result:
        print(f"   [ERROR] {result['error']}")
        return

    items = result.get("result", [])
    print(f"   [结果] 找到 {len(items)} 个类型")

    if not items:
        print("   [警告] prepareTypeHierarchy 返回空，无法继续测试")
        return

    for i, item in enumerate(items):
        print(f"   [{i}] {item.get('name')} - {item.get('detail', 'N/A')}")

    test_item = items[0]

    # 2. supertypes
    print(f"\n2. typeHierarchy/supertypes for '{test_item['name']}'")
    result = client.send_request("typeHierarchy/supertypes", {"item": test_item})

    if "error" in result:
        print(f"   [ERROR] {result['error']}")
    else:
        types = result.get("result", [])
        print(f"   [结果] 找到 {len(types)} 个父类型")
        for i, t in enumerate(types[:5]):
            print(f"   [{i}] {t.get('name')} ({t.get('detail', 'N/A')})")

    # 3. subtypes
    print(f"\n3. typeHierarchy/subtypes for '{test_item['name']}'")
    result = client.send_request("typeHierarchy/subtypes", {"item": test_item})

    if "error" in result:
        print(f"   [ERROR] {result['error']}")
    else:
        types = result.get("result", [])
        print(f"   [结果] 找到 {len(types)} 个子类型")
        for i, t in enumerate(types[:5]):
            print(f"   [{i}] {t.get('name')} ({t.get('detail', 'N/A')})")


def main():
    # 默认测试文件 - 使用 CallHierarchyHandler.kt 的 register 方法
    test_file = "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/CallHierarchyHandler.kt"

    # 光标位置 - 指向 register 方法名 (第34行，字符位置8)
    # fun register() {
    test_line = 33  # 0-indexed: 行34 = 索引33
    test_char = 8  # "fun " 后的 "register"

    if len(sys.argv) > 1:
        test_file = sys.argv[1]
    if len(sys.argv) > 2:
        test_line = int(sys.argv[2])
    if len(sys.argv) > 3:
        test_char = int(sys.argv[3])

    print(f"测试文件: {test_file}")
    print(f"位置: 行 {test_line}, 列 {test_char}")

    client = LspTestClient()

    if not client.connect():
        sys.exit(1)

    try:
        # 先发送 initialize
        init_result = client.send_request(
            "initialize",
            {
                "processId": None,
                "rootUri": "file:///f:/code/env/IntellijLsp",
                "capabilities": {},
            },
        )
        print(
            f"Initialize 结果: {init_result.get('result', {}).get('serverInfo', 'N/A')}"
        )

        # 测试调用层次
        test_call_hierarchy(client, test_file, test_line, test_char)

        # 测试类型层次 - 需要一个类声明的位置
        # 对于 TypeHierarchy，我们需要测试一个类
        type_test_file = "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/CallHierarchyHandler.kt"
        type_test_line = 24  # CallHierarchyHandler 类声明行
        type_test_char = 10

        test_type_hierarchy(client, type_test_file, type_test_line, type_test_char)

    finally:
        client.disconnect()

    print("\n" + "=" * 60)
    print("测试完成")
    print("=" * 60)


if __name__ == "__main__":
    main()
