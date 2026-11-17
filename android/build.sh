#!/usr/bin/env bash
# 大肥鱼·独立版 打包脚本
# 不依赖网桥、不依赖电脑，手机上直接跑
set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
AND="$ROOT"
WORK="$AND/build"
PARENT="$(cd "$ROOT/../../.." && pwd)"    # 回到 workspace 根（android→standalone→_lab→根）
XINJIA="$PARENT/xinjia"

BT="$XINJIA/build/sdk/build-tools/34.0.0"
JAVA="$XINJIA/build/jdk-17.0.21+9/bin"
APKJAR="$XINJIA/build/sdk/platforms/android-34/android.jar"
# 签名配置：优先读环境变量，没有就用本地默认值
KEYSTORE="${KEYSTORE:-$XINJIA/android/xiaoman.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-changeme}"
KEY_ALIAS="${KEY_ALIAS:-myapp}"
KEY_PASS="${KEY_PASS:-$KEYSTORE_PASS}"

VERSION_NAME="7.3"
VERSION_CODE="73"

# Python：只用来塞 dex，必须真能跑（Windows 商店占位程序会被排除）
python_ok() {
    [ -n "${1:-}" ] || return 1
    command -v "$1" >/dev/null 2>&1 || [ -x "$1" ] || return 1
    "$1" -c "import sys,zipfile; assert sys.version_info[0]==3" >/dev/null 2>&1
}
PY=""
for c in "${PYTHON:-}" "$PARENT/py310/python.exe" python3 python; do
    if python_ok "$c"; then PY="$c"; break; fi
done
if [ -z "$PY" ]; then echo "找不到能用的 Python 3"; exit 1; fi
echo "用的 Python: $PY"

AAPT2="$BT/aapt2.exe"
ZIPALIGN="$BT/zipalign.exe"
APKSIGNER="$BT/apksigner.bat"
JAVAC="$JAVA/javac.exe"
export JAVA_HOME="$(cygpath -w "$JAVA/..")"

echo "=== 清理 ==="
rm -rf "$WORK"
mkdir -p "$WORK/res" "$WORK/gen" "$WORK/classes" "$WORK/dex" "$WORK/apk"

echo "=== 1. aapt2 compile ==="
"$AAPT2" compile --dir "$(cygpath -w "$AND/res")" -o "$(cygpath -w "$WORK/res.zip")" || exit 1
echo "  res.zip $(stat -c%s "$WORK/res.zip") 字节"

echo "=== 2. aapt2 link ==="
"$AAPT2" link --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  -I "$(cygpath -w "$APKJAR")" \
  --manifest "$(cygpath -w "$AND/AndroidManifest.xml")" \
  --java "$(cygpath -w "$WORK/gen")" \
  -A "$(cygpath -w "$AND/assets")" \
  -o "$(cygpath -w "$WORK/apk/base.apk")" \
  "$(cygpath -w "$WORK/res.zip")" || exit 1
echo "  base.apk $(stat -c%s "$WORK/apk/base.apk") 字节"

echo "=== 3. javac ==="
SRCS=$(find "$AND/src" "$WORK/gen" -name "*.java" 2>/dev/null | tr '\n' ' ')
"$JAVAC" -encoding UTF-8 --release 8 -classpath "$(cygpath -w "$APKJAR")" \
  -d "$(cygpath -w "$WORK/classes")" $SRCS > "$WORK/javac.log" 2>&1
CNT=$(find "$WORK/classes" -name "*.class" | wc -l)
echo "  class: $CNT 个"
if [ "$CNT" -lt 3 ]; then echo "  ✗ 编译失败"; head -40 "$WORK/javac.log"; exit 1; fi

echo "=== 4. d8 ==="
BT34="$(cygpath -w "$BT")"
mapfile -t CLS < <(find "$WORK/classes" -name "*.class")
"$JAVA/java.exe" -cp "$BT34/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$(cygpath -w "$APKJAR")" --release --min-api 24 \
  --output "$(cygpath -w "$WORK/dex")" \
  "${CLS[@]}" > "$WORK/d8.log" 2>&1
if [ ! -f "$WORK/dex/classes.dex" ]; then
  echo "  d8 输出没生成，看日志："; head -20 "$WORK/d8.log"; exit 1
fi
echo "  classes.dex $(stat -c%s "$WORK/dex/classes.dex") 字节"

echo "=== 5. 塞 dex ==="
"$PY" - "$(cygpath -w "$WORK/apk/base.apk")" "$(cygpath -w "$WORK/dex/classes.dex")" "$(cygpath -w "$WORK/apk/unsigned.apk")" << 'PYEOF'
import sys, zipfile, shutil
base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
shutil.copy(base, out)
with zipfile.ZipFile(out, 'a', zipfile.ZIP_STORED) as z:
    z.write(dex, 'classes.dex')
print("  写入完成")
PYEOF
if [ ! -f "$WORK/apk/unsigned.apk" ]; then echo "  ✗ 塞 dex 失败"; exit 1; fi
echo "  $(stat -c%s "$WORK/apk/unsigned.apk") 字节"

echo "=== 6. zipalign ==="
"$ZIPALIGN" -f -p 4 "$(cygpath -w "$WORK/apk/unsigned.apk")" "$(cygpath -w "$WORK/apk/aligned.apk")" || exit 1
echo "  $(stat -c%s "$WORK/apk/aligned.apk") 字节"

echo "=== 7. 签名 ==="
"$APKSIGNER" sign --ks "$(cygpath -w "$KEYSTORE")" --ks-pass "pass:$KEYSTORE_PASS" \
  --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" \
  --out "$(cygpath -w "$WORK/大肥鱼-独立版-v$VERSION_NAME.apk")" \
  "$(cygpath -w "$WORK/apk/aligned.apk")" || exit 1

echo ""
echo "  ✓ APK: $WORK/大肥鱼-独立版-v$VERSION_NAME.apk"
echo "  大小: $(( $(stat -c%s "$WORK/大肥鱼-独立版-v$VERSION_NAME.apk") / 1024 )) KB"
echo "  版本: v$VERSION_NAME ($VERSION_CODE)"
