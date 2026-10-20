"""Check tracked publication contents; do not print secret values."""
import json
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
entries = subprocess.check_output(["git", "ls-files", "--stage", "-z"], cwd=root).decode("utf-8").split("\0")
errors = []
files = []
for entry in entries:
    if not entry:
        continue
    metadata, name = entry.split("\t", 1)
    files.append(name)
    if metadata.startswith("160000 "):
        errors.append(f"Embedded Git repository: {name}")
    if re.search(r"(^|/)(\.local[^/]*|node_modules|target|dist|logs|ruoyi-nacos-config)(/|$)", name):
        errors.append(f"Local/generated content: {name}")
    if name.endswith((".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".log", ".dump")):
        errors.append(f"Private/runtime artifact: {name}")
    if name.startswith("deploy/config/") and name.endswith(".yml") or name == "deploy/.env":
        errors.append(f"Real deployment config: {name}")
    path = root / name
    if path.is_file() and path.stat().st_size >= 50 * 1024 * 1024:
        errors.append(f"Oversized source file: {name}")
for required in ["README.md", "LICENSE", "NOTICE.md", "Wanfo-Cloud/pom.xml", "Wanfo-Cloud-Vue/package-lock.json", "docs/QUICKSTART.md", "docs/DEPLOYMENT.md"]:
    if required not in files:
        errors.append(f"Missing publication file: {required}")
package = json.loads((root / "Wanfo-Cloud-Vue/package.json").read_text(encoding="utf-8"))
lock = json.loads((root / "Wanfo-Cloud-Vue/package-lock.json").read_text(encoding="utf-8"))
if lock["packages"][""]["dependencies"] != package["dependencies"]:
    errors.append("Dependency lock does not match package.json")
if errors:
    raise SystemExit("\n".join(errors))
print(f"Release contents passed: {len(files)} tracked files; no gitlinks, private configs or build outputs.")
