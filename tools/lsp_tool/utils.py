import json


def print_result(name: str, result: dict):
    """Print a readable summary of the LSP response"""
    print(f"\n{'=' * 60}")
    print(f"  {name}")
    print(f"{'=' * 60}")

    if result.get("error"):
        print(f"[ERROR] Code: {result['error'].get('code')}")
        print(f"        Message: {result['error'].get('message')}")
        return False

    response = result.get("result")
    if response is None:
        print("[RESULT] null")
        return True

    # Format JSON
    formatted = json.dumps(response, indent=2, ensure_ascii=False)
    # Truncate if too long
    if len(formatted) > 2000:
        formatted = formatted[:2000] + "\n... (truncated)"
    print(f"[RESULT]\n{formatted}")
    return True


def validate_schema(name: str, result: dict, schema: dict) -> tuple:
    """Validate response against a simple schema"""
    issues = []

    if result.get("error"):
        return False, [f"Error response: {result['error']}"]

    response = result.get("result")

    # Check nullable
    if response is None:
        if schema.get("nullable", False):
            return True, []
        else:
            return False, ["Returned null but schema does not allow it"]

    # Check type
    expected_type = schema.get("type")
    if expected_type == "array":
        if not isinstance(response, list):
            issues.append(f"Expected array, got {type(response).__name__}")
        else:
            # Check array items
            item_fields = schema.get("itemFields", [])
            if response and item_fields:
                item = response[0]
                for field in item_fields:
                    if field not in item:
                        issues.append(f"Array item missing required field: {field}")

    elif expected_type == "object":
        if not isinstance(response, dict):
            issues.append(f"Expected object, got {type(response).__name__}")
        else:
            required_fields = schema.get("requiredFields", [])
            for field in required_fields:
                if field not in response:
                    issues.append(f"Missing required field: {field}")

    return len(issues) == 0, issues
