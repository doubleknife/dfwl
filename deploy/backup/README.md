# 线上数据库每日备份

每天北京时间 00:00 备份本机 MySQL `fleet_ops`，压缩存入
`/opt/whdfwl/backup/database/`，文件名带日期、时间和进程号，仅 root 可写，ubuntu 可读取、下载。
保留最近30天；只在新备份成功后清理本脚本生成的过期文件。
不清理此前的上线备份，不停止业务服务，不修改业务数据。

沿用服务器已验证可用的 `sudo mysql` / `sudo mysqldump` 本地 root socket
认证，不在脚本内存储数据库密码，不使用应用数据库账号。
要求 Ubuntu 的 MySQL socket 位于 `/var/run/mysqld/mysqld.sock`。

## 安装

将本目录的 `.sh`、`.service`、`.timer` 三个文件上传到 `/home/ubuntu/whdfwl-upload/`，执行：

```bash
cd /home/ubuntu/whdfwl-upload
sudo apt-get install -y acl
sudo install -d -m 755 /opt/whdfwl/scripts
sudo install -d -m 700 /opt/whdfwl/backup/database
sudo install -m 700 backup-database.sh /opt/whdfwl/scripts/backup-database.sh
sudo install -m 644 whdfwl-db-backup.service /etc/systemd/system/
sudo install -m 644 whdfwl-db-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl start whdfwl-db-backup.service && sudo systemctl enable --now whdfwl-db-backup.timer
```

首次手动备份成功才启用定时器。`Persistent=true` 表示错过凌晨执行时间时，
开机后补执行一次。重复安装会更新同名定时器，不新增第二个任务。

## 检查

```bash
sudo systemctl list-timers --all whdfwl-db-backup.timer
sudo journalctl -u whdfwl-db-backup.service -n 30 --no-pager
sudo ls -lh /opt/whdfwl/backup/database/
```

终端中的下次执行时间按服务器时区显示，计划本身固定为北京时间。
手动再次备份：`sudo systemctl start whdfwl-db-backup.service`。
停用自动备份：`sudo systemctl disable --now whdfwl-db-backup.timer`。

备份失败会以非零状态退出并记录 journal；没有配置短信、邮件通知。
并发备份通过 flock 排他锁避免重复执行。失败的临时文件会删除，不覆盖历史备份。
备份期间允许正常 InnoDB 业务写入，但不要同时执行 DDL 或数据库升级。

## 恢复与保存范围

备份包括表结构、数据、触发器、存储过程与数据库事件。
`gzip -t` 检查压缩完整性，不能代替实际恢复演练。
先在隔离 MySQL 实例恢复并核对；SQL 含 `CREATE DATABASE`/`USE fleet_ops`，
不要直接导入正在使用的线上数据库，否则可能覆盖正式数据。

本脚本不备份附件文件、外置配置或证书；同服务器备份不能防止整机损坏，
建议另行将备份复制到独立存储。
