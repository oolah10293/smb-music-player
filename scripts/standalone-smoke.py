"""Emulator smoke: native page gestures, direct HTTP audio, pause/live replay and station CRUD.
Uses only an ephemeral local fixture; no NAS, Pi or real station required.
"""
import http.server, threading, tempfile, wave, functools, subprocess, time, re, json, xml.etree.ElementTree as ET
from pathlib import Path
PKG='com.smbmusic.player'
OUT=Path('smoke-results');OUT.mkdir(exist_ok=True)
def adb(*args, binary=False):
    return subprocess.check_output(['adb',*map(str,args)],text=not binary,timeout=35)
def shell(*args): return adb('shell',*args)
def launch(page):
    shell('am','start','-n',PKG+'/.MainActivity','--ei',PKG+'.PAGE',page)
    time.sleep(2)
def tree():
    shell('uiautomator','dump','/sdcard/smb-window.xml')
    return ET.fromstring(shell('cat','/sdcard/smb-window.xml'))
def node(id, timeout=20):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        for e in tree().iter('node'):
            if e.get('resource-id')==PKG+':id/'+id: return e
        time.sleep(.5)
    raise AssertionError('Missing control: '+id)
def tap(id, long=False):
    e=node(id); x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')));x=(x1+x2)//2;y=(y1+y2)//2
    if long: shell('input','swipe',x,y,x,y,1200)
    else: shell('input','tap',x,y)
def wait_text(id,text,timeout=30):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        e=node(id)
        if text in e.get('text',''): return
        time.sleep(.5)
    raise AssertionError(f'{id}: expected {text}, got {e.get("text")}')
def screenshot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
    (OUT/(name+'.xml')).write_text(ET.tostring(tree(),encoding='unicode'))
def text_button(label):
    for e in tree().iter('node'):
        if e.get('text','').lower()==label.lower():
            x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')));shell('input','tap',(x1+x2)//2,(y1+y2)//2);return
    raise AssertionError('Missing '+label)
with tempfile.TemporaryDirectory() as folder:
    with wave.open(str(Path(folder)/'fixture.wav'),'wb') as audio:
        audio.setnchannels(1);audio.setsampwidth(2);audio.setframerate(22050);audio.writeframes(b'\0\0'*(22050*120))
    server=http.server.ThreadingHTTPServer(('0.0.0.0',18765),functools.partial(http.server.SimpleHTTPRequestHandler,directory=folder))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
        for permission in ['BLUETOOTH_CONNECT','POST_NOTIFICATIONS']: shell('pm','grant',PKG,'android.permission.'+permission)
        shell('settings','put','system','screen_off_timeout',600000)
        # Seed an old-format paused queue to exercise migration and service-owned Resume Playlist.
        seed={'items':[{'uri':'smb://fixture.invalid/Music/a.mp3','filename':'a.mp3'},
                       {'uri':'smb://fixture.invalid/Music/a.mp3','filename':'a.mp3'},
                       {'uri':'smb://fixture.invalid/Music/b.mp3','filename':'b.mp3'}],
              'index':1,'positionMs':23456,'shuffle':True,'explicitlyStopped':True}
        shell('run-as',PKG,'mkdir','-p','no_backup')
        subprocess.run(['adb','shell',f"run-as {PKG} sh -c 'cat > no_backup/standalone-session.json'"],
            input=json.dumps(seed),text=True,check=True,timeout=20)
        adb('logcat','-c');launch(2);node('radioUrlEdit');screenshot('radio-empty')
        # Swipe from ordinary Radio content through Now Playing to Browse.
        w,h=map(int,re.search(r'(\d+)x(\d+)',shell('wm','size')).groups())
        shell('input','swipe',int(.15*w),int(.55*h),int(.85*w),int(.55*h),350)
        node('queueSortButton');screenshot('files-now-playing')
        shell('input','swipe',int(.15*w),int(.55*h),int(.85*w),int(.55*h),350)
        node('addressEdit');screenshot('browse')
        launch(2);tap('radioUrlEdit');shell('input','text','http://10.0.2.2:18765/fixture.wav')
        shell('input','keyevent',4);tap('radioAddButton');launch(1)
        wait_text('playbackStatus','Live');screenshot('radio-playing')
        tap('radioPlayPause');wait_text('playbackStatus','Paused');tap('radioPlayPause');wait_text('playbackStatus','Live')
        tap('queueSortButton');wait_text('queueSortButton','A–Z')
        snapshot=json.loads(shell('run-as',PKG,'cat','no_backup/standalone-session.json'))
        assert not snapshot['radio'] and not snapshot['shouldPlay'],snapshot
        assert snapshot['index']==1 and snapshot['positionMs']==23456 and snapshot['shuffle'],snapshot
        assert [x['uri'] for x in snapshot['items']]==[x['uri'] for x in seed['items']],snapshot
        screenshot('playlist-restored')
        launch(2);tap('radioStationPlay');launch(1);wait_text('playbackStatus','Live')
        launch(2);tap('radioStationPlay',long=True)
        # Dialog EditText starts selected. Replace the name through the soft-input test path.
        shell('input','text','SmokeStation');text_button('Save')
        wait_text('radioStationName','SmokeStation');tap('radioStationDelete');node('radioUndoButton');tap('radioUndoButton')
        wait_text('radioStationName','SmokeStation');screenshot('radio-saved')
        shell('am','force-stop',PKG);launch(2);wait_text('radioStationName','SmokeStation')
        tap('radioStationPlay');launch(1);wait_text('playbackStatus','Live');tap('quitButton')
        time.sleep(1)
        errors=adb('logcat','-d','-b','crash')
        assert 'FATAL EXCEPTION' not in errors, errors
        (OUT/'result.txt').write_text('PASS: swipe pages, HTTP audio, pause/live replay, rename, delete/Undo, persistence, v0.5.1 queue migration, duplicate queue/index/position restore and Quit.\nNot a physical cellular/Doze/Bluetooth test.\n')
    finally:
        (OUT/'logcat.txt').write_text(adb('logcat','-d'))
        try: screenshot('final')
        except Exception: pass
        server.shutdown()
