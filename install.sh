#!/bin/sh
set -eu

PREFIX=${PREFIX:-/usr/local}
VERSION=${GITDASH_VERSION:-0.1.0}
SOURCE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST=${GITDASH_DIST:-}
TMP_DIR=

cleanup() {
  if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
}
trap cleanup EXIT HUP INT TERM

if [ -z "$DIST" ] && [ -x "$SOURCE_DIR/gradlew" ]; then
  DIST="$SOURCE_DIR/build/install/gitdash"
  if [ ! -x "$DIST/bin/gitdash" ]; then
    "$SOURCE_DIR/gradlew" installDist
  fi
elif [ -z "$DIST" ]; then
  TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/gitdash-install.XXXXXX")
  ARCHIVE="$TMP_DIR/gitdash.tar"
  URL="https://github.com/nihitdev/gitdash/releases/download/v$VERSION/gitdash-$VERSION.tar"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
  else
    echo "gitdash: curl or wget is required to download a release" >&2
    exit 1
  fi
  tar -xf "$ARCHIVE" -C "$TMP_DIR"
  DIST="$TMP_DIR/gitdash-$VERSION"
fi

if [ ! -x "$DIST/bin/gitdash" ]; then
  echo "gitdash: invalid distribution: $DIST" >&2
  exit 1
fi

DEST="$PREFIX/lib/gitdash"
BIN="$PREFIX/bin/gitdash"
run_install() {
  mkdir -p "$DEST" "$PREFIX/bin"
  cp -R "$DIST/." "$DEST/"
  ln -sf "$DEST/bin/gitdash" "$BIN"
}

if [ -w "$PREFIX" ] || { [ ! -e "$PREFIX" ] && [ -w "$(dirname "$PREFIX")" ]; }; then
  run_install
elif [ "$(id -u)" -eq 0 ]; then
  run_install
elif command -v sudo >/dev/null 2>&1; then
  sudo sh -c 'mkdir -p "$1/lib/gitdash" "$1/bin" && cp -R "$2/." "$1/lib/gitdash/" && ln -sf "$1/lib/gitdash/bin/gitdash" "$1/bin/gitdash"' sh "$PREFIX" "$DIST"
else
  echo "gitdash: cannot write to $PREFIX and sudo is unavailable; set PREFIX to a writable location" >&2
  exit 1
fi
echo "Installed GitDash to $BIN"
