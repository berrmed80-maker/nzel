#!/usr/bin/env python3
"""Drive the Nzel app on a booted emulator over adb and record honest results."""
import subprocess, sys, time, os, re, xml.etree.ElementTree as ET

OUT = os.environ.get("NZEL_OUT", "test-artifacts")
os.makedirs(OUT, exist_ok=True)
RESULTS = []
PKG = "com.nzel.app"
YT = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
TT = "https://www.tiktok.com/@scout2015/video/6718335390845095173"
BAD = "https://example.com/definitely-not-media"
DIRECT = "https://download.samplelib.com/mp4/sample-5s.mp4"
DIRECT2 = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"

def sh(cmd, check=True, timeout=60):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        log(f"CMD FAIL ({cmd}): {r.stderr.strip()[:200]}")
    return r

def log(msg):
    print(msg, flush=True)
    RESULTS.append(msg)

def dump():
    for _ in range(2):
        sh("adb shell uiautomator dump /sdcard/nzel-ui.xml", check=False)
        r = sh("adb exec-out cat /sdcard/nzel-ui.xml", check=False)
        if r.stdout.strip().startswith("<?xml"):
            return r.stdout
        time.sleep(2)
    return ""

def texts(xml):
    if not xml: return []
    out = []
    try:
        root = ET.fromstring(xml)
        for n in root.iter("node"):
            t = n.get("text") or ""
            d = n.get("content-desc") or ""
            if t: out.append(t)
            if d: out.append(d)
    except Exception:
        pass
    return out

