#!/usr/bin/env python3
"""Destructive only to TopTap test data on an explicitly selected emulator.

No third-party test libraries. Fixture position is read from its own debug prefs,
not UiAutomation (which can temporarily suppress accessibility services).
Debug setup writes consent to bypass repetitive onboarding in this test emulator.
Production consent is separately verified through the actual disclosure dialog.
"""
import argparse
import hashlib
import http.server
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import struct
import threading
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
    shell("run-as", FIXTURE, "rm", "-f", "files/motion.json")
    shell("am", "start", "-n", FIXTURE + "/.MainActivity", "--es", "mode", mode, "--ei", "offset", offset)
    time.sleep(.6)
    wait_for(lambda: pref("mode")==mode and pref("ready") and (mode=="web" or offset==0 or (pref("position") or 0)>0), timeout=25, message="fixture loaded")
    if mode=="web" and offset>0:
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
        if "NOT_TOUCHABLE" in window:
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

def status_bar_height():
    state=shell("dumpsys","window","displays")
    match=re.search(r"type=statusBars frame=\[0,0\]\[\d+,(\d+)\]",state)
    assert match,"Could not read the actual system status-bar height"
    return int(match.group(1))

def screenshot(name):
    output = ROOT / "docs" / "screenshots" / (args.serial + "-" + name + ".png")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(adb("exec-out", "screencap", "-p", binary=True))

original_services = shell("settings", "get", "secure", "enabled_accessibility_services")
original_enabled = shell("settings", "get", "secure", "accessibility_enabled")
baseline = ":".join(x for x in original_services.split(":") if x not in ("", "null", SERVICE, PKG+"/"+PKG+".TopTapService"))
results = []
measurements = {}
complete = False

