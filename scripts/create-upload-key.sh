#!/bin/zsh
set -euo pipefail

key_directory="${XDG_DATA_HOME:-${HOME}/.local/share}/apk-extractor"
key_path="${key_directory}/upload-key.jks"
key_alias="upload"
keychain_service="APK Extractor Upload Key"
keychain_account="${USER}"

if [[ -f "${key_path}" ]]; then
    print "Upload key already exists at ${key_path}"
    exit 0
fi

mkdir -p "${key_directory}"
chmod 700 "${key_directory}"

key_password="$(openssl rand -base64 48 | tr -d '\n')"

keytool -genkeypair \
    -keystore "${key_path}" \
    -storepass "${key_password}" \
    -alias "${key_alias}" \
    -keypass "${key_password}" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=Kole Hoenicke, O=Kole Hoenicke, C=US"

chmod 600 "${key_path}"
security add-generic-password \
    -a "${keychain_account}" \
    -s "${keychain_service}" \
    -w "${key_password}" \
    -U >/dev/null

unset key_password
print "Created the APK Extractor upload key at ${key_path}"
print "Its password is stored in macOS Keychain as '${keychain_service}'."
