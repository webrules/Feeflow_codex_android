#!/bin/bash
# Debug script: test 4D4Y session independently from the app
# Usage: ./debug_4d4y_session.sh

BASE="https://www.4d4y.com/forum"
COOKIE_JAR=$(mktemp /tmp/4d4y_cookies.XXXXXX)
UA="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

echo "=== Step 1: Fetch index page (anonymous) ==="
curl -s -o /tmp/4d4y_index_anon.html -w "HTTP %{http_code}, Size: %{size_download}" \
  -c "$COOKIE_JAR" \
  -H "User-Agent: $UA" \
  "$BASE/index.php"
echo ""

echo ""
echo "=== Step 2: Extract visible forums (anonymous) ==="
grep -oE 'forumdisplay\.php\?fid=[0-9]+[^">]*">[^<]+' /tmp/4d4y_index_anon.html | sed 's/.*">//' | sort -u
echo ""
echo "=== Step 3: Enter your 4D4Y credentials ==="
read -p "Username: " USERNAME
read -s -p "Password: " PASSWORD
echo ""

echo ""
echo "=== Step 4: Login ==="
LOGIN_RESP=$(curl -s -o /tmp/4d4y_login_resp.html -w "HTTP %{http_code}" \
  -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H "User-Agent: $UA" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$PASSWORD" \
  --data-urlencode "loginsubmit=yes" \
  --data-urlencode "inajax=1" \
  --data-urlencode "cookietime=2592000" \
  "$BASE/logging.php?action=login&loginsubmit=yes&inajax=1")
echo "$LOGIN_RESP"
echo "Login response:"
cat /tmp/4d4y_login_resp.html
echo ""

echo ""
echo "=== Step 5: Logged-in cookies ==="
echo "Cookie jar contents:"
cat "$COOKIE_JAR" | grep -v '^#' | grep -v '^$'
echo ""

echo ""
echo "=== Step 6: Fetch index page (with auth cookies) ==="
curl -s -o /tmp/4d4y_index_auth.html -w "HTTP %{http_code}, Size: %{size_download}" \
  -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H "User-Agent: $UA" \
  "$BASE/index.php"
echo ""

echo ""
echo "=== Step 7: Extract visible forums (authenticated) ==="
echo "--- Forum links ---"
grep -oE 'forumdisplay\.php\?fid=[0-9]+[^">]*">[^<]+' /tmp/4d4y_index_auth.html | sed 's/.*">//' | sort -u
echo ""

echo "--- Login state indicators ---"
grep -oiE '(logout|welcome|member|guest|logginfo|会员|游客|退出|登录|注册)' /tmp/4d4y_index_auth.html | sort -u
echo ""

# Check for SID
echo "--- SID in page ---"
grep -oE 'sid=[a-zA-Z0-9]+' /tmp/4d4y_index_auth.html | head -3
echo ""

echo "=== Step 8: Simulate what happens after 30min idle (SID expiration) ==="
echo "Deleting only SID cookie, keeping auth cookies..."
sed -i '' '/cdb_sid/d' "$COOKIE_JAR"
echo "Remaining cookies:"
cat "$COOKIE_JAR" | grep -v '^#' | grep -v '^$'
echo ""

echo "=== Step 9: Fetch index with auth cookies but NO SID ==="
curl -s -o /tmp/4d4y_index_no_sid.html -w "HTTP %{http_code}, Size: %{size_download}" \
  -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H "User-Agent: $UA" \
  "$BASE/index.php"
echo ""

echo "--- Forum links (auth cookies, no SID) ---"
grep -oE 'forumdisplay\.php\?fid=[0-9]+[^">]*">[^<]+' /tmp/4d4y_index_no_sid.html | sed 's/.*">//' | sort -u
echo ""

echo "--- Login state indicators ---"
grep -oiE '(logout|welcome|member|guest|logginfo|会员|游客|退出|登录|注册)' /tmp/4d4y_index_no_sid.html | sort -u
echo ""

rm -f "$COOKIE_JAR"
echo "=== Done: compare Step 7 vs Step 9 forum lists ==="
echo "If Step 9 shows fewer forums (missing 'Discovery'), the SID expiration is the root cause."
