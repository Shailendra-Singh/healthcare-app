#!/bin/sh
# Points the realm's api-gateway and api-gateway-swagger clients at the current FRONTEND_PORT and GATEWAY_PORT
# (their login and logout return addresses). The realm file sets these when the realm is first created; run this
# after changing either port later. From the repository root:
#
#   podman compose up -d --force-recreate keycloak    # restart Keycloak with the new .env values
#   podman compose exec -T keycloak sh < services/keycloak/sync-client-urls.sh
#
# (docker compose works the same.) It runs inside the Keycloak container and takes the ports and the admin login
# from the container's environment, so no secrets are typed or printed.
set -eu

K=/opt/keycloak/bin/kcadm.sh
CONFIG=/tmp/kcadm-sync.config
FRONTEND="http://localhost:${FRONTEND_PORT:-3000}"
GATEWAY="http://localhost:${GATEWAY_PORT:-8080}"

$K config credentials --config "$CONFIG" --server http://localhost:8080 --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null

client_id() {
    $K get clients --config "$CONFIG" -r healthcare -q clientId="$1" --fields id --format csv --noquotes
}

GW=$(client_id api-gateway)
SW=$(client_id api-gateway-swagger)

$K update "clients/$GW" --config "$CONFIG" -r healthcare \
    -s "redirectUris=[\"$FRONTEND/*\",\"$GATEWAY/*\"]" \
    -s "webOrigins=[\"$FRONTEND\",\"$GATEWAY\"]" \
    -s "attributes.\"post.logout.redirect.uris\"=$FRONTEND/*##$GATEWAY/*"
$K update "clients/$SW" --config "$CONFIG" -r healthcare \
    -s "redirectUris=[\"$FRONTEND/q/swagger-ui/*\",\"$GATEWAY/q/swagger-ui/*\"]" \
    -s "webOrigins=[\"$FRONTEND\",\"$GATEWAY\"]"

rm -f "$CONFIG"
echo "api-gateway and api-gateway-swagger now return to $FRONTEND and $GATEWAY"
