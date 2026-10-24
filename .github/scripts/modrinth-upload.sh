#!/usr/bin/env bash
# Uploads the built jar to Modrinth as a new version of the project.
# The workflow runs this only when it has just made a brand-new GitHub release.
#
# Needs: MODRINTH_TOKEN (a GitHub secret). The token is only ever sent to api.modrinth.com
# in a header and is never printed.
# Dry run (no internet, no token needed, sends nothing):  DRY_RUN=1 bash .github/scripts/modrinth-upload.sh
set -euo pipefail

# ---- The things you may want to change -------------------------------------------------
SLUG="solacemod"
VERSION_TYPE="beta"          # alpha, beta or release. Change this one word when Solace leaves beta.
LOADER="neoforge"
GAME_VERSION="1.21.1"
# -----------------------------------------------------------------------------------------

API="https://api.modrinth.com/v2"
UA="remitoshniwal/solace (github actions upload)"   # Modrinth asks for a User-Agent that says who you are
DRY_RUN="${DRY_RUN:-0}"

VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
JAR=$(ls build/libs/solace-"${VERSION}".jar 2>/dev/null | head -1 || true)
[ -n "$JAR" ] || { echo "::error title=Modrinth::build/libs/solace-${VERSION}.jar not found"; exit 1; }

# Version title: "Solace 1.15.0 (beta)", or just "Solace 1.15.0" for a full release.
if [ "$VERSION_TYPE" = "release" ]; then NAME="Solace ${VERSION}"; else NAME="Solace ${VERSION} (${VERSION_TYPE})"; fi

# Changelog: .github/modrinth-changelog.md, written for Modrinth with no story spoilers.
# Its first line must be "# <version>", so an old text can never go out by mistake.
if [ "$(head -1 .github/modrinth-changelog.md)" = "# ${VERSION}" ]; then
  CHANGELOG=$(tail -n +2 .github/modrinth-changelog.md)
else
  echo "::warning title=Modrinth::.github/modrinth-changelog.md is not for ${VERSION}, using a plain line instead"
  CHANGELOG="Solace ${VERSION}."
fi

build_body() { # $1 = project id
  jq -n \
    --arg project_id "$1" --arg name "$NAME" --arg version "$VERSION" --arg changelog "$CHANGELOG" \
    --arg type "$VERSION_TYPE" --arg loader "$LOADER" --arg game "$GAME_VERSION" \
    '{project_id:$project_id, name:$name, version_number:$version, changelog:$changelog,
      dependencies:[], game_versions:[$game], loaders:[$loader], version_type:$type,
      featured:false, file_parts:["file"], primary_file:"file"}'
}

if [ "$DRY_RUN" = "1" ]; then
  echo "DRY RUN: nothing is sent. Would upload ${JAR} to project '${SLUG}' with this data:"
  build_body "PROJECT_ID_LOOKED_UP_AT_UPLOAD_TIME"
  exit 0
fi

[ -n "${MODRINTH_TOKEN:-}" ] || { echo "::notice title=Modrinth::No MODRINTH_TOKEN secret set, skipping the Modrinth upload"; exit 0; }

# Look the project up (this also proves the token works). Any answer except 200 stops here, before any upload.
code=$(curl -sS -o project.json -w '%{http_code}' -A "$UA" -H "Authorization: $MODRINTH_TOKEN" "$API/project/$SLUG") || code=000
[ "$code" = "200" ] || { echo "::error title=Modrinth::Could not look up project '$SLUG' (HTTP $code). Nothing was uploaded."; exit 1; }
PROJECT_ID=$(jq -r .id project.json)

# Already there? Then do nothing.
code=$(curl -sS -o versions.json -w '%{http_code}' -A "$UA" -H "Authorization: $MODRINTH_TOKEN" "$API/project/$SLUG/version?include_changelog=false") || code=000
[ "$code" = "200" ] || { echo "::error title=Modrinth::Could not list versions (HTTP $code). Nothing was uploaded."; exit 1; }
if jq -e --arg v "$VERSION" 'any(.[]; .version_number == $v)' versions.json > /dev/null; then
  echo "::notice title=Modrinth::Version ${VERSION} is already on Modrinth, skipping"
  exit 0
fi

build_body "$PROJECT_ID" > data.json
code=$(curl -sS -o upload.json -w '%{http_code}' -A "$UA" -H "Authorization: $MODRINTH_TOKEN" \
  -F "data=@data.json;type=application/json" -F "file=@${JAR};type=application/java-archive" \
  "$API/version") || code=000
if [ "$code" = "200" ]; then
  echo "::notice title=Modrinth::Uploaded ${NAME} as a ${VERSION_TYPE}"
else
  echo "::error title=Modrinth::Upload failed (HTTP $code): $(head -c 400 upload.json)"
  exit 1
fi