def setup(**overrides):
    shell("settings", "put", "secure", "enabled_accessibility_services", baseline)
    shell("am", "force-stop", PKG)
    factory_defaults = overrides.pop("factory_defaults", False)
    values = dict(consent=True, enabled=True, position=0, width=96, timeout=12, indicator=True, double_tap=False, gesture_fallback=False, fast_scroll=False)
    if factory_defaults: values.pop("fast_scroll")
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
    measurements.clear()
    try:
        run()
    except Exception as error:
        results.append(dict(test=name,result="FAIL",error=str(error)))
        print(shell("logcat", "-d", "-s", "TopTap:D", "AndroidRuntime:E"), flush=True)
        screenshot("qa-failure")
        (ROOT/".tools"/"e2e-failure-windows.txt").write_text(shell("dumpsys","window","windows"))
        raise
    result = dict(test=name, result="PASS", seconds=round(time.monotonic()-start,2))
    if measurements: result["measurements"] = dict(measurements)
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
        def single_tap_migration():
            setup(double_tap=True,width=64,position=2,indicator=True);launch("direct",350);wait_for(overlay_present);tap()
            wait_for(lambda:pref("position")==0,timeout=3,message="old two-tap preference cannot block single tap")
        case("old two-tap and narrow-region settings migrate to one tap",single_tap_migration)
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
        def smooth_top(mode,offset=None):
            initial=offset if offset is not None else 48 if mode=="web" else 120
            artifact=mode+("-deep" if offset is not None else "")
            setup(fast_scroll=True);launch(mode,initial);wait_for(overlay_present)
            old_touches=pref("touch_count") or 0
            started_ms=int(float(shell("cat","/proc/uptime").split()[0])*1000)
            started=time.monotonic();tap();wait_for(lambda:pref("position")==0,timeout=12,message="smooth "+mode+" reached top")
            time.sleep(.7)
            wait_for(lambda:pref("position")==0,timeout=3,message="top stays reached after native edge settling")
            time.sleep(.3);assert pref("position")==0,"Reached top only transiently"
            if mode!="web":assert pref("offset_px")==0,"Smooth scroll left first row clipped"
            trace=json.loads(shell("run-as",FIXTURE,"cat","files/motion.json"))
            (ROOT/"docs"/("motion-"+args.serial+"-"+artifact+".json")).write_text(json.dumps(trace)+"\n")
            # Require movement across multiple frames after the finger lifts:
            # this proves native inertia rather than just an animated drag.
            touches=trace["touch"][old_touches*2:]
            glides=[]
            for index,(ended,action) in enumerate(touches):
                if action!=1:continue
                next_down=next((t for t,a in touches[index+1:] if a==0),float("inf"))
                samples=[(t,y) for t,y in trace["motion"] if ended<t<next_down]
                if len(samples)>=5 and samples[-1][0]-samples[0][0]>=100:
                    glides.append(samples[0][1]-samples[-1][1])
            movement=[(t,y) for t,y in trace["motion"] if t>=started_ms]
            assert movement and movement[-1][1]==0,"No recorded arrival at the actual top"
            if len(movement)<=2:
                assert mode=="web" and not ((pref("touch_count") or 0)-old_touches),"Unexpected lack of animation"
                measurements.update(motion_kind="instant_native",settled_position=pref("position"),injected_swipes=0)
                screenshot("qa-smooth-"+mode+"-top")
                return
            assert len(movement)>=5 and movement[-1][0]-movement[0][0]>=100,"No actual animated movement"
            assert movement[0][1]-movement[-1][1]>200,"Movement did not cover the visible content"
            first_zero=next((i for i,(_,y) in enumerate(movement) if y==0),len(movement)-1)
            forward=movement[:first_zero+1]
            assert all(b[1]<=a[1] for a,b in zip(forward,forward[1:])),"Scroll reversed direction before the top"
            max_gap=max(b[0]-a[0] for a,b in zip(forward,forward[1:]))
            assert max_gap<=100,f"Visible pause in the measured animation: {max_gap}ms"
            injected=(pref("touch_count") or 0)-old_touches
            if injected:assert any(distance>200 for distance in glides),f"No sustained post-release glide: {glides}"
            measurements.update(motion_kind="animated",animation_ms=forward[-1][0]-forward[0][0],max_frame_gap_ms=max_gap,injected_swipes=injected,settled_position=pref("position"))
            screenshot("qa-smooth-"+artifact+"-top")
        for mode in ("list","grid","web","fling_list"):
            case("fast "+mode+" reaches actual top with measured motion",lambda m=mode:smooth_top(m))
        case("deep gesture-only list reaches actual top with measured motion",lambda:smooth_top("fling_list",350))
        def smooth_already_top(mode):
            setup(fast_scroll=True);launch(mode,0);wait_for(overlay_present);tap();time.sleep(.8)
            assert pref("position")==0 and not pref("touch_count"),"Already-top viewport received a physical pull"
        for mode in ("list","web"):
            case("smooth "+mode+" already at top receives no physical swipe",lambda m=mode:smooth_already_top(m))
        def smooth_stop():
            setup(fast_scroll=True);launch("fling_list",350);wait_for(overlay_present);tap()
            wait_for(lambda:(pref("touch_count") or 0)>0,message="smooth gesture began")
            time.sleep(.15);tap();count=pref("touch_count");time.sleep(2.2)
            assert pref("touch_count")==count,"New gesture sent after cancellation"
            assert pref("position")>0,"Fixture too short to prove cancellation"
        case("smooth cancel stops sending additional gestures",smooth_stop)
        def smooth_immediate_stop():
            setup(fast_scroll=True);launch("fling_list",350);wait_for(overlay_present)
            shell("sh","-c","input tap 152 31; input tap 152 31");time.sleep(2)
            assert (pref("touch_count") or 0)<=1,"Stop tap restarted the interrupted gesture"
            count=pref("touch_count");time.sleep(.5);assert pref("touch_count")==count
        case("smooth immediate second tap does not restart a cancelled stroke",smooth_immediate_stop)
        def smooth_double_stop():
            setup(fast_scroll=True,double_tap=True);launch("fling_list",350);wait_for(overlay_present)
            tap();wait_for(lambda:(pref("touch_count") or 0)>0,message="one tap starts despite old two-tap preference")
            tap()
            count=pref("touch_count");time.sleep(2)
            assert pref("touch_count")==count,"Double-tap stop became a new start"
        case("single stop tap works with a legacy two-tap preference",smooth_double_stop)
        def smooth_context():
            setup(fast_scroll=True);launch("fling_list",350);wait_for(overlay_present);tap()
            wait_for(lambda:(pref("touch_count") or 0)>0)
            launch("list",120);time.sleep(1.2)
            assert pref("position")==120 and not pref("touch_count"),"New window inherited a gesture"
        case("smooth context change never sends a gesture to new window",smooth_context)
        def close_app():
            setup();wait_for(overlay_present)
            activity_pattern=r"ActivityRecord\{[^\n]*kr\.toptap\.android/(?:kr\.toptap\.android\.)?\.?MainActivity"
            assert re.search(activity_pattern,shell("dumpsys","activity","activities")),"TopTap activity was not open before dismissal"
            shell("input","keyevent","KEYCODE_APP_SWITCH");time.sleep(1)
            shell("input","swipe",540,1450,540,350,300);time.sleep(.6)
            shell("input","keyevent","HOME")
            activities=shell("dumpsys","activity","activities")
            assert not re.search(activity_pattern,activities),"TopTap card was not dismissed"
            launch();wait_for(overlay_present);tap();wait_for(lambda:pref("position")==0,message="scroll after recent-task dismissal")
        case("closing TopTap in recent apps keeps background scrolling available",close_app)
        def fast_native(mode):
            setup(fast_scroll=True);launch(mode,350);wait_for(overlay_present)
            start=time.monotonic();tap()
            wait_for(lambda:pref("position")==0,timeout=2.5,message="native end action reaches top promptly")
            assert pref("direct_used" if mode!="granular" else "granular_used"),"Fast native end action was bypassed"
            assert not pref("touch_count"),"Native end action was replaced by repeated swipes"
            assert pref("offset_px")==0
            measurements.update(seconds_to_top=round(time.monotonic()-start,3))
        for mode in ("direct","granular","nested"):
            case("fast mode uses native end action for "+mode,lambda m=mode:fast_native(m))
        def page_only():
            setup(fast_scroll=False);launch("page_only",20);wait_for(overlay_present);tap()
            wait_for(lambda:pref("position")==0,timeout=10,message="page-only node scrolls")
            assert pref("page_used") and not pref("touch_count")
        case("page-only accessibility action is supported",page_only)
        def full_status_bar():
            setup(factory_defaults=True,double_tap=True,width=64,position=2,indicator=True)
            width=int(re.findall(r"size: (\d+)x\d+",shell("wm","size"))[-1])
            density=int(re.findall(r"density: (\d+)",shell("wm","density"))[-1])/160
            height=status_bar_height()
            for fraction in (.02,.25,.5,.75,.98):
                for vertical in (.2,.5,.85):
                    launch("direct",350);wait_for(overlay_present)
                    shell("input","tap",round(width*fraction),round(height*vertical))
                    wait_for(lambda:pref("position")==0,timeout=3,message="one tap across full status bar at "+str((fraction,vertical)))
            measurements.update(tap_locations=15,status_bar_height=height)
        case("one tap works at fifteen positions across the full status bar",full_status_bar)
        def foreground_session():
            setup();launch();wait_for(overlay_present)
            assert "android.permission.POST_NOTIFICATIONS: granted=false" in shell("dumpsys","package",PKG),"This case requires notifications to be denied"
            wait_for(lambda: "isForeground=true" in shell("dumpsys","activity","services",PKG+"/.SessionService"),message="foreground session actually active")
        case("user-enabled session is foreground without notification permission",foreground_session)
        def clear_all():
            setup(factory_defaults=True);launch();wait_for(overlay_present)
            wait_for(lambda:"isForeground=true" in shell("dumpsys","activity","services",PKG+"/.SessionService"))
            # RotationHistory retains destroyed ActivityRecord descriptions. Match live
            # task history entries, not those diagnostic references to old activities.
            activity_pattern=r"(?m)^\s*\* Hist\s+#\d+: ActivityRecord\{[^\n]*kr\.toptap\.(?:android|fixture)/"
            for cycle in range(3):
                # Open the two tasks, then operate the dedicated emulator's real Clear all button.
                shell("am","start","-n",PKG+"/.MainActivity");launch()
                assert len(re.findall(activity_pattern,shell("dumpsys","activity","activities")))>=2,"Both test tasks must be present before Clear all"
                shell("input","keyevent","KEYCODE_APP_SWITCH");time.sleep(.8)
                for _ in range(4):
                    shell("input","swipe",100,1100,980,1100,180);time.sleep(.25)
                time.sleep(.8) # Let the launcher's end-of-carousel spring settle before clicking.
                if cycle==0:screenshot("qa-clear-all-before")
                shell("input","tap",210,1190);time.sleep(.7)
                wait_for(lambda:not re.search(activity_pattern,shell("dumpsys","activity","activities")),timeout=6,message="Clear all removes both test activities")
                assert "isForeground=true" in shell("dumpsys","activity","services",PKG+"/.SessionService"),"Clear all stopped the foreground session"
                launch("direct",350);wait_for(overlay_present);tap();wait_for(lambda:pref("position")==0,timeout=3,message="scroll after Clear all")
            measurements.update(clear_all_cycles=3)
            screenshot("qa-clear-all-after")
        case("real recent-apps Clear all preserves scrolling for three cycles",clear_all)
        def status_pixels(mode="list"):
            # Keep the real system clock away from its minute boundary during comparison.
            second=int(shell("date","+%S"))
            if second>=40:time.sleep(61-second)
            def raw_bar():
                raw=adb("exec-out","screencap",binary=True)
                width,height,fmt=struct.unpack_from("<III",raw)
                assert fmt in (1,2),"Unexpected raw screenshot pixel format"
                offset=len(raw)-width*height*4
                assert offset in (12,16),"Unexpected screenshot header"
                rows=status_bar_height()
                return raw[offset:offset+width*rows*4]
            def settled_bar():
                previous=raw_bar();deadline=time.monotonic()+3
                while time.monotonic()<deadline:
                    time.sleep(.2);current=raw_bar()
                    if current==previous:return current
                    previous=current
                raise AssertionError("System status bar did not settle for a pixel comparison")
            suffix="" if mode=="list" else "-"+mode
            setup(enabled=False);launch(mode,350);wait_for(lambda:not overlay_present())
            baseline=settled_bar();screenshot("qa-statusbar-stock"+suffix)
            setup(factory_defaults=True,indicator=True);launch(mode,350);wait_for(overlay_present)
            idle=settled_bar();screenshot("qa-statusbar-idle"+suffix)
            if idle!=baseline:
                (ROOT/'.tools'/('status-baseline'+suffix+'.rgba')).write_bytes(baseline)
                (ROOT/'.tools'/('status-idle'+suffix+'.rgba')).write_bytes(idle)
                changed=[i for i,(a,b) in enumerate(zip(baseline,idle)) if a!=b]
                raise AssertionError(f"Status-bar pixels changed: {len(changed)} bytes, first={changed[:12]}")
            tap();running=raw_bar();screenshot("qa-statusbar-scrolling"+suffix)
            assert running==idle,"Scrolling changed status-bar pixels"
            measurements.update(status_bar_height=status_bar_height(),compared_rgba_bytes=len(idle),idle_changed_bytes=0,scrolling_changed_bytes=0)
        case("status-bar pixels match stock both idle and scrolling",status_pixels)
        for appearance in ("light_status","dark_status"):
            case("status-bar pixels preserve "+appearance+" appearance",lambda m=appearance:status_pixels(m))
        def gesture_only():
            setup(factory_defaults=True);launch("gesture",12);wait_for(overlay_present);tap()
            wait_for(lambda:pref("position")==0,timeout=12,message="recent movement enables a gesture-only vertical viewport")
            assert pref("touch_count")
        case("default mode handles a vertical viewport with no upward actions",gesture_only)
        def legacy_fast_default():
            setup(factory_defaults=True,smooth_scroll=False,double_tap=True,indicator=True,width=64,position=2)
            launch("fling_list",120);wait_for(overlay_present);tap()
            wait_for(lambda:(pref("touch_count") or 0)>0,timeout=3,message="upgrade uses the new fast default without another setup step")
        case("upgrade ignores the old disabled smooth-mode preference",legacy_fast_default)
        def no_consent():
            setup(consent=False,enabled=True);launch();time.sleep(.5)
            assert not overlay_present() and "isForeground=true" not in shell("dumpsys","activity","services",PKG)
            before=pref("position");tap();time.sleep(.4);assert pref("position")==before
        case("saved enable flag cannot activate scrolling without local consent",no_consent)
        def rotated_bar():
            try:
                shell("wm","user-rotation","lock","1");time.sleep(.8)
                setup(factory_defaults=True)
                width=struct.unpack_from("<I",adb("exec-out","screencap",binary=True))[0]
                assert width>1080,"Fixture did not rotate to landscape"
                for fraction in (.03,.5,.97):
                    launch("direct",350);wait_for(overlay_present)
                    shell("input","tap",round(width*fraction),31)
                    wait_for(lambda:pref("position")==0,timeout=3,message="landscape status bar tap")
            finally:shell("wm","user-rotation","lock","0");time.sleep(.5)
        case("landscape keeps the entire status bar usable with one tap",rotated_bar)
        def quick_tile_pause():
            setup();launch();wait_for(overlay_present)
            component=PKG+"/.TopTapTileService"
            try:
                shell("cmd","statusbar","add-tile",component)
                shell("cmd","statusbar","expand-settings");time.sleep(.8)
                shell("cmd","statusbar","click-tile",component);time.sleep(.4)
                shell("cmd","statusbar","collapse")
                wait_for(lambda:not overlay_present(),message="tile pause removes the trigger")
                wait_for(lambda:"isForeground=true" not in shell("dumpsys","activity","services",PKG+"/.SessionService"),message="tile pause stops the foreground session")
                shell("cmd","statusbar","expand-settings");time.sleep(.5)
                shell("cmd","statusbar","click-tile",component);time.sleep(.3);shell("cmd","statusbar","collapse")
                wait_for(overlay_present);tap();wait_for(lambda:pref("position")==0,message="tile resume restores scrolling")
            finally:shell("cmd","statusbar","collapse");shell("cmd","statusbar","remove-tile",component)
        case("quick settings pause stops the session and resume restores scrolling",quick_tile_pause)
        def explicit_stop():
            setup();launch();wait_for(overlay_present)
            shell("cmd","activity","stop-app",PKG);time.sleep(2)
            assert not shell("pidof",PKG,check=False),"Explicit system Stop was ignored"
            assert not overlay_present()
            assert "isForeground=true" not in shell("dumpsys","activity","services",PKG)
        case("explicit system Stop still stops the entire app",explicit_stop)
        def immersive():
            setup(enabled=False);launch("immersive",120);tap();time.sleep(.5)
            stock_touches=pref("touch_count") or 0
            setup(factory_defaults=True);launch("immersive",120)
            wait_for(lambda:not overlay_present(),message="hidden status bar has no touch interceptor")
            tap();time.sleep(.5)
            assert pref("position")==120 and (pref("touch_count") or 0)==stock_touches,"Fullscreen touch handling differs from stock"
            launch("direct",350);wait_for(overlay_present);tap();wait_for(lambda:pref("position")==0,timeout=3)
        case("fullscreen content is not intercepted and the status bar recovers",immersive)
        def chrome_article():
            # The real Chrome app renders an offline article served only through this emulator's ADB tunnel.
            observed={}
            article="""<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font:20px sans-serif;margin:20px}section{height:120px;border-bottom:1px solid #ddd}</style>
<h1>TOP_REACHED</h1><main></main><script>
document.querySelector('main').innerHTML=Array.from({length:300},(_,i)=>`<section>Local article section ${i}</section>`).join('');
const motion=[];let timer,frame=false;
function report(){fetch('/report',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({y:scrollY,motion})});}
addEventListener('scroll',()=>{if(!frame){frame=true;requestAnimationFrame(()=>{motion.push([performance.now(),scrollY]);frame=false;});}clearTimeout(timer);timer=setTimeout(report,150);});
setTimeout(()=>{scrollTo(0,18000);setTimeout(report,400);},300);
</script>""".encode()
            class Handler(http.server.BaseHTTPRequestHandler):
                def log_message(self,*args):pass
                def do_GET(self):
                    self.send_response(200);self.send_header("Content-Type","text/html");self.end_headers();self.wfile.write(article)
                def do_POST(self):
                    observed.update(json.loads(self.rfile.read(int(self.headers['Content-Length']))))
                    self.send_response(204);self.end_headers()
            server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            port=server.server_address[1]
            try:
                assert shell("pm","path","com.android.chrome",check=False),"The dedicated emulator needs Chrome installed"
                adb("reverse",f"tcp:{port}",f"tcp:{port}")
                setup(factory_defaults=True)
                shell("am","start","-a","android.intent.action.VIEW","-d",f"http://127.0.0.1:{port}/","-p","com.android.chrome")
                wait_for(lambda:observed.get('y',0)>10000,timeout=25,message="Chrome opens the local article (complete its first-run screen in this test emulator if needed)")
                shell("input","swipe",540,1900,540,700,500);time.sleep(1.5);wait_for(overlay_present)
                first=len(observed.get('motion',[]));start=time.monotonic();tap()
                wait_for(lambda:observed.get('y')==0,timeout=12,message="real Chrome returns to the document top")
                elapsed=time.monotonic()-start;time.sleep(.8);assert observed['y']==0
                motion=observed['motion'][first:]
                assert motion and motion[-1][1]==0,"Chrome did not reach the actual document top"
                assert all(b[1]<=a[1] for a,b in zip(motion,motion[1:])),"Chrome changed scroll direction"
                assert elapsed<2,"Chrome did not use a fast document-start action"
                report=dict(seconds_to_top=round(elapsed,3),motion_kind="animated" if len(motion)>5 else "instant_native",frames=len(motion),max_frame_gap_ms=round(max((b[0]-a[0] for a,b in zip(motion,motion[1:])),default=0),2),final_scroll_y=observed['y'])
                measurements.update(report)
                version=shell("getprop","ro.build.version.sdk")
                (ROOT/"docs"/("chrome-motion-api"+version+".json")).write_text(json.dumps(dict(report=report,motion=motion))+"\n")
                screenshot("qa-real-chrome-top")
            finally:
                adb("reverse","--remove",f"tcp:{port}",check=False);server.shutdown();server.server_close();thread.join(timeout=2)
        case("real Chrome local article reaches document top within two seconds",chrome_article)
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
