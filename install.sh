#!/bin/sh
set -eu

PREFIX=${PREFIX:-/usr/local}
VERSION=${GITDASH_VERSION:-0.2.0}
ACTION=${1:-install}
SOURCE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST=${GITDASH_DIST:-}
TMP_DIR=

cleanup() {
  if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR"
  fi
}
trap cleanup EXIT HUP INT TERM

DEST="$PREFIX/lib/gitdash"
BIN="$PREFIX/bin/gitdash"

run_uninstall() {
  if [ -L "$BIN" ] || [ -f "$BIN" ]; then rm -f "$BIN"; fi
  if [ -d "$DEST" ]; then rm -rf "$DEST"; fi
  echo "Uninstalled GitDash from $PREFIX"
}

if [ "$ACTION" = "uninstall" ]; then
  if [ -w "$PREFIX" ]; then run_uninstall
  elif [ "$(id -u)" -eq 0 ]; then run_uninstall
  elif command -v sudo >/dev/null 2>&1; then
    sudo sh -c 'bin="$1/bin/gitdash"; dest="$1/lib/gitdash"; [ ! -e "$bin" ] || rm -f "$bin"; [ ! -d "$dest" ] || rm -rf "$dest"' sh "$PREFIX"
    echo "Uninstalled GitDash from $PREFIX"
  else
    echo "gitdash: cannot write to $PREFIX and sudo is unavailable" >&2; exit 1
  fi
  exit 0
elif [ "$ACTION" != "install" ] && [ "$ACTION" != "update" ]; then
  echo "usage: install.sh [install|update|uninstall]" >&2; exit 2
fi

if [ -z "$DIST" ] && [ -x "$SOURCE_DIR/gradlew" ]; then
  DIST="$SOURCE_DIR/build/install/gitdash"
  if [ ! -x "$DIST/bin/gitdash" ]; then
    "$SOURCE_DIR/gradlew" installDist
  fi
elif [ -z "$DIST" ]; then
  TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/gitdash-install.XXXXXX")
  ARCHIVE="$TMP_DIR/gitdash.tar"
  CHECKSUMS="$TMP_DIR/SHA256SUMS"
  URL="https://github.com/nihitdev/gitdash/releases/download/v$VERSION/gitdash-$VERSION.tar"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$URL" -o "$ARCHIVE"
    curl -fL --retry 3 "https://github.com/nihitdev/gitdash/releases/download/v$VERSION/SHA256SUMS" -o "$CHECKSUMS"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
    wget -O "$CHECKSUMS" "https://github.com/nihitdev/gitdash/releases/download/v$VERSION/SHA256SUMS"
  else
    echo "gitdash: curl or wget is required to download a release" >&2
    exit 1
  fi
  expected=$(awk -v file="gitdash-$VERSION.tar" '$2 == file { print $1 }' "$CHECKSUMS")
  [ -n "$expected" ] || { echo "gitdash: archive checksum is missing" >&2; exit 1; }
  if command -v sha256sum >/dev/null 2>&1; then actual=$(sha256sum "$ARCHIVE" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then actual=$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')
  elif command -v openssl >/dev/null 2>&1; then actual=$(openssl dgst -sha256 "$ARCHIVE" | awk '{print $NF}')
  else echo "gitdash: sha256sum, shasum, or openssl is required" >&2; exit 1
  fi
  [ "$actual" = "$expected" ] || { echo "gitdash: archive checksum verification failed" >&2; exit 1; }
  tar -xf "$ARCHIVE" -C "$TMP_DIR"
  DIST="$TMP_DIR/gitdash-$VERSION"
fi

if [ ! -x "$DIST/bin/gitdash" ]; then
  echo "gitdash: invalid distribution: $DIST" >&2
  exit 1
fi

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
