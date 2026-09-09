#!/usr/bin/env python3
"""Destructive only to TopTap test data on an explicitly selected emulator.

No third-party test libraries. Fixture position is read from its own debug prefs,
not UiAutomation (which can temporarily suppress accessibility services).
Debug setup writes consent to bypass repetitive onboarding in this test emulator.
Production consent is separately verified through the actual disclosure dialog.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
PKG = "kr.toptap.android"
FIXTURE = "kr.toptap.fixture"
SERVICE = PKG + "/.TopTapService"
parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
parser.add_argument("--quick", action="store_true")
parser.add_argument("--only", action="append", help="Run only cases containing this text (repeatable)")
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    parser.error("Only a disposable Android emulator is accepted, never a personal device.")

def adb(*command, input=None, check=True, binary=False):
    p = subprocess.run([args.adb, "-s", args.serial, *command], input=input, capture_output=True, text=not binary, timeout=40)
    if check and p.returncode:
        raise RuntimeError(f"adb {command}: {p.stderr} {p.stdout}")
    return p.stdout if binary else p.stdout.strip()

def shell(*command, **kwargs):
    return adb("shell", shlex.join(str(c) for c in command), **kwargs)

def wait_for(predicate, timeout=15, message="condition"):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(.25)
    raise AssertionError(f"Timed out: {message() if callable(message) else message}")

def pref(name):
    xml = shell("run-as", FIXTURE, "cat", "shared_prefs/qa.xml", check=False)
    try:
        node = ET.fromstring(xml).find(f"*[@name='{name}']")
        if node is None:
            return None
        if node.tag == "boolean":
            return node.attrib["value"] == "true"
        if node.tag == "string":
            return node.text
        return int(node.attrib["value"])
    except ET.ParseError:
        return None

def launch(mode="list", offset=120):
    shell("am", "force-stop", FIXTURE)
    shell("run-as", FIXTURE, "rm", "-f", "shared_prefs/qa.xml", "shared_prefs/qa.xml.bak")
    shell("am", "start", "-n", FIXTURE + "/.MainActivity", "--es", "mode", mode, "--ei", "offset", offset)
    time.sleep(.6)
    wait_for(lambda: pref("mode")==mode and pref("ready") and (mode=="web" or (pref("position") or 0)>0), timeout=25, message="fixture loaded")
    if mode=="web":
        # Use real scrolling: programmatic host/JS seeding can leave stale
        # accessibility actions in emulator WebView, unlike user touch input.
        for _ in range(max(2,min(6,offset//24))):
            shell("input","swipe",540,1900,540,700,550)
        time.sleep(.8)
        assert (pref("position") or 0)>0, "Physical input did not scroll web fixture"
    time.sleep(.25)

def overlay_present(pid=None):
    state = shell("dumpsys", "window", "windows")
    for window in re.split(r"\n  Window #\d+",state)[1:]:
        if "TopTap trigger" not in window.split("\n",1)[0]:
            continue
        if pid and not re.search(r"mSession=Session\{[^ ]+ "+re.escape(pid)+r":",window):
            continue
        if "mHasSurface=true" in window and "mViewVisibility=0x0" in window and "isVisible=true" in window:
            return True
    return False

def tap():
    density_text = shell("wm", "density")
    density = int(re.findall(r"density: (\d+)", density_text)[-1]) / 160
    shell("input", "tap", round(58*density), round(12*density))

def screenshot(name):
    output = ROOT / "docs" / "screenshots" / (args.serial + "-" + name + ".png")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(adb("exec-out", "screencap", "-p", binary=True))

original_services = shell("settings", "get", "secure", "enabled_accessibility_services")
original_enabled = shell("settings", "get", "secure", "accessibility_enabled")
baseline = ":".join(x for x in original_services.split(":") if x not in ("", "null", SERVICE, PKG+"/"+PKG+".TopTapService"))
results = []
complete = False

def setup(**overrides):
    shell("settings", "put", "secure", "enabled_accessibility_services", baseline)
    shell("am", "force-stop", PKG)
    values = dict(consent=True, enabled=True, position=0, width=96, timeout=12, indicator=True, double_tap=False, gesture_fallback=False)
    values.update(overrides)
    xml = ET.Element("map")
    for key, value in values.items():
        if isinstance(value, bool):
            ET.SubElement(xml, "boolean", name=key, value=str(value).lower())
        elif isinstance(value, int):
            ET.SubElement(xml, "int", name=key, value=str(value))
        else:
            node = ET.SubElement(xml, "set", name=key)
            for package in value:
                ET.SubElement(node, "string").text = package
    shell("run-as", PKG, "sh", "-c", "mkdir -p shared_prefs && cat > shared_prefs/toptap.xml", input=ET.tostring(xml, encoding="unicode"))
    shell("am", "start", "-n", PKG + "/.MainActivity")
    shell("settings", "put", "secure", "enabled_accessibility_services", ":".join(filter(None, [baseline, SERVICE])))
    shell("settings", "put", "secure", "accessibility_enabled", "1")
    time.sleep(.6)

def case(name, run):
    if args.only and not any(fragment in name for fragment in args.only):
        return
    start = time.monotonic()
    try:
        run()
    except Exception as error:
        results.append(dict(test=name,result="FAIL",error=str(error)))
        print(shell("logcat", "-d", "-s", "TopTap:D", "AndroidRuntime:E"), flush=True)
        screenshot("qa-failure")
        raise
    result = dict(test=name, result="PASS", seconds=round(time.monotonic()-start,2))
    results.append(result)
    print(json.dumps(result), flush=True)

def reaches_top(mode):
    # Normal web case is ~5 viewports. Very long pages separately verify the
    # documented execution budget and continuation behavior below.
    launch(mode,48 if mode=="web" else 120)
    wait_for(overlay_present, message="overlay attached")
    start = pref("position")
    tap()
    wait_for(lambda: pref("position") == 0, timeout=15, message=lambda: f"{mode} reached top from {start}; last={pref('position')}")
    time.sleep(.4)
    if mode != "web":
        assert pref("offset_px") == 0, f"First row is clipped: {pref('offset_px')}"
    if mode == "granular":
        assert pref("granular_used"), "Granular Infinity argument was not used"
    screenshot("qa-"+mode+"-top")

try:
    assert shell("getprop", "ro.kernel.qemu") == "1", "Not an emulator"
    shell("input","keyevent","224");shell("wm","dismiss-keyguard");shell("logcat","-c")
    adb("install", "-r", str(ROOT/"app/build/outputs/apk/debug/app-debug.apk"))
    adb("install", "-r", str(ROOT/"fixture/build/outputs/apk/debug/fixture-debug.apk"))
    setup()
    for mode in ("direct", "granular", "list", "grid", "web"):
        case("cross-app "+mode+" returns to actual top", lambda m=mode: reaches_top(m))
    if not args.quick:
        def horizontal():
            launch("horizontal"); before=pref("position");tap();time.sleep(1);assert pref("position")==before
        case("horizontal-only carousel unchanged", horizontal)
        def range_control():
            launch("range");before=pref("position");tap();time.sleep(1);assert pref("position")==before
        case("range control value is never adjusted",range_control)
        def fallback_disabled():
            setup();launch("gesture",3);wait_for(overlay_present);before=pref("position");tap();time.sleep(1.5);assert pref("position")==before
        case("gesture fallback is disabled by default",fallback_disabled)
        def fallback_enabled():
            setup(gesture_fallback=True);launch("gesture",3);wait_for(overlay_present);tap();wait_for(lambda:pref("position")==0,message="opt-in swipe returned to top")
        case("opt-in gesture fallback scrolls identified viewport",fallback_enabled)
        def bounded_web():
            setup(timeout=4);launch("web",200);wait_for(overlay_present);before=pref("position");tap();time.sleep(5)
            stopped=pref("position");assert 0<stopped<before;time.sleep(.8);assert pref("position")==stopped
            tap();time.sleep(.8);assert pref("position")<stopped;tap();time.sleep(.8)
        case("very long web respects time budget and resumes on next tap",bounded_web)
        def paused():
            setup(enabled=False);launch();wait_for(lambda:not overlay_present());before=pref("position");tap();time.sleep(.7);assert pref("position")==before
        case("pause removes overlay and disables trigger", paused)
        def excluded():
            setup(excluded=[FIXTURE]);launch();wait_for(lambda:not overlay_present());before=pref("position");tap();time.sleep(.7);assert pref("position")==before
        case("excluded app untouched", excluded)
        def double_tap():
            setup(double_tap=True);launch();wait_for(overlay_present);before=pref("position");tap();time.sleep(.6);assert pref("position")==before
            # A single shell command avoids host/adb latency between the two taps.
            shell("sh","-c","input tap 152 31; input tap 152 31")
            wait_for(lambda:pref("position")==0,message="double tap reached top")
        case("double-tap mode ignores one tap", double_tap)
        def drag():
            setup();launch();wait_for(overlay_present);before=pref("position")
            shell("input","swipe",152,31,152,650,300);time.sleep(.7)
            assert pref("position")==before
            wait_for(lambda:re.search(r"mCurrentFocus=.*(?:NotificationShade|StatusBar)",shell("dumpsys","window")),message="shade opened")
            shell("input","keyevent","BACK");time.sleep(.5)
        case("trigger downward drag opens shade without scrolling",drag)
        def stop():
            launch("web",250);wait_for(overlay_present);tap();time.sleep(.35);tap();time.sleep(.7)
            stopped=pref("position");time.sleep(.8);assert pref("position")==stopped and stopped>0
        case("second activation cancels ongoing scroll",stop)
        def context_change():
            launch("web",250);wait_for(overlay_present);tap();time.sleep(.25)
            launch("list",120);before=pref("position");time.sleep(1);assert pref("position")==before
        case("changed window does not inherit previous scroll",context_change)
        def process_recovery():
            launch();wait_for(overlay_present);old=shell("pidof",PKG).strip()
            shell("run-as",PKG,"kill","-9",old)
            wait_for(lambda:shell("pidof",PKG,check=False).strip() not in ("",old),timeout=20,message="OS rebound service after process death")
            new_pid=shell("pidof",PKG).strip()
            wait_for(lambda:overlay_present(new_pid),timeout=20,message="new process overlay rendered");time.sleep(.5);tap();wait_for(lambda:pref("position")==0,message="scroll after OS rebind")
        case("OS rebind after process death and scrolling recovered",process_recovery)
        def screen_cycle():
            launch();wait_for(overlay_present);shell("input","keyevent","223");wait_for(lambda:not overlay_present(),message="screen-off overlay removed")
            shell("input","keyevent","224");shell("wm","dismiss-keyguard");wait_for(overlay_present);tap();wait_for(lambda:pref("position")==0)
        case("screen off removes trigger; unlock restores it",screen_cycle)
        def revoke():
            shell("settings","put","secure","enabled_accessibility_services",baseline);wait_for(lambda:not overlay_present(),message="revoked overlay removed")
        case("permission revocation removes overlay",revoke)
    complete = True
finally:
    shell("input","keyevent","224",check=False);shell("wm","dismiss-keyguard",check=False)
    shell("settings","put","secure","enabled_accessibility_services",original_services if original_services!="null" else "",check=False)
    shell("settings","put","secure","accessibility_enabled",original_enabled if original_enabled!="null" else "0",check=False)
    version=shell("getprop","ro.build.version.sdk")
    report = dict(device=args.serial, android=shell("getprop","ro.build.version.release"), suite="filtered" if args.only else "quick" if args.quick else "full", passed=complete,
                  apk_sha256=hashlib.sha256((ROOT/"app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest(), tests=results)
    filename="e2e-results-api"+version+("-filtered" if args.only else "")+".json"
    (ROOT/"docs"/filename).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
print(f"PASS: {len(results)} emulator end-to-end cases",flush=True)
