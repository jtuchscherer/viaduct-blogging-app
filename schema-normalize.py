#!/usr/bin/env python3
"""Normalise a GraphQL introspection response into a stable, diffable listing.

Reads the introspection JSON on stdin and writes one sorted line per type, field, input field
and enum value. Sorting keeps the output stable against field-ordering churn, so a diff shows
only real contract changes. Used by schema-check.sh.
"""
import json
import sys


def render(t):
    """Render a possibly-wrapped type reference as SDL-ish text (e.g. [Post!]!)."""
    if t is None:
        return "?"
    kind, name, of = t.get("kind"), t.get("name"), t.get("ofType")
    if kind == "NON_NULL":
        return render(of) + "!"
    if kind == "LIST":
        return "[" + render(of) + "]"
    return name or "?"


def main():
    doc = json.load(sys.stdin)
    if "errors" in doc:
        sys.exit("introspection failed: " + json.dumps(doc["errors"])[:400])

    lines = []
    for t in doc["data"]["__schema"]["types"]:
        name = t["name"]
        if name.startswith("__"):  # skip introspection meta-types
            continue
        lines.append("{} {}".format(t["kind"], name))
        for f in t.get("fields") or []:
            lines.append("  {}.{}: {}".format(name, f["name"], render(f["type"])))
        for f in t.get("inputFields") or []:
            lines.append("  {}.{} (input)".format(name, f["name"]))
        for v in t.get("enumValues") or []:
            lines.append("  {}.{} (enum)".format(name, v["name"]))
    print("\n".join(sorted(lines)))


if __name__ == "__main__":
    main()
