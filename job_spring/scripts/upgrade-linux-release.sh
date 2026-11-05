#!/usr/bin/env bash
set -euo pipefail
umask 077

unit=job-tracker.service
unit_file=/etc/systemd/system/$unit
app=/opt/job-tracker
web=/opt/job-tracker-nginx-preview
site=/etc/nginx/sites-enabled/job-tracker-5175
private=/root/job-tracker-linux-private
archive=/root/job-tracker-linux-release-f5e9b01.tar.gz
archive_sha=5a5c6f4b06dfb1324c18924dd143b2d7b34ddeacda354a57bbca6e17337c9fc9
old_unit_sha=56f5c3ee89bd160efe6e7ffb8f529e3984ecf524e0ef9290254c5fa70efb53a4
vision_files=(vision-model-local.yaml vision-quota-local.yaml)
base=http://127.0.0.1:5175

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

restore() {
  local file
  systemctl stop "$unit" || return 1
  cp -a -- "$backup/job-tracker.jar" "$app/job-tracker.jar" || return 1
  cp -a -- "$backup/application-linux.yaml" "$app/config/application-linux.yaml" || return 1
  cp -a -- "$backup/$unit" "$unit_file" || return 1
  for file in "${vision_files[@]}"; do
    if [[ -f $backup/$file ]]; then
      cp -a -- "$backup/$file" "$app/config/$file" || return 1
    else
      rm -f -- "$app/config/$file" || return 1
    fi
  done
  if [[ -d $backup/frontend ]]; then
    if [[ -d $web/frontend ]]; then
      mv -T -- "$web/frontend" "$backup/frontend.failed" || return 1
    fi
    mv -T -- "$backup/frontend" "$web/frontend" || return 1
  fi
  systemctl daemon-reload || return 1
  systemctl reset-failed "$unit" || return 1
  if [[ $previous_state == active ]]; then
    systemctl start "$unit" || return 1
  fi
  printf '%s\n' rolled-back > "$backup/status" || return 1
  printf '已恢复旧文件及 Java 原运行状态。备份目录 %s\n' "$backup"
}

[[ $EUID == 0 && $(uname -s) == Linux ]] || fail '请在 Linux root SSH 终端运行。'
exec 9>/run/lock/job-tracker-release.lock
flock -n 9 || fail '另一个发布或回退正在运行。'

