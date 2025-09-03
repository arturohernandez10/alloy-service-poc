#!/usr/bin/env bash
set -euo pipefail

echo "==> Installing OpenJDK (17 preferred, 21 fallback)"
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -y
if ! sudo apt-get install -y openjdk-17-jdk; then
  echo "openjdk-17-jdk not available; trying openjdk-21-jdk"
  sudo apt-get install -y openjdk-21-jdk
fi

echo "==> Resolving JAVA_HOME (no mlocate used)"
# Prefer known Debian/Ubuntu JDK install paths if present (fast)
if [[ -d /usr/lib/jvm/java-17-openjdk-amd64 ]]; then
  JAVA_HOME_DIR="/usr/lib/jvm/java-17-openjdk-amd64"
elif [[ -d /usr/lib/jvm/java-21-openjdk-amd64 ]]; then
  JAVA_HOME_DIR="/usr/lib/jvm/java-21-openjdk-amd64"
else
  # Fallback: derive from javac location via readlink (fast, no scanning)
  if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac not found on PATH after install" >&2
    exit 1
  fi
  JAVAC_BIN="$(command -v javac)"
  JAVA_HOME_DIR="$(dirname "$(dirname "$(readlink -f "$JAVAC_BIN")")")"
fi

echo "JAVA_HOME resolved to: $JAVA_HOME_DIR"

echo "==> Updating ~/.bashrc (idempotent)"
# Remove any existing JAVA_HOME/Path lines we might have added previously
sed -i '/^export JAVA_HOME=.*\/usr\/lib\/jvm\//d' "$HOME/.bashrc" || true
sed -i '/^export PATH="\$JAVA_HOME\/bin:.*"/d' "$HOME/.bashrc" || true
# Append fresh lines
{
  echo "export JAVA_HOME=$JAVA_HOME_DIR"
  echo 'export PATH="$JAVA_HOME/bin:$PATH"'
} >> "$HOME/.bashrc"

echo "==> Verifying current shell"
export JAVA_HOME="$JAVA_HOME_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version

echo "==> Done. Open a new shell or run: source ~/.bashrc"