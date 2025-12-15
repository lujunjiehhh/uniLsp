import sys

from ..utils import validate_schema

# LSP 3.17 Schemas
LSP_SCHEMAS = {
    "textDocument/hover": {
        "nullable": True,
        "type": "object",
        "requiredFields": ["contents"]
    },
    "textDocument/definition": {
        "nullable": True,
        "type": "object",  # Location or Location[]
        "requiredFields": ["uri", "range"]
    },
    "textDocument/references": {
        "nullable": True,
        "type": "array",
        "itemFields": ["uri", "range"]
    },
    "textDocument/documentSymbol": {
        "nullable": True,
        "type": "array",
        "itemFields": ["name", "kind", "range", "selectionRange"]
    },
    "textDocument/signatureHelp": {
        "nullable": True,
        "type": "object",
        "requiredFields": ["signatures"]
    },
    "textDocument/codeAction": {
        "nullable": True,
        "type": "array",
        "itemFields": ["title"]
    },
    "textDocument/formatting": {
        "nullable": True,
        "type": "array",
        "itemFields": ["range", "newText"]
    },
    "textDocument/inlayHint": {
        "nullable": True,
        "type": "array",
        "itemFields": ["position", "label"]
    },
    "textDocument/implementation": {
        "nullable": True,
        "type": "array",
        "itemFields": ["uri", "range"]
    },
    "workspace/symbol": {
        "nullable": True,
        "type": "array",
        "itemFields": ["name", "kind", "location"]
    },
    "textDocument/typeDefinition": {
        "nullable": True,
        "type": "object",
        "requiredFields": ["uri", "range"]
    },
    "textDocument/semanticTokens/full": {
        "nullable": True,
        "type": "object",
        "requiredFields": ["data"]
    }
}


def run(client, args):
    """Run compliance checks"""
    print("=" * 70)
    print("  LSP 3.17 Compliance Check")
    print("=" * 70)

    test_file = args.target_file or "file:///f:/code/env/IntellijLsp/src/main/kotlin/com/frenchef/intellijlsp/handlers/WorkspaceSymbolHandler.kt"

    results = []

    try:
        if not client.connect():
            return

        # Tests
        tests = [
            ("textDocument/hover", {
                "textDocument": {"uri": test_file},
                "position": {"line": 11, "character": 10}
            }),
            ("textDocument/definition", {
                "textDocument": {"uri": test_file},
                "position": {"line": 17, "character": 20}
            }),
            ("textDocument/references", {
                "textDocument": {"uri": test_file},
                "position": {"line": 12, "character": 20},
                "context": {"includeDeclaration": True}
            }),
            ("textDocument/documentSymbol", {
                "textDocument": {"uri": test_file}
            }),
            ("textDocument/signatureHelp", {
                "textDocument": {"uri": test_file},
                "position": {"line": 21, "character": 55}
            }),
            ("textDocument/codeAction", {
                "textDocument": {"uri": test_file},
                "range": {
                    "start": {"line": 12, "character": 0},
                    "end": {"line": 12, "character": 50}
                },
                "context": {"diagnostics": []}
            }),
            ("textDocument/formatting", {
                "textDocument": {"uri": test_file},
                "options": {"tabSize": 4, "insertSpaces": True}
            }),
            ("textDocument/inlayHint", {
                "textDocument": {"uri": test_file},
                "range": {
                    "start": {"line": 0, "character": 0},
                    "end": {"line": 30, "character": 0}
                }
            }),
            ("textDocument/implementation", {
                "textDocument": {"uri": test_file},
                "position": {"line": 11, "character": 10}
            }),
            ("workspace/symbol", {"query": "Handler"}),
            ("textDocument/typeDefinition", {
                "textDocument": {"uri": test_file},
                "position": {"line": 12, "character": 30}
            }),
            ("textDocument/semanticTokens/full", {
                "textDocument": {"uri": test_file}
            })
        ]

        for method, params in tests:
            print(f"\n{'=' * 60}")
            print(f"  {method}")
            print(f"{'=' * 60}")

            try:
                result = client.send_request(method, params)
                schema = LSP_SCHEMAS.get(method, {"nullable": True})
                passed, issues = validate_schema(method, result, schema)

                response = result.get("result")

                if result.get("error"):
                    print(f"[ERROR] {result['error']}")
                    results.append((method, False, str(result['error'])))
                elif passed:
                    # Print brief result
                    if response is None:
                        print("[OK] null (Allowed)")
                    elif isinstance(response, list):
                        print(f"[OK] Returned {len(response)} items")
                        if response:
                            print(f"     First item keys: {list(response[0].keys())}")
                    elif isinstance(response, dict):
                        print(f"[OK] Returned object")
                        print(f"     Keys: {list(response.keys())}")
                    results.append((method, True, "Compliant"))
                else:
                    print(f"[FAIL] Schema issues:")
                    for issue in issues:
                        print(f"       - {issue}")
                    results.append((method, False, "; ".join(issues)))

            except Exception as e:
                print(f"[ERROR] {e}")
                results.append((method, False, str(e)))

        # Summary
        print("\n" + "=" * 70)
        print("  Summary Report")
        print("=" * 70)
        passed_count = sum(1 for _, p, _ in results if p)
        total = len(results)
        print(f"\nPassed: {passed_count}/{total}")

        if passed_count < total:
            print("\nFailures:")
            for method, passed, msg in results:
                if not passed:
                    print(f"  - {method}: {msg}")

        print("\n" + "=" * 70)

    except Exception as e:
        print(f"[ERROR] {e}")
    finally:
        client.disconnect()
