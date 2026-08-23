#!/bin/zsh
set -euo pipefail

repository_root="${0:A:h:h}"
key_path="${XDG_DATA_HOME:-${HOME}/.local/share}/apk-extractor/upload-key.jks"
keychain_service="APK Extractor Upload Key"
keychain_account="${USER}"

if [[ ! -f "${key_path}" ]]; then
    print -u2 "Upload key not found. Run scripts/create-upload-key.sh first."
    exit 1
fi

key_password="$(security find-generic-password \
    -a "${keychain_account}" \
    -s "${keychain_service}" \
    -w)"

export APK_EXTRACTOR_UPLOAD_STORE_FILE="${key_path}"
export APK_EXTRACTOR_UPLOAD_STORE_PASSWORD="${key_password}"
export APK_EXTRACTOR_UPLOAD_KEY_ALIAS="upload"
export APK_EXTRACTOR_UPLOAD_KEY_PASSWORD="${key_password}"

cd "${repository_root}"
./gradlew clean :app:bundleRelease

unset key_password
unset APK_EXTRACTOR_UPLOAD_STORE_PASSWORD
unset APK_EXTRACTOR_UPLOAD_KEY_PASSWORD

print "Signed Play bundle: ${repository_root}/app/build/outputs/bundle/release/app-release.aab"
