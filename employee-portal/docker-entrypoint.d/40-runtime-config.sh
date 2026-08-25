#!/bin/sh
# Runs automatically before nginx starts (the official nginx image's
# entrypoint execs every script under /docker-entrypoint.d/). Restricting
# envsubst to just API_BASE_URL keeps it from touching any other
# ${...}-looking text that might end up in this file.
set -eu

envsubst '${API_BASE_URL}' \
  < /usr/share/nginx/html/config.js.template \
  > /usr/share/nginx/html/config.js
