#!/bin/sh
set -eu

PREFIX=${PREFIX:-/usr/local}
SOURCE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST="$SOURCE_DIR/build/install/gitdash"

if [ ! -x "$DIST/bin/gitdash" ]; then
  "$SOURCE_DIR/gradlew" installDist
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
