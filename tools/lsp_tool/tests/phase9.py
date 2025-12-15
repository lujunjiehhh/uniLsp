def run(client, args):
    """Run Phase 9 tests"""
    uri = args.target_file or "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/LspServerStartupActivity.kt"

    try:
        if not client.connect():
            return

        print()

        # Test 1: workspace/symbol
        print("=" * 50)
        print("Test 1: workspace/symbol")
        print("=" * 50)
        result = client.send_request("workspace/symbol", {"query": "Lsp"})
        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            symbols = result.get("result", [])
            print(f"[OK] Found {len(symbols)} symbols")
            for s in symbols[:3]:
                print(f"    - {s.get('name')} ({s.get('kind')})")
        print()

        # Test 2: textDocument/signatureHelp
        print("=" * 50)
        print("Test 2: textDocument/signatureHelp")
        print("=" * 50)
        result = client.send_request("textDocument/signatureHelp", {
            "textDocument": {"uri": uri},
            "position": {"line": 50, "character": 20}
        })
        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            print(f"[OK] Response received: {type(result.get('result'))}")
        print()

        # Test 3: textDocument/formatting
        print("=" * 50)
        print("Test 3: textDocument/formatting")
        print("=" * 50)
        result = client.send_request("textDocument/formatting", {
            "textDocument": {"uri": uri},
            "options": {"tabSize": 4, "insertSpaces": True}
        })
        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            edits = result.get("result", [])
            print(f"[OK] Got {len(edits) if edits else 0} text edits")
        print()

        # Test 4: textDocument/codeAction
        print("=" * 50)
        print("Test 4: textDocument/codeAction")
        print("=" * 50)
        result = client.send_request("textDocument/codeAction", {
            "textDocument": {"uri": uri},
            "range": {
                "start": {"line": 10, "character": 0},
                "end": {"line": 10, "character": 50}
            },
            "context": {"diagnostics": []}
        })
        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            actions = result.get("result", [])
            print(f"[OK] Got {len(actions) if actions else 0} code actions")
        print()

        # Test 5: textDocument/implementation
        print("=" * 50)
        print("Test 5: textDocument/implementation")
        print("=" * 50)
        result = client.send_request("textDocument/implementation", {
            "textDocument": {"uri": uri},
            "position": {"line": 20, "character": 10}
        })
        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            print(f"[OK] Response received")
        print()

        # Test 6: textDocument/inlayHint
        print("=" * 50)
        print("Test 6: textDocument/inlayHint")
        print("=" * 50)
        result = client.send_request("textDocument/inlayHint", {
            "textDocument": {"uri": uri},
            "range": {
                "start": {"line": 0, "character": 0},
                "end": {"line": 50, "character": 0}
            }
        })
        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            hints = result.get("result", [])
            print(f"[OK] Got {len(hints) if hints else 0} inlay hints")
        print()

        print("=" * 50)
        print("[SUCCESS] All Phase 9 features tested!")
        print("=" * 50)

    except Exception as e:
        print(f"[ERROR] {e}")
    finally:
        client.disconnect()
