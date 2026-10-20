"""Initialize only an empty Compose development database. Never resets an existing system."""
import argparse
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--project-name", default="wanfo-bi-dev")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
command = ["docker", "compose", "--project-name", args.project_name, "--env-file", str(root / "deploy/.env"), "-f", str(root / "deploy/compose.infra.yml"), "exec", "-T", "mysql", "sh", "-c", 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"']
probe = subprocess.run(command, input=b"SHOW TABLES;", capture_output=True, check=True)
if probe.stdout.strip():
    raise SystemExit("Database is not empty. Initialization cancelled; no tables were changed.")
# Flyway MySQL support reads this one performance_schema table during migration.
# Grant only that table, using the container root credential without exposing it.
grant_command = command[:-2] + ["-c", 'case "$MYSQL_USER" in *[!a-zA-Z0-9_]*|"") exit 2;; esac; MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "GRANT SELECT ON performance_schema.user_variables_by_thread TO \'$MYSQL_USER\'@\'%\';"']
subprocess.run(grant_command, check=True, stdout=subprocess.DEVNULL)
for relative in ["Wanfo-Cloud/sql/ry_20260417.sql", "Wanfo-Cloud/ruoyi-modules/ruoyi-bi/deploy/ruoyi-menu.sql"]:
    subprocess.run(command, input=(root / relative).read_bytes(), check=True, stdout=subprocess.DEVNULL)
print("Initialized system tables and BI menu in the empty development database. BI Flyway migrations run when BI starts.")
