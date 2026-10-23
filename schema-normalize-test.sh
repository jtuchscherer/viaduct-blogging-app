#!/bin/bash

# Behavior tests for the schema snapshot formatter; requires only Bash and jq.
# Run from any directory: bash /path/to/schema-normalize-test.sh
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
PASSED=0

assert_output() {
    local label=$1 input=$2 expected=$3
    printf '%s\n' "$expected" > "$TEST_DIR/expected"
    printf '%s' "$input" | bash "$SCRIPT_DIR/schema-normalize.sh" > "$TEST_DIR/actual"
    if ! diff -u "$TEST_DIR/expected" "$TEST_DIR/actual"; then
        echo "FAIL: $label" >&2
        exit 1
    fi
    PASSED=$((PASSED + 1))
}

assert_rejected() {
    local label=$1 input=$2 message=$3
    if printf '%s' "$input" | bash "$SCRIPT_DIR/schema-normalize.sh" \
        > "$TEST_DIR/actual" 2> "$TEST_DIR/error"; then
        echo "FAIL: $label was accepted" >&2
        exit 1
    fi
    if [ -s "$TEST_DIR/actual" ] || ! grep -Fq "$message" "$TEST_DIR/error"; then
        echo "FAIL: $label must report the error on stderr without a snapshot" >&2
        cat "$TEST_DIR/error" >&2
        exit 1
    fi
    PASSED=$((PASSED + 1))
}

SCHEMA=$(cat <<'JSON'
{"data":{"__schema":{"types":[
  {"kind":"OBJECT","name":"Query","fields":[
    {"name":"posts","type":{"kind":"NON_NULL","ofType":{"kind":"LIST","ofType":{"kind":"NON_NULL","ofType":{"kind":"OBJECT","name":"Post"}}}}},
    {"name":"post","type":{"kind":"OBJECT","name":"Post"}},
    {"name":"matrix","type":{"kind":"LIST","ofType":{"kind":"LIST","ofType":{"kind":"NON_NULL","ofType":{"kind":"SCALAR","name":"Int"}}}}}
  ]},
  {"kind":"ENUM","name":"Status","enumValues":[{"name":"PUBLISHED"},{"name":"DRAFT"}]},
  {"kind":"INPUT_OBJECT","name":"PostInput","inputFields":[{"name":"title"},{"name":"body"}]},
  {"kind":"OBJECT","name":"Post","fields":[{"name":"title","type":{"kind":"NON_NULL","ofType":{"kind":"SCALAR","name":"String"}}}]},
  {"kind":"SCALAR","name":"String","fields":null,"inputFields":null,"enumValues":null},
  {"kind":"SCALAR","name":"Int"},
  {"kind":"OBJECT","name":"__Schema","fields":[{"name":"types","type":{"name":"Ignored"}}]},
  {"kind":"ENUM","name":"__TypeKind","enumValues":[{"name":"OBJECT"}]}
]}}}
JSON
)

EXPECTED=$(cat <<'TEXT'
  Post.title: String!
  PostInput.body (input)
  PostInput.title (input)
  Query.matrix: [[Int!]]
  Query.post: Post
  Query.posts: [Post!]!
  Status.DRAFT (enum)
  Status.PUBLISHED (enum)
ENUM Status
INPUT_OBJECT PostInput
OBJECT Post
OBJECT Query
SCALAR Int
SCALAR String
TEXT
)

assert_output "sorted contract with nested types and no introspection meta-types" "$SCHEMA" "$EXPECTED"

REORDERED=$(printf '%s' "$SCHEMA" | jq '
    .data.__schema.types |= (reverse | map(
        if .fields then .fields |= reverse else . end
        | if .inputFields then .inputFields |= reverse else . end
        | if .enumValues then .enumValues |= reverse else . end
    ))
')
assert_output "type and member ordering does not affect the snapshot" "$REORDERED" "$EXPECTED"
assert_output "empty schema produces a single newline" '{"data":{"__schema":{"types":[]}}}' ''
assert_output "incomplete type references retain the unknown marker" \
    '{"data":{"__schema":{"types":[{"kind":"OBJECT","name":"Query","fields":[{"name":"missing","type":null},{"name":"wrapped","type":{"kind":"NON_NULL","ofType":{"kind":"LIST"}}}]}]}}}' \
    $'  Query.missing: ?\n  Query.wrapped: [?]!\nOBJECT Query'

assert_rejected "GraphQL errors" '{"errors":[{"message":"denied"}],"data":null}' 'introspection failed:'
assert_rejected "partial data with GraphQL errors" \
    '{"errors":[{"message":"partial"}],"data":{"__schema":{"types":[]}}}' 'introspection failed:'
assert_rejected "malformed JSON" '{' 'parse error'
assert_rejected "missing schema" '{"data":{}}' 'expected data.__schema.types array'
assert_rejected "invalid type collection" '{"data":{"__schema":{"types":{}}}}' 'expected data.__schema.types array'
assert_rejected "empty input" '' 'expected exactly one introspection response'
assert_rejected "multiple documents" \
    $'{"data":{"__schema":{"types":[]}}}\n{"data":{"__schema":{"types":[]}}}' \
    'expected exactly one introspection response'

echo "All $PASSED schema normalizer tests passed."
