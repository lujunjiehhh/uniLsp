def _score_action(action: dict) -> int:
    """Heuristic scoring for actions that resolve to WorkspaceEdit"""
    title = (action.get("title") or "")
    kind = (action.get("kind") or "")
    data = action.get("data") if isinstance(action.get("data"), dict) else {}
    action_type = data.get("actionType")

    score = 0
    if action_type == "quickfix":
        score += 100
    if kind == "quickfix":
        score += 10

    low = title.lower()
    if "remove" in low or "delete" in low or "移除" in title or "删除" in title:
        score += 20
    if "val" in low or "var" in low:
        score += 10
    if "create test" in low or "创建测试" in title:
        score -= 50

    return score


def _pick_candidates(actions: list) -> list:
    """Pick candidates prioritizing quickfixes"""
    with_data = [a for a in actions if isinstance(a, dict) and a.get("data")]
    quickfixes = [
        a for a in with_data
        if isinstance(a.get("data"), dict) and a["data"].get("actionType") == "quickfix"
    ]
    candidates = quickfixes or with_data or actions
    return sorted(candidates, key=_score_action, reverse=True)


def run(client, args):
    """Run codeAction/resolve test"""
    test_file = args.target_file or "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/WorkspaceSymbolHandler.kt"

    print("=" * 60)
    print("  codeAction/resolve Test")
    print("=" * 60)

    try:
        if not client.connect():
            return

        # 1. Get codeActions
        print("\n[Step 1] Fetching textDocument/codeAction...")
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
            return

        actions = result.get("result", [])
        print(f"Found {len(actions)} CodeActions")

        if not actions:
            print("[SKIP] No CodeActions found for test")
            return

        # 2. Try resolve
        print("\n[Step 2] Sending codeAction/resolve...")
        candidates = _pick_candidates(actions)

        resolved = None
        for i, action in enumerate(candidates[:10]):
            print(f"\nAttempt #{i + 1}: {action.get('title')}")
            print(f"  kind: {action.get('kind')}")

            resolve_result = client.send_request("codeAction/resolve", action)
            if resolve_result.get("error"):
                print(f"[ERROR] {resolve_result['error']}")
                continue

            resolved = resolve_result.get("result", {})
            edit = resolved.get("edit")
            print("Resolved CodeAction:")
            print(f"  title: {resolved.get('title')}")
            print(f"  edit: {edit}")

            if edit:
                changes = edit.get("changes", {})
                for uri, edits in changes.items():
                    print(f"\n  File: {uri}")
                    for e in edits:
                        print(f"    - range: {e.get('range')}")
                        print(f"      newText: {repr(e.get('newText')[:50] if e.get('newText') else '')}")
                print("\n[SUCCESS] codeAction/resolve returned WorkspaceEdit!")
                break

        if not resolved or not resolved.get("edit"):
            print("\n[WARN] No candidate returned an edit")

    except Exception as e:
        print(f"[ERROR] {e}")
    finally:
        client.disconnect()
