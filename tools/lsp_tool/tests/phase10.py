def test_rename(client, uri):
    """Test US1: Rename Refactoring"""
    print("=" * 50)
    print("Test 1: textDocument/prepareRename")
    print("=" * 50)

    # Test prepareRename
    result = client.send_request("textDocument/prepareRename", {
        "textDocument": {"uri": uri},
        "position": {"line": 20, "character": 10}
    })

    if result.get("error"):
        print(f"[ERROR] {result['error']}")
    elif result.get("result") is None:
        print("[OK] Got null (element not renameable) - expected for some positions")
    else:
        res = result.get("result")
        print(f"[OK] Renameable: range={res.get('range')}, placeholder='{res.get('placeholder')}'")
    print()

    # Test rename
    print("=" * 50)
    print("Test 2: textDocument/rename")
    print("=" * 50)

    result = client.send_request("textDocument/rename", {
        "textDocument": {"uri": uri},
        "position": {"line": 20, "character": 10},
        "newName": "testNewName"
    })

    if result.get("error"):
        print(f"[ERROR] {result['error']}")
    elif result.get("result") is None:
        print("[OK] Got null (cannot rename) - expected for some positions")
    else:
        edit = result.get("result", {})
        changes = edit.get("changes", {})
        total_edits = sum(len(v) for v in changes.values())
        print(f"[OK] WorkspaceEdit: {len(changes)} files, {total_edits} edits")
    print()


def test_call_hierarchy(client, uri):
    """Test US2: Call Hierarchy"""
    print("=" * 50)
    print("Test 3: textDocument/prepareCallHierarchy")
    print("=" * 50)

    result = client.send_request("textDocument/prepareCallHierarchy", {
        "textDocument": {"uri": uri},
        "position": {"line": 111, "character": 10}  # getVirtualFile method
    })

    item = None
    if result.get("error"):
        print(f"[ERROR] {result['error']}")
    elif result.get("result") is None or len(result.get("result", [])) == 0:
        print("[WARN] No CallHierarchyItem found - try a different position")
    else:
        items = result.get("result", [])
        item = items[0]
        print(f"[OK] Found method: {item.get('name')} (kind={item.get('kind')})")
    print()

    if item:
        # Test incomingCalls
        print("=" * 50)
        print("Test 4: callHierarchy/incomingCalls")
        print("=" * 50)

        result = client.send_request("callHierarchy/incomingCalls", {
            "item": item
        })

        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            calls = result.get("result", [])
            print(f"[OK] Found {len(calls)} incoming calls")
            for call in calls[:3]:
                print(f"    - from: {call.get('from', {}).get('name')}")
        print()

        # Test outgoingCalls
        print("=" * 50)
        print("Test 5: callHierarchy/outgoingCalls")
        print("=" * 50)

        result = client.send_request("callHierarchy/outgoingCalls", {
            "item": item
        })

        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            calls = result.get("result", [])
            print(f"[OK] Found {len(calls)} outgoing calls")
            for call in calls[:3]:
                print(f"    - to: {call.get('to', {}).get('name')}")
        print()


def test_type_hierarchy(client, uri):
    """Test US3: Type Hierarchy"""
    print("=" * 50)
    print("Test 6: textDocument/prepareTypeHierarchy")
    print("=" * 50)

    result = client.send_request("textDocument/prepareTypeHierarchy", {
        "textDocument": {"uri": uri},
        "position": {"line": 12, "character": 10}
    })

    item = None
    if result.get("error"):
        print(f"[ERROR] {result['error']}")
    elif result.get("result") is None or len(result.get("result", [])) == 0:
        print("[WARN] No TypeHierarchyItem found - try a different position")
    else:
        items = result.get("result", [])
        item = items[0]
        print(f"[OK] Found class: {item.get('name')} (kind={item.get('kind')})")
    print()

    if item:
        # Test supertypes
        print("=" * 50)
        print("Test 7: typeHierarchy/supertypes")
        print("=" * 50)

        result = client.send_request("typeHierarchy/supertypes", {
            "item": item
        })

        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            types = result.get("result", [])
            print(f"[OK] Found {len(types)} supertypes")
            for t in types[:3]:
                print(f"    - {t.get('name')}")
        print()

        # Test subtypes
        print("=" * 50)
        print("Test 8: typeHierarchy/subtypes")
        print("=" * 50)

        result = client.send_request("typeHierarchy/subtypes", {
            "item": item
        })

        if result.get("error"):
            print(f"[ERROR] {result['error']}")
        else:
            types = result.get("result", [])
            print(f"[OK] Found {len(types)} subtypes")
            for t in types[:3]:
                print(f"    - {t.get('name')}")
        print()


def test_workspace_folders(client):
    """Test US5: Workspace Folders"""
    print("=" * 50)
    print("Test 9: workspace/didChangeWorkspaceFolders (notification)")
    print("=" * 50)

    try:
        client.send_notification("workspace/didChangeWorkspaceFolders", {
            "event": {
                "added": [
                    {"uri": "file:///f:/code/env/IntellijLsp/test-folder", "name": "test-folder"}
                ],
                "removed": []
            }
        })
        print("[OK] Notification sent successfully")
    except Exception as e:
        print(f"[ERROR] {e}")
    print()


def test_file_watching(client):
    """Test US6: File Watching"""
    print("=" * 50)
    print("Test 10: workspace/didChangeWatchedFiles (notification)")
    print("=" * 50)

    try:
        client.send_notification("workspace/didChangeWatchedFiles", {
            "changes": [
                {"uri": "file:///f:/code/env/IntellijLsp/test-file.kt", "type": 1},  # Created
                {"uri": "file:///f:/code/env/IntellijLsp/test-file.kt", "type": 2},  # Changed
            ]
        })
        print("[OK] Notification sent successfully")
    except Exception as e:
        print(f"[ERROR] {e}")
    print()


def run(client, args):
    """Run Phase 10 tests"""
    uri_startup = args.target_file or "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/LspServerStartupActivity.kt"
    uri_docmgr = "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/intellij/DocumentManager.kt"

    try:
        if not client.connect():
            return

        print()

        # US1: Rename
        test_rename(client, uri_startup)

        # US2: Call Hierarchy
        test_call_hierarchy(client, uri_docmgr)

        # US3: Type Hierarchy
        test_type_hierarchy(client, uri_startup)

        # US5: Workspace Folders
        test_workspace_folders(client)

        # US6: File Watching
        test_file_watching(client)

        print("=" * 50)
        print("[SUCCESS] All Phase 10 features tested!")
        print("=" * 50)

    except Exception as e:
        print(f"[ERROR] {e}")
    finally:
        client.disconnect()
