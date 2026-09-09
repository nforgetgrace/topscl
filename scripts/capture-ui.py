#!/usr/bin/env python3
"""Capture native UI at normal/large text and landscape on a designated emulator."""
import argparse
import re
import shlex
import subprocess
import time
from pathlib import Path
import xml.etree.ElementTree as ET

p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--adb',default='adb');args=p.parse_args()
if not args.serial.startswith('emulator-'):p.error('Use a disposable emulator')
root=Path(__file__).resolve().parent.parent
out=root/'docs/screenshots';out.mkdir(parents=True,exist_ok=True)
def adb(*cmd,binary=False):return subprocess.check_output([args.adb,'-s',args.serial,*cmd],text=not binary,timeout=40)
def shell(*cmd):return adb('shell',shlex.join(map(str,cmd))).strip()
def nodes():
    shell('uiautomator','dump','/sdcard/toptap-ui.xml')
    return list(ET.fromstring(shell('cat','/sdcard/toptap-ui.xml')).iter('node'))
def click(text):
    items=nodes()
    matches=[node for node in items if node.attrib.get('content-desc')==text] or [node for node in items if node.attrib.get('text')==text]
    for node in matches:
        l,t,r,b=map(int,re.findall(r'\d+',node.attrib['bounds']));shell('input','tap',(l+r)//2,(t+b)//2);time.sleep(.6);return
    raise AssertionError('Control absent: '+text)
def capture(name):
    time.sleep(1);(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
    xml=shell('uiautomator','dump','/sdcard/toptap-ui.xml');(out/(name+'.xml')).write_text(shell('cat','/sdcard/toptap-ui.xml'))
old={k:shell('settings','get','system',k) for k in ('font_scale','accelerometer_rotation','user_rotation')}
saved_preferences=None
try:
    shell('settings','put','system','font_scale','1.0');shell('settings','put','system','accelerometer_rotation','0');shell('settings','put','system','user_rotation','0')
    adb('install','-r',str(root/'app/build/outputs/apk/debug/app-debug.apk'))
    shell('am','force-stop','kr.toptap.android');shell('am','start','-n','kr.toptap.android/.MainActivity');time.sleep(1)
    capture('home');click('설정');capture('settings')
    previous=next(n.attrib.get('checked')=='true' for n in nodes() if n.attrib.get('content-desc')=='빠르고 부드럽게')
    click('빠르고 부드럽게')
    preferences=ET.fromstring(shell('run-as','kr.toptap.android','cat','shared_prefs/toptap.xml'))
    current=preferences.find("boolean[@name='fast_scroll']").attrib['value']=='true'
    assert current!=previous,'Smooth switch did not persist its value'
    capture('settings-smooth');click('빠르고 부드럽게')
    click('도움말');capture('help');click('홈')
    shell('settings','put','system','font_scale','2.0');time.sleep(1);capture('home-large-text');click('설정');capture('settings-large-text')
    shell('settings','put','system','font_scale','1.0');shell('wm','user-rotation','lock','1');time.sleep(2);capture('settings-landscape')
    click('도움말');capture('help-landscape')
    shell('wm','user-rotation','lock','0');time.sleep(.5)
    saved_preferences=shell('run-as','kr.toptap.android','cat','shared_prefs/toptap.xml')
    shell('am','force-stop','kr.toptap.android')
    blank='<map><boolean name="consent" value="false"/><boolean name="enabled" value="false"/></map>'
    subprocess.run([args.adb,'-s',args.serial,'shell','run-as kr.toptap.android sh -c '+shlex.quote('cat > shared_prefs/toptap.xml')],input=blank,text=True,check=True,timeout=40)
    shell('am','start','-n','kr.toptap.android/.MainActivity');time.sleep(.5)
    click('탑탭 시작하기');capture('disclosure');shell('input','keyevent','BACK')
    consent=ET.fromstring(shell('run-as','kr.toptap.android','cat','shared_prefs/toptap.xml')).find("boolean[@name='consent']")
    assert consent.attrib['value']=='false','Cancelling disclosure granted consent'
    print('PASS: 9 native UI states captured; navigation, fast switch and disclosure cancellation verified')
finally:
    if saved_preferences is not None:
        shell('am','force-stop','kr.toptap.android')
        subprocess.run([args.adb,'-s',args.serial,'shell','run-as kr.toptap.android sh -c '+shlex.quote('cat > shared_prefs/toptap.xml')],input=saved_preferences,text=True,check=True,timeout=40)
    shell('wm','user-rotation','free')
    for key,value in old.items():
        if value=='null':shell('settings','delete','system',key)
        else:shell('settings','put','system',key,value)
