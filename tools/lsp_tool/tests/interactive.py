import ast
import json


def run(client, args):
    """Run interactive mode"""
    print("=" * 60)
    print("  Interactive LSP Client")
    print("=" * 60)
    print("Enter LSP method name and params (JSON or Python dict)")
    print("Example: textDocument/hover {'textDocument': {'uri': '...'}, ...}")
    print("Enter 'quit' or 'exit' to stop")
    print("-" * 60)

    try:
        if not client.connect():
            return

        while True:
            try:
                line = input("LSP> ").strip()
                if not line:
                    continue
                if line.lower() in ["quit", "exit"]:
                    break

                # Parse method and params
                parts = line.split(maxsplit=1)
                method = parts[0]

                params = {}
                if len(parts) > 1:
                    raw_params = parts[1]
                    try:
                        # Try parsing as JSON first
                        params = json.loads(raw_params)
                    except json.JSONDecodeError:
                        try:
                            # Try parsing as Python literal
                            params = ast.literal_eval(raw_params)
                        except Exception as e:
                            print(f"[ERROR] Invalid params format: {e}")
                            continue

                # Send request
                print(f"Sending {method}...")
                result = client.send_request(method, params)

                # Print result
                if result.get("error"):
                    print(f"[ERROR] {result['error']}")
                else:
                    response = result.get("result")
                    formatted = json.dumps(response, indent=2, ensure_ascii=False)
                    if len(formatted) > 2000:
                        formatted = formatted[:2000] + "\n... (truncated)"

                    print(f"[RESULT]\n{formatted}")

            except KeyboardInterrupt:
                break
            except Exception as e:
                print(f"[ERROR] {e}")

    finally:
        client.disconnect()
