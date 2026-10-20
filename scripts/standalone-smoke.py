"""Emulator smoke: native page gestures, direct HTTP audio, pause/live replay and station CRUD.
Uses only an ephemeral local fixture; no NAS, Pi or real station required.
"""
import http.server, threading, tempfile, wave, functools, subprocess, time, re, json, sys, xml.etree.ElementTree as ET
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
    # UIAutomator can briefly fail to obtain a hierarchy while a page settles.
    for attempt in range(3):
        try:
            shell('uiautomator','dump','/sdcard/smb-window.xml')
            return ET.fromstring(shell('cat','/sdcard/smb-window.xml'))
        except (subprocess.CalledProcessError,ET.ParseError):
            if attempt==2: raise
            time.sleep(.5)
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
    else: shell('input','swipe',x,y,x,y,120)
def wait_text(id,text,timeout=30):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        e=node(id)
        if (e.get('text','') == text if text == 'LIVE' else text in e.get('text','')): return
        time.sleep(.5)
    raise AssertionError(f'{id}: expected {text}, got {e.get("text")}')
def screenshot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
    (OUT/(name+'.xml')).write_text(ET.tostring(tree(),encoding='unicode'))
def text_button(label):
    for e in tree().iter('node'):
        if e.get('text','').lower()==label.lower():
            x1,y1,x2,y2=map(int,re.findall(r'\d+',e.get('bounds')));shell('input','swipe',(x1+x2)//2,(y1+y2)//2,(x1+x2)//2,(y1+y2)//2,120);return
    raise AssertionError('Missing '+label)
def dialog_text(value):
    # Touch-mode dialogs do not automatically focus an editor. Tap it first, then
    # wait for focus before injecting keys; otherwise the first key can be lost.
    end=time.monotonic()+10
    while time.monotonic()<end:
        editors=[e for e in tree().iter('node') if e.get('class')=='android.widget.EditText']
        if editors: break
        time.sleep(.25)
    assert editors,'Dialog editor did not appear'
    x1,y1,x2,y2=map(int,re.findall(r'\d+',editors[0].get('bounds')))
    shell('input','swipe',(x1+x2)//2,(y1+y2)//2,(x1+x2)//2,(y1+y2)//2,120)
    end=time.monotonic()+10
    while time.monotonic()<end:
        focused=[e for e in tree().iter('node') if e.get('class')=='android.widget.EditText' and e.get('focused')=='true']
        if focused: break
        time.sleep(.25)
    assert focused,'Dialog editor did not receive focus'
    old=focused[0].get('text','')
    shell('input','keyevent','KEYCODE_MOVE_END',*(['KEYCODE_DEL']*len(old)))
    shell('input','text',value)
    actual=[e.get('text') for e in tree().iter('node') if e.get('class')=='android.widget.EditText' and e.get('focused')=='true']
    assert actual==[value],('Injected text mismatch',actual)
with tempfile.TemporaryDirectory() as folder:
    with wave.open(str(Path(folder)/'fixture.wav'),'wb') as audio:
        audio.setnchannels(1);audio.setsampwidth(2);audio.setframerate(22050);audio.writeframes(b'\0\0'*(22050*120))
    server=http.server.ThreadingHTTPServer(('0.0.0.0',18765),functools.partial(http.server.SimpleHTTPRequestHandler,directory=folder))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    house_state={'stations':[{'id':'house-a','name':'House Alpha','url':'http://10.0.2.2:18765/fixture.wav?house=a'},
                              {'id':'house-b','name':'House Beta','url':'http://10.0.2.2:18765/fixture.wav?house=b'}],
                 'bad':False,'requests':[]}
    class HouseFixture(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            house_state['requests'].append(('GET',self.path))
            body = b'{bad json' if house_state['bad'] else json.dumps({'service':'house-audio-server','stations':house_state['stations']}).encode()
            self.send_response(200);self.send_header('Content-Type','application/json')
            self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
        def do_POST(self):
            house_state['requests'].append(('POST',self.path));self.send_error(405)
        def log_message(self,*args): pass
    house=http.server.ThreadingHTTPServer(('0.0.0.0',8787),HouseFixture)
    threading.Thread(target=house.serve_forever,daemon=True).start()
    def station_rows():
        prefs=ET.fromstring(shell('run-as',PKG,'cat','shared_prefs/standalone_radio.xml'))
        return json.loads(next(e.text for e in prefs if e.get('name')=='stations'))
    def wait_stations(predicate,timeout=85):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            rows=station_rows()
            if predicate(rows): return rows
            time.sleep(1)
        raise AssertionError('Station refresh did not converge: '+str(rows))
    def wait_snapshot(predicate,timeout=15):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            snapshot=json.loads(shell('run-as',PKG,'cat','no_backup/standalone-session.json'))
            if predicate(snapshot): return snapshot
            time.sleep(.5)
        raise AssertionError('Playback state did not converge: '+str(snapshot))
    try:
        # This app uses no Play services. On a fresh image, their bulk provisioning
        # caused a verified Pixel Launcher ANR that covered the app before tests began.
        packages=shell('pm','list','packages')
        for package in ['com.google.android.gms','com.android.vending']:
            if 'package:'+package+'\n' in packages:
                shell('pm','disable-user','--user','0',package)
        shell('am','force-stop','com.google.android.apps.nexuslauncher')
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
        adb('logcat','-c')
        # Check file controls BEFORE radio: radio source switching explicitly grants
        # commands and used to hide a broken initial onConnect permission result.
        launch(1);wait_text('playbackStatus','Paused');wait_text('nowPlayingText','a')
        assert node('nowPlayingText').get('text')=='a', 'Retained file queue unavailable on initial connection'
        assert node('exo_play_pause').get('enabled')=='true', 'File Play disabled on initial connection'
        assert node('exo_next').get('enabled')=='true', 'File Next disabled on initial connection'
        assert node('exo_shuffle').get('enabled')=='true', 'File Shuffle disabled on initial connection'
        # Verify commands reach the service, rather than checking appearance alone.
        tap('exo_shuffle');wait_snapshot(lambda s: not s['shuffle'])
        tap('exo_shuffle');wait_snapshot(lambda s: s['shuffle'])
        screenshot('file-controls-before-radio')
        # Recreate the service/controller from retained state, still without any radio command.
        shell('am','force-stop',PKG);launch(1)
        wait_text('playbackStatus','Paused');wait_text('nowPlayingText','a')
        assert node('nowPlayingText').get('text')=='a', 'Retained file queue unavailable after restart'
        assert node('exo_play_pause').get('enabled')=='true', 'File Play disabled after restart'
        assert node('exo_next').get('enabled')=='true', 'File Next disabled after restart'
        assert node('exo_shuffle').get('enabled')=='true', 'File Shuffle disabled after restart'
        tap('exo_shuffle');wait_snapshot(lambda s: not s['shuffle'])
        tap('exo_shuffle');wait_snapshot(lambda s: s['shuffle'])
        screenshot('file-controls-after-restart')
        launch(2);node('radioUrlEdit');screenshot('radio-empty')
        if '--station-refresh-only' in sys.argv:
            tap('radioUrlEdit');shell('input','text','http://10.0.2.2:18765/fixture.wav')
            shell('input','keyevent',4);tap('radioAddButton');wait_text('radioStatus','Live')
            launch(1);wait_text('playbackStatus','LIVE')
        else:
            # Use a sustained drag: cold emulator input injection can consume a short swipe
            # duration waiting for DOWN dispatch. Native gesture handling still performs navigation.
            # Swipe from ordinary Radio content through Now Playing to Browse.
            w,h=map(int,re.search(r'(\d+)x(\d+)',shell('wm','size')).groups())
            shell('input','swipe',int(.15*w),int(.55*h),int(.85*w),int(.55*h),1200)
            node('queueSortButton');screenshot('files-now-playing')
            shell('input','swipe',int(.15*w),int(.55*h),int(.85*w),int(.55*h),1200)
            node('addressEdit');screenshot('browse')
            launch(2);tap('radioUrlEdit');shell('input','text','http://10.0.2.2:18765/fixture.wav')
            shell('input','keyevent',4);tap('radioAddButton');wait_text('radioStatus','Live');launch(1)
            wait_text('playbackStatus','LIVE');screenshot('radio-playing')
            tap('radioPlayPause');wait_text('playbackStatus','Paused');tap('radioPlayPause');wait_text('playbackStatus','LIVE')
            tap('queueSortButton');wait_text('queueSortButton','A–Z');wait_text('playbackStatus','Paused')
            snapshot=json.loads(shell('run-as',PKG,'cat','no_backup/standalone-session.json'))
            assert not snapshot['radio'] and not snapshot['shouldPlay'],snapshot
            assert snapshot['index']==1 and snapshot['positionMs']==23456 and snapshot['shuffle'],snapshot
            assert [x['uri'] for x in snapshot['items']]==[x['uri'] for x in seed['items']],snapshot
            screenshot('playlist-restored')
            launch(2);tap('radioStationPlay');wait_text('radioStatus','Live');launch(1);wait_text('playbackStatus','LIVE')
            launch(2);tap('radioStationPlay',long=True)
            # Dialog EditText starts selected. Replace the name through the soft-input test path.
            dialog_text('SmokeStation');text_button('Save')
            wait_text('radioStationName','SmokeStation');tap('radioStationDelete');node('radioUndoButton');tap('radioUndoButton')
            wait_text('radioStationName','SmokeStation');screenshot('radio-saved')
            shell('am','force-stop',PKG);launch(2);wait_text('radioStationName','SmokeStation')
            tap('radioStationPlay');wait_text('radioStatus','Live');launch(1);wait_text('playbackStatus','LIVE');tap('quitButton')
            time.sleep(1)
            # Configure once, then verify unattended initial, periodic and network-return refreshes.
            launch(2);tap('radioStationPlay');wait_text('radioStatus','Live');launch(1);wait_text('playbackStatus','LIVE')
        before=json.loads(shell('run-as',PKG,'cat','no_backup/standalone-session.json'))
        launch(2);tap('radioHouseStatus');dialog_text('10.0.2.2');text_button('Save')
        config=ET.fromstring(shell('run-as',PKG,'cat','shared_prefs/house_radio_sync.xml'))
        assert next(e.text for e in config if e.get('name')=='host')=='10.0.2.2',ET.tostring(config)
        (OUT/'connectivity-before-house.txt').write_text(shell('dumpsys','connectivity'))
        (OUT/'wifi-before-house.txt').write_text(shell('cmd','wifi','status'))
        rows=wait_stations(lambda rows: len(rows)==3 and any(x.get('houseId')=='house-a' for x in rows))
        assert any(x['url']=='http://10.0.2.2:18765/fixture.wav' and x['local'] for x in rows),rows
        # No page change or refresh gesture: the one-minute automatic refresh must update the cache.
        house_state['stations']=[{'id':'house-a','name':'House Renamed','url':'http://10.0.2.2:18765/fixture.wav?house=changed'},
                                 {'id':'house-c','name':'House Gamma','url':'http://10.0.2.2:18765/fixture.wav?house=c'}]
        rows=wait_stations(lambda rows: len(rows)==3 and any(x['name']=='House Renamed' for x in rows)
                           and any(x.get('houseId')=='house-c' for x in rows)
                           and not any(x.get('houseId')=='house-b' for x in rows))
        screenshot('house-auto-refresh')
        after=json.loads(shell('run-as',PKG,'cat','no_backup/standalone-session.json'))
        assert before['stationId']==after['stationId'] and after['radio'] and after['shouldPlay'],after
        # Force a new request through page entry and verify malformed data cannot wipe the cache.
        house_state['bad']=True
        launch(1);launch(2);wait_text('radioHouseStatus','House unavailable')
        assert {x['url'] for x in station_rows()}=={x['url'] for x in rows}
        house_state['bad']=False
        shell('svc','wifi','disable');time.sleep(3)
        cached=station_rows()
        house_state['stations'].append({'id':'house-d','name':'Home Return','url':'http://10.0.2.2:18765/fixture.wav?house=d'})
        time.sleep(3)
        assert not any(x.get('houseId')=='house-d' for x in station_rows())
        shell('svc','wifi','enable')
        wait_stations(lambda rows: any(x.get('houseId')=='house-d' for x in rows),timeout=35)
        assert all(method=='GET' and path=='/radio/stations' for method,path in house_state['requests']),house_state['requests']
        # A restart with the fixture unavailable still retains the complete cache for away use.
        house.shutdown();shell('am','force-stop',PKG);launch(2)
        assert any(x.get('houseId')=='house-d' for x in station_rows())
        screenshot('house-cached-offline')
        launch(1);tap('quitButton')
        errors=adb('logcat','-d','-b','crash')
        assert 'FATAL EXCEPTION' not in errors, errors
        label = ('PASS: targeted station-refresh test; direct HTTP playback; ' if '--station-refresh-only' in sys.argv else
                 'PASS: full playback/navigation/CRUD/playlist-restore smoke plus ')
        (OUT/'result.txt').write_text('PASS: retained file queue, enabled Play/Next/Shuffle and service-observed Shuffle changes on initial connection and after force-stop/restart, before any radio command.\n'+label+'automatic House initial/periodic/network-return refresh, cache reconciliation, malformed-response retention, no playback change, GET-only access and offline cache persistence.\nNot a real SMB transfer or physical cellular/Doze/Bluetooth test.\n')

    finally:
        (OUT/'connectivity-final.txt').write_text(shell('dumpsys','connectivity'))
        (OUT/'wifi-final.txt').write_text(shell('cmd','wifi','status'))
        (OUT/'logcat.txt').write_text(adb('logcat','-d'))
        try: screenshot('final')
        except Exception: pass
        server.shutdown()
        house.shutdown()
