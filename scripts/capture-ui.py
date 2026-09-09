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
try:
    shell('settings','put','system','font_scale','1.0');shell('settings','put','system','accelerometer_rotation','0');shell('settings','put','system','user_rotation','0')
    adb('install','-r',str(root/'app/build/outputs/apk/debug/app-debug.apk'))
    shell('am','force-stop','kr.toptap.android');shell('am','start','-n','kr.toptap.android/.MainActivity');time.sleep(1)
    capture('home');click('설정');capture('settings')
    previous=next(n.attrib.get('checked')=='true' for n in nodes() if n.attrib.get('content-desc')=='부드럽게 올라가기')
    click('부드럽게 올라가기')
    preferences=ET.fromstring(shell('run-as','kr.toptap.android','cat','shared_prefs/toptap.xml'))
    current=preferences.find("boolean[@name='smooth_scroll']").attrib['value']=='true'
    assert current!=previous,'Smooth switch did not persist its value'
    capture('settings-smooth');click('부드럽게 올라가기')
    click('도움말');capture('help');click('홈')
    shell('settings','put','system','font_scale','2.0');time.sleep(1);capture('home-large-text');click('설정');capture('settings-large-text')
    shell('settings','put','system','font_scale','1.0');shell('wm','user-rotation','lock','1');time.sleep(2);capture('settings-landscape')
    click('도움말');capture('help-landscape')
    print('PASS: 8 native UI states captured; navigation and smooth switch verified')
finally:
    shell('wm','user-rotation','free')
    for key,value in old.items():
        if value=='null':shell('settings','delete','system',key)
        else:shell('settings','put','system',key,value)
