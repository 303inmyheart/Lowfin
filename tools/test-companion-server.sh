#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST="$ROOT/.tools/companion-test"
VERSION="$(sed -n 's/.*<Version>\([^<]*\)<\/Version>.*/\1/p' "$ROOT/companion/src/Lowfin.Companion/Lowfin.Companion.csproj")"
PLUGIN="Lowfin.Companion_${VERSION}.0"
case "${1:-status}" in
  start)
    "$ROOT/.tools/dotnet10/dotnet" build "$ROOT/companion/src/Lowfin.Companion/Lowfin.Companion.csproj" -c Release --no-restore
    mkdir -p "$TEST/data/plugins/$PLUGIN" "$TEST/config" "$TEST/cache" "$TEST/logs" "$TEST/media" "$TEST/plugin-backups"
    for previous in "$TEST"/data/plugins/Lowfin.Companion_*; do
      if [[ -d "$previous" && "${previous##*/}" != "$PLUGIN" ]]; then mv "$previous" "$TEST/plugin-backups/${previous##*/}-$(date +%s)"; fi
    done
    cp "$ROOT/companion/src/Lowfin.Companion/bin/Release/net10.0/Lowfin.Companion.dll" "$TEST/data/plugins/$PLUGIN/Lowfin.Companion.dll"
    if [[ -f "$ROOT/companion/artifacts/$PLUGIN/meta.json" ]]; then
      cp "$ROOT/companion/artifacts/$PLUGIN/meta.json" "$ROOT/companion/assets/lowfin-companion.png" "$TEST/data/plugins/$PLUGIN/"
    fi
    if [[ ! -f "$TEST/media/Lowfin Sample (2026).mp4" ]]; then
      ffmpeg -hide_banner -loglevel error -f lavfi -i color=c=black:s=16x16:r=1/60 -t 43200 -c:v libx264 -preset ultrafast -an "$TEST/media/Lowfin Sample (2026).mp4"
    fi
    if [[ ! -f "$TEST/config/network.xml" ]]; then
      cat > "$TEST/config/network.xml" <<'XML'
<NetworkConfiguration><InternalHttpPort>18096</InternalHttpPort><InternalHttpsPort>18920</InternalHttpsPort><EnableHttps>false</EnableHttps><EnableIPv6>false</EnableIPv6><EnableRemoteAccess>false</EnableRemoteAccess><LocalNetworkAddresses><string>127.0.0.1</string></LocalNetworkAddresses></NetworkConfiguration>
XML
    fi
    echo $$ > "$TEST/server.pid"
    exec "$TEST/runtime/usr/lib/jellyfin/bin/jellyfin" --datadir "$TEST/data" --configdir "$TEST/config" --cachedir "$TEST/cache" --logdir "$TEST/logs" --nowebclient --ffmpeg /usr/bin/ffmpeg
    ;;
  status) tail -n 35 "$TEST"/logs/log_*.log ;;
  stop)
    pid="$(cat "$TEST/server.pid")"
    if [[ -r "/proc/$pid/cmdline" ]] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q 'companion-test'; then kill "$pid"; fi
    ;;
  *) exit 2 ;;
esac