if [[ ${1:-} == --rollback && $# == 2 ]]; then
  backup=$(cd -- "$2" && pwd -P)
  [[ $backup == "$web"/.release-f5e9b01.* && $(stat -c '%u:%a' "$backup") == 0:700 &&
     $(cat "$backup/status") == installed ]] || fail '备份目录或状态不符合预期。'
  cmp -s "$backup/release/job-tracker.jar" "$app/job-tracker.jar" &&
    cmp -s "$backup/release/$unit" "$unit_file" || fail '当前版本已变化，暂停回退。'
  previous_state=$(cat "$backup/previous-state")
  [[ $previous_state == active || $previous_state == inactive ]] || fail '旧服务状态无效。'
  restore || fail "回退未完成，备份保存在 $backup。"
  exit 0
fi
[[ $# == 0 ]] || fail '用法：直接运行发布，或加 --rollback 和备份目录。'

[[ -f $archive && ! -L $archive ]] || fail '找不到发布包。'
printf '%s  %s\n' "$archive_sha" "$archive" | sha256sum --check --status || fail '发布包摘要不匹配。'
[[ $(stat -c '%u:%a' "$private") == 0:700 ]] || fail 'Linux 私有目录应为 root 所有、权限 700。'
for file in "${vision_files[@]}"; do
  [[ -s $private/$file && ! -L $private/$file && $(stat -c '%u:%a' "$private/$file") == 0:600 ]] ||
    fail "请核对私有文件 $file，应为 root 所有、权限 600。"
done
grep -Eq '^[[:space:]]+enabled:[[:space:]]+true[[:space:]]*$' "$private/vision-model-local.yaml" ||
  fail '私有模型配置未声明 enabled: true。'

[[ $(systemctl show -p FragmentPath --value "$unit") == "$unit_file" &&
   -z $(systemctl show -p DropInPaths --value "$unit") ]] || fail '现有 systemd 单元与部署记录不一致。'
printf '%s  %s\n' "$old_unit_sha" "$unit_file" | sha256sum --check --status || fail '现有单元内容已变化。'
previous_state=$(systemctl is-active "$unit" || true)
[[ $previous_state == active || $previous_state == inactive ]] || fail 'Java 服务状态需先核对。'
listener=$(ss -H -ltn 'sport = :8082' | awk '{print $4}')
if [[ $previous_state == active ]]; then
  [[ $listener == 127.0.0.1:8082 ]] || fail '当前 Java 监听地址与部署记录不一致。'
else
  [[ -z $listener ]] || fail '8082 已有其他监听。'
fi
for dependency in mysql.service redis-server.service nginx.service; do
  systemctl is-active --quiet "$dependency" || fail "$dependency 未运行。"
done
[[ -d $web/frontend && ! -L $web/frontend && -d $app/config && ! -L $app/config ]] || fail '运行目录与部署记录不一致。'
for file in "$unit_file" "$app/job-tracker.jar" "$app/config/application-linux.yaml" "$app/config/credentials-linux-local.yaml"; do
  [[ -f $file && ! -L $file ]] || fail "运行文件 $file 不是普通文件。"
done
for file in "${vision_files[@]}"; do
  [[ ! -L $app/config/$file && ( ! -e $app/config/$file || -f $app/config/$file ) ]] || fail "目标 $file 不是普通文件。"
done

nginx_config=$(nginx -T 2>/dev/null) || fail 'Nginx 当前配置校验失败。'
grep -Fxq "# configuration file $site:" <<< "$nginx_config" &&
  grep -Eq '^[[:space:]]*listen 0\.0\.0\.0:5175;' "$site" &&
  grep -Eq "^[[:space:]]*root $web/frontend;" "$site" &&
  grep -Eq "^[[:space:]]*include $web/job-tracker.locations.conf;" "$site" &&
  grep -Eq '^[[:space:]]*proxy_pass http://127\.0\.0\.1:8082;' "$web/job-tracker.locations.conf" &&
  grep -Eq '^[[:space:]]*proxy_set_header X-Forwarded-For \$remote_addr;' "$web/job-tracker.locations.conf" ||
  fail '5175 站点或客户端地址转发规则与部署记录不一致。'
if grep -Eq '^[[:space:]]*(real_ip_header|real_ip_recursive|set_real_ip_from)[[:space:]]' <<< "$nginx_config"; then
  fail '发现 Nginx 真实 IP 重写规则，请先核对代理边界。'
fi
unset nginx_config

backup=$(mktemp -d "$web/.release-f5e9b01.XXXXXXXX")
stage=$backup/release
mkdir -- "$stage"
(umask 022; tar --no-same-owner --no-same-permissions -xzf "$archive" -C "$stage")
(cd "$stage" && sha256sum --check --strict --status SHA256SUMS) || fail '包内文件摘要不匹配。'
systemd-analyze verify --recursive-errors=no "$stage/$unit"
cp -a -- "$app/job-tracker.jar" "$app/config/application-linux.yaml" "$unit_file" "$backup/"
for file in "${vision_files[@]}"; do
  if [[ -f $app/config/$file ]]; then cp -a -- "$app/config/$file" "$backup/"; fi
done
printf '%s\n' "$previous_state" > "$backup/previous-state"
printf '原文件备份目录 %s\n' "$backup"

on_exit() {
  local result=$?
  trap - EXIT
  if (( result != 0 )); then
    printf '%s\n' '新版验证未通过，正在恢复旧版本。' >&2
    restore || printf '恢复未完成，备份保存在 %s\n' "$backup" >&2
  fi
  exit "$result"
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

systemctl stop "$unit"
install -o root -g job-tracker -m 0640 "$stage/job-tracker.jar" "$app/job-tracker.jar"
install -o root -g job-tracker -m 0640 "$stage/config/application-linux.yaml" "$app/config/application-linux.yaml"
install -o root -g job-tracker -m 0640 "${vision_files[@]/#/$private/}" "$app/config/"
install -o root -g root -m 0644 "$stage/$unit" "$unit_file"
mv -T -- "$web/frontend" "$backup/frontend"
mv -T -- "$stage/frontend" "$web/frontend"
nginx -t
systemctl daemon-reload
systemctl start "$unit"

health=
for _ in {1..35}; do
  health=$(curl --noproxy '*' -fsS --max-time 2 "$base/api/health" 2>/dev/null || true)
  [[ $health == '{"status":"UP","database":"UP"}' ]] && break
  sleep 1
done
[[ $health == '{"status":"UP","database":"UP"}' ]] || fail '经 Nginx 访问 Java 的健康检查失败。'
[[ $(ss -H -ltn 'sport = :8082' | awk '{print $4}') == 127.0.0.1:8082 ]] || fail 'Java 的监听地址不符合预期。'

index=$web/frontend/index.html
asset=$(sed -nE 's/.*src="(\/assets\/[^\"]+\.js)".*/\1/p' "$index")
style=$(sed -nE 's/.*href="(\/assets\/[^\"]+\.css)".*/\1/p' "$index")
[[ $asset == /assets/*.js && $style == /assets/*.css ]] || fail '未找到新版入口的 JS 或 CSS 路径。'
for path in / /jobs/1; do
  curl --noproxy '*' -fsS --max-time 5 "$base$path" | cmp -s - "$index" || fail "前端入口检查失败 $path。"
done
for path in "$asset" "$style" /fonts/lxgw-marker-gothic.woff; do
  curl --noproxy '*' -fsS --max-time 5 "$base$path" | cmp -s - "$web/frontend$path" || fail "新版静态文件检查失败 $path。"
done
anonymous=$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' --max-time 5 "$base/api/jobs")
[[ $anonymous == 401 ]] || fail '匿名岗位接口未返回 401。'

printf '%s\n' installed > "$backup/status"
trap - EXIT INT TERM HUP
printf 'Linux 新版已启动。首页、深链接、静态文件、健康 UP/UP、匿名 401 检查通过。\n'
printf '需要回退时运行 bash %q --rollback %q\n' "$0" "$backup"
