def run(client, args):
    """Show detailed code actions"""
    test_file = args.target_file or "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/WorkspaceSymbolHandler.kt"

    print("=" * 60)
    print("  textDocument/codeAction Details")
    print("=" * 60)

    try:
        if not client.connect():
            return

        # Test at specific location
        result = client.send_request("textDocument/codeAction", {
            "textDocument": {"uri": test_file},
            "range": {
                "start": {"line": 12, "character": 0},
                "end": {"line": 12, "character": 50}
            },
            "context": {"diagnostics": []}
        })

        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            actions = result.get("result", [])
            print(f"\nFound {len(actions)} CodeActions:\n")
            for i, action in enumerate(actions, 1):
                print(f"{i}. {action.get('title')}")
                print(f"   kind: {action.get('kind')}")
                print(f"   isPreferred: {action.get('isPreferred')}")
                print()

    except Exception as e:
        print(f"[ERROR] {e}")
    finally:
        client.disconnect()
