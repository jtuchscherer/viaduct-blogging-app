#!/bin/bash

# Normalise a GraphQL introspection response into a stable, diffable listing.
# Usage: ./schema-normalize.sh < introspection.json
# Requires jq (also used by query-tests.sh). Called by schema-check.sh.
# Keeps the snapshot format: type kinds, field return types, input names and enum values.

set -euo pipefail

# Slurp to require exactly one JSON document, including rejecting empty input.
# Raw output writes plain text; -e also rejects filters that produce no result.
exec jq -ers '
    def render:
        if . == null then "?"
        elif .kind == "NON_NULL" then (.ofType | render) + "!"
        elif .kind == "LIST" then "[" + (.ofType | render) + "]"
        else .name // "?"
        end;

    if length != 1 then error("expected exactly one introspection response")
    else .[0]
    end
    | if .errors then error("introspection failed: " + (.errors | tojson | .[:400]))
      else .
      end
    | if (.data.__schema.types | type) != "array" then
          error("expected data.__schema.types array")
      else .data.__schema.types
      end
    | [
        .[]
        | select(.name | startswith("__") | not)
        | . as $t
        | "\(.kind) \(.name)",
          (.fields // [] | .[] | "  \($t.name).\(.name): \(.type | render)"),
          (.inputFields // [] | .[] | "  \($t.name).\(.name) (input)"),
          (.enumValues // [] | .[] | "  \($t.name).\(.name) (enum)")
      ]
    # jq sorts strings by code point, independent of the machine locale.
    | sort | join("\n")
'
