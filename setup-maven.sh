#!/usr/bin/env bash
set -euo pipefail

# Installs Apache Maven into /opt and wires PATH in ~/.bashrc (idempotent).
# Usage:
#   MAVEN_VERSION=3.9.6 ./setup-maven.sh
# Defaults to 3.9.6 if not provided.

MAVEN_VERSION="${MAVEN_VERSION:-3.9.6}"
MAVEN_NAME="apache-maven-${MAVEN_VERSION}"
MAVEN_TGZ="${MAVEN_NAME}-bin.tar.gz"
MAVEN_SHA="${MAVEN_TGZ}.sha512"
DOWNLOAD_BASE="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries"
INSTALL_DIR="/opt/${MAVEN_NAME}"
SYMLINK_DIR="/opt/maven"

echo "==> Preparing to install Maven ${MAVEN_VERSION}"
if [[ "$(uname -s)" != "Linux" ]]; then
  echo "ERROR: This script targets Linux (e.g., WSL Ubuntu)." >&2
  exit 1
fi

echo "==> Installing prerequisites"
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -y
sudo apt-get install -y ca-certificates wget tar coreutils

if [[ -d "${INSTALL_DIR}" ]]; then
  echo "==> ${INSTALL_DIR} already exists; reusing existing installation"
else
  echo "==> Downloading Maven from Apache archives"
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT
  (cd "$tmpdir" && wget -q "${DOWNLOAD_BASE}/${MAVEN_TGZ}" "${DOWNLOAD_BASE}/${MAVEN_SHA}")

  echo "==> Verifying SHA-512"
  (cd "$tmpdir" && sha512sum -c "${MAVEN_SHA}")

  echo "==> Extracting to /opt"
  sudo tar -C /opt -xzf "$tmpdir/${MAVEN_TGZ}"
fi

echo "==> Updating symlink ${SYMLINK_DIR} -> ${INSTALL_DIR}"
sudo ln -sfn "${INSTALL_DIR}" "${SYMLINK_DIR}"

echo "==> Updating ~/.bashrc (idempotent PATH/MAVEN_HOME)"
grep -q 'export MAVEN_HOME=' "$HOME/.bashrc" 2>/dev/null || {
  {
    echo "export MAVEN_HOME=${SYMLINK_DIR}"
    echo 'export PATH="$MAVEN_HOME/bin:$PATH"'
  } >> "$HOME/.bashrc"
}

echo "==> Verifying current shell"
export MAVEN_HOME="${SYMLINK_DIR}"
export PATH="$MAVEN_HOME/bin:$PATH"
mvn -v

echo "==> Done. Open a new shell or run: source ~/.bashrc"


