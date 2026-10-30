#!/usr/bin/env bash
# One-time: create the permanent release keystore and the gitignored keystore.properties.
set -euo pipefail

usage() {
  cat >&2 <<U
usage: $0 [--dry-run] [--keystore FILE] [--cred FILE] [--props FILE] [--alias NAME] [--dname DN]
defaults: --keystore ~/keystores/pmca-timelapse-release.jks
          --cred     ~/keystores/pmca-timelapse-release.cred
          --props    <repo>/keystore.properties
          --alias    pmca-timelapse
          --dname    CN=TimeLapse+
U
  exit 2
}

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ks="$HOME/keystores/pmca-timelapse-release.jks"
cred="$HOME/keystores/pmca-timelapse-release.cred"
props="$root/keystore.properties"
alias_name="pmca-timelapse"
dname="CN=TimeLapse+"
dry=0
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) dry=1 ;;
    --keystore) [ $# -ge 2 ] || usage; ks="$2"; shift ;;
    --cred) [ $# -ge 2 ] || usage; cred="$2"; shift ;;
    --props) [ $# -ge 2 ] || usage; props="$2"; shift ;;
    --alias) [ $# -ge 2 ] || usage; alias_name="$2"; shift ;;
    --dname) [ $# -ge 2 ] || usage; dname="$2"; shift ;;
    *) usage ;;
  esac
  shift
done

keytool=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then keytool="$JAVA_HOME/bin/keytool"
elif command -v keytool >/dev/null 2>&1; then keytool="$(command -v keytool)"
fi
if [ -z "$keytool" ]; then
  if [ "$dry" -eq 1 ]; then echo "[dry-run] would abort: keytool not found (set JAVA_HOME or add it to PATH)"; keytool="keytool"
  else echo "error: keytool not found (set JAVA_HOME or add it to PATH)" >&2; exit 1; fi
fi

if [ -e "$ks" ]; then
  echo "error: $ks already exists; refusing to overwrite the release keystore" >&2
  exit 1
fi
if [ -e "$cred" ]; then
  echo "error: $cred already exists; refusing to overwrite" >&2
  exit 1
fi

echo "keytool:  $keytool"
echo "keystore: $ks"
echo "cred:     $cred (mode 600)"
echo "props:    $props"
echo "alias:    $alias_name  dname: $dname  (RSA 2048, 30 years)"

if [ "$dry" -eq 1 ]; then
  echo "[dry-run] mkdir -p $(dirname "$ks") $(dirname "$cred") (mode 700)"
  echo "[dry-run] generate random password, write it to $cred"
  echo "[dry-run] keytool -genkeypair -keystore $ks -alias $alias_name -keyalg RSA -keysize 2048 -validity 10950 -dname '$dname' (password via stdin)"
  echo "[dry-run] write $props"
  echo "dry run complete; nothing changed"
  exit 0
fi

umask 077
mkdir -p "$(dirname "$ks")" "$(dirname "$cred")"
pw="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)"
[ "${#pw}" -eq 32 ] || { echo "error: could not generate a password" >&2; exit 1; }
printf '%s\n' "$pw" > "$cred"
chmod 600 "$cred"

# keytool reads the password from stdin when there is no console; it asks for it twice.
if ! printf '%s\n%s\n' "$pw" "$pw" | "$keytool" -genkeypair -keystore "$ks" -alias "$alias_name" \
     -keyalg RSA -keysize 2048 -validity 10950 -dname "$dname" >/dev/null 2>&1; then
  rm -f "$ks" "$cred"
  echo "error: keytool failed; removed partial files" >&2
  exit 1
fi
chmod 600 "$ks"

{
  echo "storeFile=$ks"
  echo "storePassword=$pw"
  echo "keyAlias=$alias_name"
  echo "keyPassword=$pw"
} > "$props"
chmod 600 "$props"
unset pw

cat <<MSG

Created $ks
Wrote   $cred and $props (not printed; both mode 600, props is gitignored)

############################################################
#  BACK UP NOW, OFFLINE:                                   #
#    $ks
#    $cred
#  This key is PERMANENT. If it is lost, every user must    #
#  uninstall TimeLapse+ before they can update. Never put   #
#  it in the repo, CI secrets or a public place.            #
############################################################
MSG
