#!/bin/zsh
# Read-only verification. Passwords are entered by mysql, never stored here.
# Only for the migration window before target business writes. After cutover,
# source/target differences can be legitimate; never reimport the old source
# merely to make this comparison pass. See docs/stage6-mac-datasource.md.
unsetopt XTRACE
setopt PIPE_FAIL
umask 077
typeset -r task_verify_script_dir=${0:A:h}

task_validate_results() {
  awk -F '\t' -v expected_version="$2" '
    $1 == "identity" {
      if (NR != 1 || NF != 4 || $2 != "mysql_version" ||
          index($3, expected_version) != 1 || $4 != "job_tracker") bad = 1
      identity_count++; next
    }
    $1 == "schema" || $1 == "data" {
      valid_signature = (length($4) == 64 && $4 ~ /^[0-9a-f]+$/) ||
                        ($3 == "0" && $4 == "NULL")
      if (NF != 4 || $3 !~ /^[0-9]+$/ || !valid_signature) bad = 1
      if ($1 == "schema") schema_count++; else data_count++
      next
    }
    $1 == "integrity" {
      if (NF != 4 || $3 != "0" || $4 != "") bad = 1
      integrity_count++; next
    }
    { bad = 1 }
    END {
      exit (bad || identity_count != 1 || schema_count != 7 ||
            data_count != 4 || integrity_count != 10 || NR != 22)
    }
  ' "$1"
}

task_verify_migration() {
  local task_client=/usr/local/mysql/bin/mysql
  local task_sql_file="${task_verify_script_dir:h}/docs/sql/stage6_migration_verify.sql"
  local task_source_user task_source_port task_result_dir

  if [[ ! -x "$task_client" || ! -r "$task_sql_file" ]]; then
    print -r -- '找不到 MySQL 客户端或校验 SQL，未连接数据库。'
    return 1
  fi

  print -r -- '请保持 Mac 源库暂停写入；本脚本只读查询，不修改配置。'
  read -r 'task_source_user?Mac 源 MySQL 用户名：' || return 1
  read -r 'task_source_port?Mac 源 MySQL 端口（回车使用 3306）：' || return 1
  task_source_port=${task_source_port:-3306}
  if [[ -z "$task_source_user" || "$task_source_port" != <1-65535> ||
        "$task_source_port" == 13306 ]]; then
    print -r -- '源库用户名或端口不正确；源库端口不能使用 Linux 转发端口 13306。'
    return 1
  fi
  task_result_dir=$(mktemp -d "$HOME/job-tracker-final-check.XXXXXXXX") || return 1
  print -r -- "私有校验结果目录：$task_result_dir"

  print -r -- '第一遍：请输入 Mac 源 MySQL 的密码。'
  MYSQL_HISTFILE=/dev/null "$task_client" --no-defaults \
    --protocol=TCP --host=127.0.0.1 --port="$task_source_port" \
    --user="$task_source_user" --password --connect-timeout=5 \
    --database=job_tracker --batch --raw --skip-column-names --show-warnings \
    < "$task_sql_file" > "$task_result_dir/source.tsv" || return 1
  if ! task_validate_results "$task_result_dir/source.tsv" '9.3.'; then
    print -r -- 'Mac 源库校验未通过：可能有版本、警告、结果格式或完整性异常。停止比较。'
    return 1
  fi

  print -r -- '第二遍：请输入 Linux MySQL root 的密码。'
  MYSQL_HISTFILE=/dev/null "$task_client" --no-defaults \
    --protocol=TCP --host=127.0.0.1 --port=13306 \
    --user=root --password --connect-timeout=5 \
    --database=job_tracker --batch --raw --skip-column-names --show-warnings \
    < "$task_sql_file" > "$task_result_dir/linux.tsv" || return 1
  if ! task_validate_results "$task_result_dir/linux.tsv" '8.4.'; then
    print -r -- 'Linux 正式库校验未通过：可能有版本、警告、结果格式或完整性异常。停止比较。'
    return 1
  fi

  # Versions intentionally differ. Compare all other results, without printing hashes.
  tail -n +2 "$task_result_dir/source.tsv" > "$task_result_dir/source-checks.tsv" || return 1
  tail -n +2 "$task_result_dir/linux.tsv" > "$task_result_dir/linux-checks.tsv" || return 1
  if ! cmp -s "$task_result_dir/source-checks.tsv" "$task_result_dir/linux-checks.tsv"; then
    print -r -- '两端结构、数量或内容存在差异。请先反馈，保留结果，不重导或切换连接。'
    return 1
  fi

  print -r -- '正式库只读校验通过：结构、数量、内容摘要一致，完整性异常均为 0，未发现 SQL 警告。'
  awk -F '\t' '$1 == "data" { printf "%s：%s\n", $2, $3 }' "$task_result_dir/linux.tsv"
  print -r -- '尚未修改 YAML 或授予正式库写权限；源库继续暂停写入。'
}

task_verify_migration
