#!/usr/bin/env bash
set -euo pipefail

here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
user_agent='modcrafters/industrial-overkill (release)'

die() { echo "$1" >&2; exit 1; }

check_env() {
  [[ "${CURSEFORGE_PROJECT_ID:-}" =~ ^[0-9]+$ ]] || die "Set CURSEFORGE_PROJECT_ID to the numeric CurseForge project ID"
  [[ -n "${CURSEFORGE_TOKEN:-}" ]] || die "Set the CURSEFORGE_TOKEN repository secret"
  if [[ -n "${MODRINTH_PROJECT_ID:-}" ]]; then
    [[ "$MODRINTH_PROJECT_ID" =~ ^[A-Za-z0-9]+$ ]] || die "MODRINTH_PROJECT_ID must be a Modrinth project ID"
    [[ -n "${MODRINTH_TOKEN:-}" ]] || die "Set the MODRINTH_TOKEN repository secret for Modrinth publishing"
  fi
}

http_code() {
  local out=$1
  shift
  curl -sS -o "$out" -w '%{http_code}' "$@" || die "The request did not complete"
}

check_response() {
  local code=$1 operation=$2 body=$3
  if [[ "$code" != 200 ]]; then
    cat "$body" >&2
    die "${operation} returned HTTP ${code}"
  fi
}

curseforge_versions() {
  local code
  code=$(http_code "$work/versions.json" -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    https://minecraft.curseforge.com/api/game/versions)
  check_response "$code" "CurseForge game versions" "$work/versions.json"
  code=$(http_code "$work/types.json" -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    https://minecraft.curseforge.com/api/game/version-types)
  check_response "$code" "CurseForge game version types" "$work/types.json"
  python3 "$here/curseforge-version-ids.py" "$work/versions.json" "$work/types.json" "$game"
}

curseforge_upload() {
  local game_versions=$1 code
  code=$(http_code "$work/files.json" -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    "https://minecraft.curseforge.com/api/projects/${CURSEFORGE_PROJECT_ID}/files")
  if [[ "$code" == 200 ]]; then
    if jq -e --arg file "$(basename "$jar")" --arg display "$display" '
      (if type == "array" then . else (.data // .files // []) end)
      | any(.[]; .fileName == $file or .displayName == $display)
    ' "$work/files.json" >/dev/null; then
      echo "CurseForge already has ${display}"
      return
    fi
  else
    echo "CurseForge file list returned HTTP ${code}. A repeated run may upload the same JAR again." >&2
  fi
  jq -n --rawfile changelog "$notes" --arg display "$display" --arg channel "$RELEASE_TYPE" \
    --argjson game_versions "$game_versions" '{
      changelog: $changelog,
      changelogType: "markdown",
      displayName: $display,
      gameVersions: $game_versions,
      releaseType: $channel,
      relations: {projects: [{slug: "mmdlib", type: "requiredDependency"}]}
    }' > "$work/curseforge.json"
  code=$(http_code "$work/uploaded.json" -H "X-Api-Token: ${CURSEFORGE_TOKEN}" \
    -F "metadata=<${work}/curseforge.json;type=application/json" -F "file=@${jar}" \
    "https://minecraft.curseforge.com/api/projects/${CURSEFORGE_PROJECT_ID}/upload-file")
  check_response "$code" "CurseForge upload of ${display}" "$work/uploaded.json"
  echo "CurseForge ${display} published ($(jq -r .id "$work/uploaded.json"))"
}

modrinth_upload() {
  local code
  code=$(http_code "$work/exists.json" -H "User-Agent: ${user_agent}" -H "Authorization: ${MODRINTH_TOKEN}" \
    "https://api.modrinth.com/v2/project/${MODRINTH_PROJECT_ID}/version/${version}")
  if [[ "$code" == 200 ]]; then
    echo "Modrinth ${version} already exists"
    return
  fi
  [[ "$code" == 404 ]] || check_response "$code" "Modrinth version lookup" "$work/exists.json"

  jq -n --rawfile changelog "$notes" --arg display "$display" --arg version "$version" \
    --arg channel "$RELEASE_TYPE" --arg project_id "$MODRINTH_PROJECT_ID" --arg game "$game" '{
      name: $display,
      version_number: $version,
      changelog: $changelog,
      dependencies: [],
      game_versions: [$game],
      version_type: $channel,
      loaders: ["neoforge"],
      featured: true,
      project_id: $project_id,
      file_parts: ["jar"],
      primary_file: "jar",
      status: "listed",
      requested_status: "listed",
      environment: "client_and_server"
    }' > "$work/modrinth.json"
  code=$(http_code "$work/created.json" -H "User-Agent: ${user_agent}" -H "Authorization: ${MODRINTH_TOKEN}" \
    -F "data=<${work}/modrinth.json;type=application/json" -F "jar=@${jar}" \
    https://api.modrinth.com/v2/version)
  check_response "$code" "Modrinth upload of ${version}" "$work/created.json"
  echo "Modrinth ${version} published ($(jq -r .id "$work/created.json"))"
}

github_release() {
  if ! gh release view "$TAG" >/dev/null 2>&1; then
    local -a args=(release create "$TAG" --verify-tag --title "Industrial Overkill ${version}" --notes-file "$notes")
    [[ "$RELEASE_TYPE" == release ]] || args+=(--prerelease)
    gh "${args[@]}" "$jar"
  else
    gh release upload "$TAG" --clobber "$jar"
  fi
}

upload() (
  local version=$1 notes=$2 jar=$3 game_versions game display work
  check_env
  [[ -n "${TAG:-}" && "${TAG#v}" == "$version" ]] || die "The tag and JAR version must match"
  [[ -f "$notes" ]] || die "No release notes at ${notes}"
  [[ -f "$jar" && "$(basename "$jar")" == "industrial-overkill-${version}.jar" ]] || die "Missing release JAR at ${jar}"
  case "${RELEASE_TYPE:-}" in
    alpha|beta|release) ;;
    *) die "RELEASE_TYPE must be alpha, beta, or release" ;;
  esac

  game=$(awk -F= '$1 == "minecraft_version" { print $2 }' "$here/../../gradle.properties")
  [[ "$game" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || die "Invalid minecraft_version in gradle.properties"
  display="Industrial Overkill ${version} for NeoForge ${game}"
  work=$(mktemp -d)
  trap 'rm -f -- "$work/"*.json; rmdir -- "$work"' EXIT

  game_versions=$(curseforge_versions)
  curseforge_upload "$game_versions"
  if [[ -n "${MODRINTH_PROJECT_ID:-}" ]]; then
    modrinth_upload
  else
    echo "MODRINTH_PROJECT_ID is unset. Skipping Modrinth."
  fi
  github_release
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  case "$#:${1:-}" in
    1:check-env) check_env ;;
    4:upload) upload "$2" "$3" "$4" ;;
    *) die "Usage: release-publish.sh check-env | upload <version> <notes> <jar>" ;;
  esac
fi
