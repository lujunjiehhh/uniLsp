from ..utils import print_result


def run(client, args):
    """Run detailed tests for unusual endpoints"""
    workspace_symbol_handler = args.target_file or "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/WorkspaceSymbolHandler.kt"

    try:
        if not client.connect():
            return

        # 1. textDocument/signatureHelp
        print("\n" + "=" * 60)
        print("  TEST 1: textDocument/signatureHelp")
        print("=" * 60)

        result = client.send_request("textDocument/signatureHelp", {
            "textDocument": {"uri": workspace_symbol_handler},
            "position": {"line": 21, "character": 55}
        })
        print_result("signatureHelp (line 21, char 55)", result)

        result = client.send_request("textDocument/signatureHelp", {
            "textDocument": {"uri": workspace_symbol_handler},
            "position": {"line": 16, "character": 35}
        })
        print_result("signatureHelp (line 16, char 35)", result)

        # 2. workspace/symbol
        print("\n" + "=" * 60)
        print("  TEST 2: workspace/symbol queries")
        print("=" * 60)

        for query in ["Lsp", "Worker", "Provider", "Activity", "Symbol"]:
            result = client.send_request("workspace/symbol", {"query": query})
            symbols = result.get("result", [])
            print(f"\nQuery '{query}': Found {len(symbols)} symbols")
            for s in symbols[:5]:
                print(f"    - {s.get('name')} ({s.get('kind')})")

        # 3. textDocument/hover
        print("\n" + "=" * 60)
        print("  TEST 3: textDocument/hover")
        print("=" * 60)

        result = client.send_request("textDocument/hover", {
            "textDocument": {"uri": workspace_symbol_handler},
            "position": {"line": 11, "character": 10}
        })
        print_result("hover on class name (line 11)", result)

        result = client.send_request("textDocument/hover", {
            "textDocument": {"uri": workspace_symbol_handler},
            "position": {"line": 12, "character": 30}
        })
        print_result("hover on 'Project' (line 12)", result)

        # 4. textDocument/codeAction
        print("\n" + "=" * 60)
        print("  TEST 4: textDocument/codeAction (val delete)")
        print("=" * 60)

        # Line 12
        result = client.send_request("textDocument/codeAction", {
            "textDocument": {"uri": workspace_symbol_handler},
            "range": {
                "start": {"line": 12, "character": 0},
                "end": {"line": 12, "character": 50}
            },
            "context": {"diagnostics": []}
        })
        print_result("codeAction on line 12", result)

        # Line 13
        result = client.send_request("textDocument/codeAction", {
            "textDocument": {"uri": workspace_symbol_handler},
            "range": {
                "start": {"line": 13, "character": 0},
                "end": {"line": 13, "character": 50}
            },
            "context": {"diagnostics": []}
        })
        print_result("codeAction on line 13", result)

        # Constructor range
        result = client.send_request("textDocument/codeAction", {
            "textDocument": {"uri": workspace_symbol_handler},
            "range": {
                "start": {"line": 11, "character": 0},
                "end": {"line": 14, "character": 10}
            },
            "context": {"diagnostics": []}
        })
        print_result("codeAction on constructor (lines 11-14)", result)

        # 5. textDocument/typeDefinition
        print("\n" + "=" * 60)
        print("  TEST 5: textDocument/typeDefinition")
        print("=" * 60)

        result = client.send_request("textDocument/typeDefinition", {
            "textDocument": {"uri": workspace_symbol_handler},
            "position": {"line": 12, "character": 20}
        })
        print_result("typeDefinition on 'project'", result)

        print("\n" + "=" * 60)
        print("  [COMPLETE] All detailed tests finished!")
        print("=" * 60 + "\n")

    except Exception as e:
        print(f"[ERROR] {e}")
    finally:
        client.disconnect()
