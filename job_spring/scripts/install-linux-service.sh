#!/usr/bin/env bash
set -euo pipefail

unit=job-tracker.service
source_unit=/root/job-tracker.service
target_unit=/etc/systemd/system/job-tracker.service
app_dir=/opt/job-tracker

if [[ $EUID != 0 || ! -f $source_unit || -L $source_unit ]]; then
  printf '%s\n' '需要在 Linux 的 root SSH 终端运行，且 /root/job-tracker.service 应已上传。' >&2
  exit 1
fi
if [[ -e $target_unit || -L $target_unit ||
      $(systemctl show -p LoadState --value "$unit") != not-found ||
      -n $(ss -H -ltn 'sport = :8082') ]]; then
  printf '%s\n' '服务名称或 8082 已占用，请先核对。' >&2
  exit 1
fi
if [[ ! -x /usr/lib/jvm/java-17-openjdk-amd64/bin/java ]] ||
   ! runuser -u job-tracker -- test -r "$app_dir/job-tracker.jar" ||
   ! runuser -u job-tracker -- test -r "$app_dir/config/application-linux.yaml" ||
   ! runuser -u job-tracker -- test -r "$app_dir/config/credentials-linux-local.yaml"; then
  printf '%s\n' 'Java 17、运行目录或应用账号的读权限需要核对。' >&2
  exit 1
fi
for dependency in mysql.service redis-server.service; do
  if ! systemctl is-active --quiet "$dependency" ||
     [[ $(systemctl is-enabled "$dependency") != enabled ]]; then
    printf '请先核对 %s 的运行和开机启动状态。\n' "$dependency" >&2
    exit 1
  fi
done

# Inspect the current on-disk Nginx configuration without printing it.
if ! nginx_config=$(nginx -T 2>/dev/null); then
  printf '%s\n' '现有 Nginx 配置检查失败，请先核对。' >&2
  exit 1
fi
if awk '/^[[:space:]]*#/ {next} /(^|[^0-9])8082([^0-9]|$)/ {found=1} END {exit !found}' <<< "$nginx_config" ||
   pgrep -x cloudflared >/dev/null; then
  printf '%s\n' '发现 Nginx 配置引用 8082 或本机有 Tunnel 进程，请先核对现有入口。' >&2
  exit 1
fi
unset nginx_config

available_kib=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
if (( available_kib < 460800 )); then
  printf '当前可用内存 %s KiB，需要至少 450 MiB 才启动。\n' "$available_kib" >&2
  exit 1
fi
systemd-analyze verify --recursive-errors=no "$source_unit"
install -o root -g root -m 0644 "$source_unit" "$target_unit"

rollback() {
  systemctl disable --now "$unit" >/dev/null 2>&1 ||
    printf '%s\n' '请核对新服务的停止和禁用状态。' >&2
  printf '%s\n' '常驻验证未通过，单元文件保留供核对。' >&2
}
trap rollback EXIT
systemctl daemon-reload
systemctl start "$unit"

health=
for _ in {1..35}; do
  health=$(curl --noproxy '*' -fsS --max-time 2 http://127.0.0.1:8082/api/health 2>/dev/null || true)
  [[ $health = '{"status":"UP","database":"UP"}' ]] && break
  sleep 1
done
if [[ $health != '{"status":"UP","database":"UP"}' ]]; then
  printf '%s\n' 'Java 健康检查未通过。' >&2
  exit 1
fi
java_pid=$(systemctl show -p MainPID --value "$unit")
listener=$(ss -H -ltnp 'sport = :8082' | awk -v marker="pid=$java_pid," 'index($0, marker) {print $4}')
if [[ $listener != 127.0.0.1:8082 ]]; then
  printf '%s\n' '8082 的监听进程或地址与预期不符。' >&2
  exit 1
fi
anonymous_status=$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' --max-time 2 http://127.0.0.1:8082/api/jobs)
[[ $anonymous_status = 401 ]]
systemctl enable "$unit"
systemctl is-enabled "$unit"
systemctl show "$unit" -p ActiveState -p MainPID -p MemoryCurrent -p MemoryPeak -p MemoryMax -p NRestarts
printf 'Java 监听 %s\n健康检查 %s\n匿名岗位接口 HTTP %s\n' "$listener" "$health" "$anonymous_status"
trap - EXIT
printf '%s\n' 'Linux Java 常驻服务验证通过。'
