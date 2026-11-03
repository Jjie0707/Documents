#!/usr/bin/env bash
set -euo pipefail

archive=/root/job-tracker-web-preview.tar.gz
preview=/opt/job-tracker-nginx-preview
unit=job-tracker-nginx-preview.service
archive_sha=1c775cfe433808d7ff9ce1df3ff675c871e8c43bb6f5339e226d1e967d835464

if [[ $EUID != 0 || ! -f $archive || -L $archive ]]; then
  printf '%s\n' '请在 Linux root SSH 终端运行，并先上传预览包。' >&2
  exit 1
fi
if [[ -e $preview || -L $preview ||
      $(systemctl show -p LoadState --value "$unit") != not-found ||
      -n $(ss -H -ltn 'sport = :18080') ]]; then
  printf '%s\n' '预览目录、服务名或 18080 已占用，请先核对。' >&2
  exit 1
fi
nginx_bin=$(command -v nginx)
getent passwd www-data >/dev/null
nginx -v
printf '%s  %s\n' "$archive_sha" "$archive" | sha256sum --check -
direct_health=$(curl --noproxy '*' -fsS --max-time 3 http://127.0.0.1:8082/api/health)
if [[ $direct_health != '{"status":"UP","database":"UP"}' ]]; then
  printf '%s\n' 'Java 当前健康检查未通过。' >&2
  exit 1
fi

install -d -o root -g root -m 0755 "$preview"
(
  umask 022
  tar --no-same-owner --no-same-permissions -xzf "$archive" -C "$preview"
)
install -d -o www-data -g www-data -m 0700 "$preview/run"
runuser -u www-data -- "$nginx_bin" -t -p "$preview/" -c job-tracker-preview.conf

systemd-run --unit="$unit" --uid=www-data --gid=www-data \
  --working-directory="$preview" --service-type=exec --collect \
  --property=NoNewPrivileges=yes --property=MemoryMax=64M --property=RuntimeMaxSec=30min \
  "$nginx_bin" -p "$preview/" -c job-tracker-preview.conf -g 'daemon off;'
trap 'systemctl stop "$unit"; printf "%s\n" "预览检查未完成，已停止本次预览。" >&2' EXIT

base=http://127.0.0.1:18080
index="$preview/frontend/index.html"
for _ in {1..10}; do
  if curl --noproxy '*' -fsS -o /dev/null --max-time 2 "$base/" 2>/dev/null; then
    break
  fi
  sleep 1
done
preview_pid=$(systemctl show -p MainPID --value "$unit")
listener=$(ss -H -ltnp 'sport = :18080' | awk -v marker="pid=$preview_pid," 'index($0, marker) {print $4}')
[[ $listener = 127.0.0.1:18080 ]]
curl --noproxy '*' -fsS --max-time 3 "$base/" | cmp -s - "$index"
curl --noproxy '*' -fsS --max-time 3 "$base/jobs/1" | cmp -s - "$index"
asset=$(sed -nE 's/.*src="(\/assets\/[^"]+\.js)".*/\1/p' "$index")
[[ $asset = /assets/*.js ]]
curl --noproxy '*' -fsS --max-time 3 "$base$asset" | cmp -s - "$preview/frontend$asset"
curl --noproxy '*' -fsS --max-time 3 "$base/fonts/lxgw-marker-gothic.woff" |
  cmp -s - "$preview/frontend/fonts/lxgw-marker-gothic.woff"
missing=$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' --max-time 3 "$base/assets/preview-missing.js")
anonymous=$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' --max-time 3 "$base/api/jobs")
health=$(curl --noproxy '*' -fsS --max-time 3 "$base/api/health")
[[ $missing = 404 && $anonymous = 401 && $health = "$direct_health" ]]
printf '预览监听 %s\n首页、深链接、JS 和字体内容校验通过。\n' "$listener"
printf '缺失静态文件 HTTP %s\nAPI 健康 %s\n匿名岗位接口 HTTP %s\n' "$missing" "$health" "$anonymous"
trap - EXIT
printf '%s\n' 'Linux Nginx 回环预览通过，临时服务最多运行 30 分钟。'
