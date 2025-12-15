import argparse
import os
import sys

# Ensure package is in path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from lsp_tool.client import LspClient
from lsp_tool.tests import (
    compliance,
    phase9,
    phase10,
    all_endpoints,
    show_code_actions,
    resolve_action,
    detailed,
    interactive
)

MODULES = {
    "compliance": compliance,
    "phase9": phase9,
    "phase10": phase10,
    "all": all_endpoints,
    "code_actions": show_code_actions,
    "resolve": resolve_action,
    "detailed": detailed,
    "interactive": interactive
}


def main():
    parser = argparse.ArgumentParser(description="Unified LSP Test Tool")
    parser.add_argument("command", choices=MODULES.keys(), help="Test suite to run")
    parser.add_argument("--host", default="localhost", help="LSP server host (default: localhost)")
    parser.add_argument("--port", type=int, default=2087, help="LSP server port (default: 2087)")
    parser.add_argument("--file", dest="target_file", help="Target file URI for tests")

    args = parser.parse_args()

    client = LspClient(args.host, args.port)

    module = MODULES.get(args.command)
    if module:
        module.run(client, args)
    else:
        print(f"Unknown command: {args.command}")
        sys.exit(1)


if __name__ == "__main__":
    main()
