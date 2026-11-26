#!/usr/bin/env bash
set -euo pipefail

source "$(dirname -- "${BASH_SOURCE[0]}")/release-publish.sh"

fixtures=$(mktemp -d)
trap 'rm -f -- "$fixtures/"*; rmdir -- "$fixtures"' EXIT
trace="$fixtures/requests"
notes_file="$fixtures/notes.md"
jar_file="$fixtures/industrial-overkill-0.1.0-alpha.jar"
printf 'Release notes\n' > "$notes_file"
touch "$jar_file"

export TAG=v0.1.0-alpha RELEASE_TYPE=alpha
export CURSEFORGE_PROJECT_ID=123 CURSEFORGE_TOKEN=test
unset MODRINTH_PROJECT_ID MODRINTH_TOKEN

cat > "$fixtures/versions.json" <<'JSON'
[
  {"id": 100, "gameVersionTypeID": 1, "name": "1.21.1"},
  {"id": 101, "gameVersionTypeID": 2, "name": "1.21.1"},
  {"id": 102, "gameVersionTypeID": 3, "name": "NeoForge"},
  {"id": 103, "gameVersionTypeID": 4, "name": "Client"},
  {"id": 104, "gameVersionTypeID": 4, "name": "Server"},
  {"id": 105, "gameVersionTypeID": 5, "name": "Java 21"}
]
JSON
cat > "$fixtures/types.json" <<'JSON'
[
  {"id": 1, "slug": "addons"},
  {"id": 2, "slug": "minecraft-1-21"},
  {"id": 3, "slug": "modloader"},
  {"id": 4, "slug": "environment"},
  {"id": 5, "slug": "java"}
]
JSON

http_code() {
  local url=${!#}
  printf '%s\n' "$url" >> "$trace"
  case "$url" in
    https://minecraft.curseforge.com/api/game/versions) cp "$fixtures/versions.json" "$1" ;;
    https://minecraft.curseforge.com/api/game/version-types) cp "$fixtures/types.json" "$1" ;;
    https://minecraft.curseforge.com/api/projects/123/files) printf '[]' > "$1" ;;
    https://minecraft.curseforge.com/api/projects/123/upload-file)
      jq -e '.gameVersions == [101, 102, 103, 104, 105] and .releaseType == "alpha"
        and .relations.projects == [{slug: "mmdlib", type: "requiredDependency"}]' \
        "$work/curseforge.json" > /dev/null || die "Wrong CurseForge metadata"
      printf '{"id":1}' > "$1"
      ;;
    https://api.modrinth.com/v2/project/AbC123/version/0.1.0-alpha)
      printf '{}' > "$1"
      printf 404
      return
      ;;
    https://api.modrinth.com/v2/version)
      jq -e '.project_id == "AbC123" and .version_type == "alpha" and .version_number == "0.1.0-alpha"
        and .loaders == ["neoforge"] and .game_versions == ["1.21.1"]' \
        "$work/modrinth.json" > /dev/null || die "Wrong Modrinth metadata"
      printf '{"id":"test-version"}' > "$1"
      ;;
    *) die "Unexpected request to ${url}" ;;
  esac
  printf 200
}

gh() {
  printf 'gh %s %s\n' "$1" "$2" >> "$trace"
  [[ "$2" != view ]]
}

for id_state in unset empty; do
  [[ "$id_state" == unset ]] || export MODRINTH_PROJECT_ID=''
  : > "$trace"
  upload 0.1.0-alpha "$notes_file" "$jar_file"
  grep -Fxq 'https://minecraft.curseforge.com/api/projects/123/upload-file' "$trace"
  grep -Fxq 'gh release create' "$trace"
  if grep -q 'api.modrinth.com' "$trace"; then
    die "A Modrinth request was made with the project ID ${id_state}"
  fi
done

export MODRINTH_PROJECT_ID=AbC123
if (check_env) > "$fixtures/preflight.log" 2>&1; then
  die "A configured Modrinth project must require a token"
fi
grep -q 'MODRINTH_TOKEN' "$fixtures/preflight.log"

export MODRINTH_TOKEN=test
: > "$trace"
upload 0.1.0-alpha "$notes_file" "$jar_file"
grep -Fxq 'https://api.modrinth.com/v2/project/AbC123/version/0.1.0-alpha' "$trace"
grep -Fxq 'https://api.modrinth.com/v2/version' "$trace"
grep -Fxq 'https://minecraft.curseforge.com/api/projects/123/upload-file' "$trace"
grep -Fxq 'gh release create' "$trace"
echo "Release publishing checks passed."
