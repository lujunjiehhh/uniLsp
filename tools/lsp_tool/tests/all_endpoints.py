from ..utils import print_result


def run(client, args):
    """Run all endpoints test"""
    # Test file URI
    test_file = args.target_file or "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/LspServerStartupActivity.kt"

    try:
        if not client.connect():
            return

        # ============================================================
        # Basic Endpoints
        # ============================================================

        # 1. textDocument/hover
        result = client.send_request("textDocument/hover", {
            "textDocument": {"uri": test_file},
            "position": {"line": 25, "character": 15}  # On class name
        })
        print_result("textDocument/hover", result)

        # 2. textDocument/definition
        result = client.send_request("textDocument/definition", {
            "textDocument": {"uri": test_file},
            "position": {"line": 30, "character": 20}
        })
        print_result("textDocument/definition", result)

        # 3. textDocument/references
        result = client.send_request("textDocument/references", {
            "textDocument": {"uri": test_file},
            "position": {"line": 25, "character": 15},
            "context": {"includeDeclaration": True}
        })
        print_result("textDocument/references", result)

        # 4. textDocument/completion
        result = client.send_request("textDocument/completion", {
            "textDocument": {"uri": test_file},
            "position": {"line": 50, "character": 10}
        })
        # Custom print for completion to avoid spam
        items = result.get("result", {}).get("items", []) if isinstance(result.get("result"), dict) else []
        print_result("textDocument/completion (summary)", {
            "result": {"itemCount": len(items)},
            "error": result.get("error")
        })

        # 5. textDocument/documentSymbol
        result = client.send_request("textDocument/documentSymbol", {
            "textDocument": {"uri": test_file}
        })
        print_result("textDocument/documentSymbol", result)

        # ============================================================
        # Phase 9 Extended Endpoints
        # ============================================================

        # 6. workspace/symbol
        result = client.send_request("workspace/symbol", {"query": "Handler"})
        print_result("workspace/symbol (query: Handler)", result)

        # 7. textDocument/signatureHelp
        result = client.send_request("textDocument/signatureHelp", {
            "textDocument": {"uri": test_file},
            "position": {"line": 100, "character": 30}
        })
        print_result("textDocument/signatureHelp", result)

        # 8. textDocument/formatting
        result = client.send_request("textDocument/formatting", {
            "textDocument": {"uri": test_file},
            "options": {"tabSize": 4, "insertSpaces": True}
        })
        print_result("textDocument/formatting", result)

        # 9. textDocument/rangeFormatting
        result = client.send_request("textDocument/rangeFormatting", {
            "textDocument": {"uri": test_file},
            "range": {
                "start": {"line": 20, "character": 0},
                "end": {"line": 30, "character": 0}
            },
            "options": {"tabSize": 4, "insertSpaces": True}
        })
        print_result("textDocument/rangeFormatting", result)

        # 10. textDocument/codeAction
        result = client.send_request("textDocument/codeAction", {
            "textDocument": {"uri": test_file},
            "range": {
                "start": {"line": 10, "character": 0},
                "end": {"line": 10, "character": 100}
            },
            "context": {"diagnostics": []}
        })
        print_result("textDocument/codeAction", result)

        # 11. textDocument/implementation
        result = client.send_request("textDocument/implementation", {
            "textDocument": {"uri": test_file},
            "position": {"line": 25, "character": 10}
        })
        print_result("textDocument/implementation", result)

        # 12. textDocument/inlayHint
        result = client.send_request("textDocument/inlayHint", {
            "textDocument": {"uri": test_file},
            "range": {
                "start": {"line": 0, "character": 0},
                "end": {"line": 30, "character": 0}
            }
        })
        print_result("textDocument/inlayHint", result)

        # 13. textDocument/typeDefinition
        result = client.send_request("textDocument/typeDefinition", {
            "textDocument": {"uri": test_file},
            "position": {"line": 30, "character": 15}
        })
        print_result("textDocument/typeDefinition", result)

        # 14. textDocument/semanticTokens/full
        result = client.send_request("textDocument/semanticTokens/full", {
            "textDocument": {"uri": test_file}
        })
        # Truncate large data
        if result.get("result") and result["result"].get("data"):
            data = result["result"]["data"]
            print_result("textDocument/semanticTokens/full", {
                "result": {"dataLength": len(data), "firstTokens": data[:20]},
                "error": None
            })
        else:
            print_result("textDocument/semanticTokens/full", result)

        print(f"\n{'=' * 60}")
        print("  [COMPLETE] All endpoints tested")
        print(f"{'=' * 60}\n")

    except Exception as e:
        print(f"\n[ERROR] {e}")
    finally:
        client.disconnect()