def find_center(xml, candidates):
    if not xml: return None
    try:
        root = ET.fromstring(xml)
        for n in root.iter("node"):
            t = (n.get("text") or "").strip()
            d = (n.get("content-desc") or "").strip()
            if t in candidates or d in candidates:
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
                if m:
                    x1,y1,x2,y2 = map(int, m.groups())
                    return ((x1+x2)//2, (y1+y2)//2)
    except Exception:
        pass
    return None

def find_field(xml):
    try:
        root = ET.fromstring(xml)
        for n in root.iter("node"):
            if "EditText" in (n.get("class") or ""):
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
                if m:
                    x1,y1,x2,y2 = map(int, m.groups())
                    return ((x1+x2)//2, (y1+y2)//2)
    except Exception:
        pass
    return None

def shot(name):
    p = os.path.join(OUT, name)
    sh(f"adb exec-out screencap -p > {p}", check=False, timeout=30)
    log(f"screenshot {name}")

def wait_for(markers_any, timeout=120, interval=4):
    """Poll UI dump until any marker text appears. Returns matched marker or None."""
    end = time.time() + timeout
    while time.time() < end:
        xml = dump()
        ts = texts(xml)
        for m in markers_any:
            if any(m in t for t in ts):
                return m, xml
        time.sleep(interval)
    return None, dump()

def tap(xy):
    sh(f"adb shell input tap {xy[0]} {xy[1]}", check=False)

def type_url(url):
    esc = url.replace("'", "").replace("&", "\\&")
    sh(f"adb shell input keyboard text '{esc}'", check=False, timeout=60)

def enter_and_fetch(url):
    xml = dump()
    f = find_field(xml)
    if not f:
        log("FAIL: url field not found"); return False
    tap(f); time.sleep(1)
    type_url(url); time.sleep(1)
    sh("adb shell input keyevent KEYCODE_BACK", check=False)  # hide keyboard
    time.sleep(1)
    xml = dump()
    c = find_center(xml, ["Fetch link", "جلب الرابط", "Récupérer"])
    if not c:
        log("FAIL: fetch button not found"); shot("fail-no-fetch.png"); return False
    tap(c)
    return True

def clear_field():
    xml = dump()
    c = find_center(xml, ["Clear", "مسح", "Effacer"])
    if c: tap(c); time.sleep(1)

def try_link(name, url, want_download):
    log(f"--- {name}: {url}")
    if not enter_and_fetch(url): return False
    marker, xml = wait_for(["Choose format", "اختر الصيغة", "Could not get this link", "تعذر جلب هذا الرابط"], timeout=150)
    shot(f"{name}-after-fetch.png")
    if marker and ("Could not" in marker or "تعذر" in marker):
        interesting = [t for t in texts(xml) if any(k in t for k in ("bot", "login", "log in", "Unsupported", "ERROR", "403", "Forbidden", "Traceback", "network", "blocked"))]
        log(f"{name}: analyze REACHED ERROR STATE. detail: " + " | ".join(interesting[:3])[:300])
        return False
    if not marker:
        log(f"{name}: TIMEOUT waiting for analyze result")
        return False
    log(f"{name}: metadata OK (format picker shown)")
    if not want_download: return True
    c = find_center(xml, ["Download"])
    # the nav bar also has 'New' label, button text is exactly 'Download'
    if not c:
        log(f"{name}: FAIL download button not found"); return False
    tap(c); time.sleep(2)
    # notification permission dialog may appear
    x2 = dump()
    allow = find_center(x2, ["Allow", "ALLOW"])
    if allow: tap(allow); time.sleep(1)
    marker, xml = wait_for(["Saved to", "تم الحفظ", "Could not get this link", "تعذر"], timeout=240, interval=5)
    shot(f"{name}-after-download.png")
    if marker and "Saved" in marker or (marker and "تم الحفظ" in marker):
        log(f"{name}: DOWNLOAD SAVED OK")
        return True
    if marker:
        log(f"{name}: download REACHED ERROR STATE")
    else:
        log(f"{name}: TIMEOUT waiting for download")
    return False

log("=== Nzel emulator test ===")
sh("adb wait-for-device", timeout=180)
sh("adb shell input keyevent KEYCODE_WAKEUP", check=False)
apk = None
for cand in ["apk/app-x86_64-release.apk", "apk/app-x86_64-debug.apk"]:
    if os.path.exists(cand): apk = cand; break
if not apk:
    log("FAIL: no x86_64 apk found in ./apk"); open(os.path.join(OUT,"results.txt"),"w").write("\n".join(RESULTS)); sys.exit(1)
sh(f"adb install -r {apk}", timeout=300)
sh(f"adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1", check=False)
log("app launched, waiting for engine init (python env extract + yt-dlp self-update attempt)")
marker, xml = wait_for(["Fetch link", "جلب الرابط", "Récupérer"], timeout=240, interval=5)
shot("01-home.png")
if not marker:
    log("FAIL: home screen never showed Fetch button"); open(os.path.join(OUT,"results.txt"),"w").write("\n".join(RESULTS)); sys.exit(1)
log("home OK: url field and fetch button visible")

ready_marker, _ = wait_for(["Preparing the download engine"], timeout=3, interval=1)
engine_wait = time.time() + 240
while time.time() < engine_wait:
    ts = texts(dump())
    if not any("Preparing the download engine" in t or "جاري تجهيز" in t for t in ts):
        break
    time.sleep(5)
ts = texts(dump())
if any("could not start" in t or "تعذر تشغيل" in t for t in ts):
    log("FAIL: engine init error shown"); shot("engine-error.png")
    open(os.path.join(OUT,"results.txt"),"w").write("\n".join(RESULTS)); sys.exit(1)
log("engine ready")
shot("02-engine-ready.png")

direct_ok = try_link("direct", DIRECT, want_download=True)
if not direct_ok:
    time.sleep(2); clear_field(); time.sleep(1)
    direct_ok = try_link("direct2", DIRECT2, want_download=True)
time.sleep(2)
clear_field(); time.sleep(1)
yt_ok = try_link("youtube", YT, want_download=True)
time.sleep(2)
clear_field(); time.sleep(1)
tt_ok = try_link("tiktok", TT, want_download=True)
time.sleep(2)
clear_field(); time.sleep(1)
bad_ok = try_link("invalid", BAD, want_download=False)
log(f"invalid link: error state shown = {not bad_ok}")

# history
xml = dump()
h = find_center(xml, ["History", "السجل", "Historique"])
if h:
    tap(h); time.sleep(2); shot("07-history.png")
    ts = texts(dump())
    log("history entries visible: " + str(any("MB" in t or "KB" in t for t in ts)))
    b = find_center(dump(), ["New", "جديد", "Nouveau"])
    if b: tap(b); time.sleep(1)

# settings + arabic
xml = dump()
s = find_center(xml, ["Settings", "الإعدادات", "Réglages"])
if s:
    tap(s); time.sleep(2); shot("08-settings.png")
    a = find_center(dump(), ["العربية"])
    if a:
        tap(a); time.sleep(5)
        sh("adb shell input keyevent KEYCODE_BACK", check=False); time.sleep(2)
        shot("09-arabic.png")
        ts = texts(dump())
        log("arabic UI active: " + str(any("نزّل" in t or "جلب الرابط" in t for t in ts)))
else:
    log("WARN: settings button not found")

# crash check
r = sh("adb logcat -d | grep -E 'FATAL EXCEPTION' | head -5", check=False)
if r.stdout.strip():
    log("FAIL: crash detected in logcat:\n" + r.stdout.strip()[:400])
    open(os.path.join(OUT,"results.txt"),"w").write("\n".join(RESULTS)); sys.exit(1)
log("no crash in logcat")
log("=== done ===")
open(os.path.join(OUT,"results.txt"),"w").write("\n".join(RESULTS))
