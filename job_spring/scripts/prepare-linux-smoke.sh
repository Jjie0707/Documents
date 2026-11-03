#!/usr/bin/env bash
# Run from the unpacked Linux delivery directory as root.
set -euo pipefail
set +x

app_dir=/opt/job-tracker
app_user=job-tracker
app_unit=job-tracker-smoke.service
private_yaml=/root/job-tracker-linux-private/credentials-linux-local.yaml
java_bin="/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)/bin/java"

if [[ $EUID != 0 || ! -x $java_bin || ! -s $private_yaml || -L $private_yaml ]]; then
  printf '%s\n' '需要 Linux root、已安装的 Java 17 和服务器私有 YAML。' >&2
  exit 1
fi
if [[ -e $app_dir || -L $app_dir ]] ||
   getent passwd "$app_user" >/dev/null ||
   getent group "$app_user" >/dev/null ||
   [[ $(systemctl show -p LoadState --value "$app_unit") != not-found ]] ||
   [[ -n $(ss -H -ltn 'sport = :8082') ]]; then
  printf '%s\n' '运行目录、系统账号、测试服务名或 8082 已占用，请先核对。' >&2
  exit 1
fi
available_kib=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
if (( available_kib < 460800 )); then
  printf '当前可用内存 %s KiB，需要至少 450 MiB 才进行本次启动验证。\n' "$available_kib" >&2
  exit 1
fi
sha256sum --check SHA256SUMS
"$java_bin" -version

useradd --system --user-group --no-create-home --home-dir "$app_dir" --shell /usr/sbin/nologin "$app_user"
install -d -o root -g "$app_user" -m 0750 "$app_dir" "$app_dir/config"
install -o root -g "$app_user" -m 0640 job-tracker.jar "$app_dir/job-tracker.jar"
install -o root -g "$app_user" -m 0640 config/application-linux.yaml "$app_dir/config/application-linux.yaml"
install -o root -g "$app_user" -m 0640 "$private_yaml" "$app_dir/config/credentials-linux-local.yaml"

probe_dir=$(mktemp -d /var/tmp/job-tracker-probe.XXXXXX)
cleanup() {
  if [[ ${started_unit:-0} = 1 ]]; then
    systemctl stop "$app_unit" || printf '%s\n' '请检查测试服务的停止状态。' >&2
  fi
  rm -rf -- "$probe_dir"
}
trap cleanup EXIT
chgrp "$app_user" "$probe_dir"
chmod 0750 "$probe_dir"
install -m 0644 LinuxDeploymentProbe.class "$probe_dir/LinuxDeploymentProbe.class"
python3 - "$app_dir/job-tracker.jar" "$probe_dir" <<'PY'
import os
import sys
from pathlib import Path
from zipfile import ZipFile

# These are non-secret build artifacts. The outer directory limits access
# to root and the service group even if the caller uses a restrictive umask.
os.umask(0o022)
with ZipFile(sys.argv[1]) as jar:
    destination = Path(sys.argv[2])
    for name in jar.namelist():
        if name.startswith("BOOT-INF/lib/") and name.endswith(".jar"):
            jar.extract(name, destination)
    (destination / "application.yaml").write_bytes(jar.read("BOOT-INF/classes/application.yaml"))
PY

cd "$app_dir"
runuser -u "$app_user" -- /usr/bin/env -i PATH=/usr/bin:/bin LANG=C.UTF-8 \
  "$java_bin" -Xmx128m -cp "$probe_dir:$probe_dir/BOOT-INF/lib/*" LinuxDeploymentProbe

systemd-run --unit="$app_unit" --uid="$app_user" --gid="$app_user" \
  --working-directory="$app_dir" --service-type=exec --collect \
  --property=RuntimeMaxSec=120s --property=MemoryMax=448M \
  --property=NoNewPrivileges=yes \
  /usr/bin/env -i PATH=/usr/bin:/bin LANG=C.UTF-8 \
  "$java_bin" -Xms64m -Xmx192m -XX:ActiveProcessorCount=2 -Djava.net.preferIPv4Stack=true \
  -jar "$app_dir/job-tracker.jar" \
  --server.port=0 --vision.model.enabled=false
started_unit=1

health=
smoke_address=
for _ in {1..35}; do
  if ! systemctl is-active --quiet "$app_unit"; then
    break
  fi
  smoke_pid=$(systemctl show -p MainPID --value "$app_unit")
  smoke_address=$(ss -H -ltnp | awk -v marker="pid=$smoke_pid," '
    index($0, marker) && $4 ~ /^127\.0\.0\.1:[0-9]+$/ {print $4}
  ')
  if [[ $smoke_address =~ ^127\.0\.0\.1:[0-9]+$ ]]; then
    health=$(curl --noproxy '*' -fsS --max-time 2 "http://$smoke_address/api/health" 2>/dev/null || true)
    if [[ $health = '{"status":"UP","database":"UP"}' ]]; then
      break
    fi
  fi
  sleep 1
done
if [[ $health != '{"status":"UP","database":"UP"}' ]]; then
  printf '%s\n' 'Java 健康检查未通过，请先核对测试服务状态。' >&2
  exit 1
fi
anonymous_status=$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' --max-time 2 "http://$smoke_address/api/jobs")
[[ $anonymous_status = 401 ]]
printf '临时 Java 监听 %s\n' "$smoke_address"
printf 'Java 健康检查 %s\n' "$health"
printf '匿名岗位接口 HTTP %s\n' "$anonymous_status"
systemctl stop "$app_unit"
started_unit=0
printf '%s\n' 'Linux Java 启动验证通过，临时服务已停止。'
