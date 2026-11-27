"""Disposable emulator acceptance test. Uses synthetic apps and a fresh launcher."""
from pathlib import Path
import os, re, sqlite3, subprocess, sys, time, xml.etree.ElementTree as ET, zipfile, json, tempfile
from contextlib import closing
ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'build/niagara-device';OUT.mkdir(parents=True,exist_ok=True)
SDK=Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
BT=sorted((SDK/'build-tools').iterdir(),key=lambda p:tuple(int(x) if x.isdigit() else 0 for x in p.name.split('.')))[-1]
AJ=SDK/'platforms/android-35/android.jar'
if not AJ.is_file(): raise RuntimeError('The acceptance test requires Android SDK platform 35')
def run(*args,check=True,timeout=None):
    try:
        x=subprocess.run([str(v) for v in args],capture_output=True,timeout=timeout)
    except subprocess.TimeoutExpired:
        if check: raise
        return ''
    if check and x.returncode:
        print(x.stdout.decode(errors='replace'),x.stderr.decode(errors='replace'),flush=True)
        x.check_returncode()
    return x.stdout.decode(errors='replace').strip()
def adb(*args,timeout=60,**kw): return run('adb',*args,timeout=timeout,**kw)
def snapshot(name):
    adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
    xml=adb('shell','cat','/sdcard/window.xml',check=False)
    (OUT/(name+'.xml')).write_text(xml)
    with (OUT/(name+'.png')).open('wb') as f: subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=False)
    try: return ET.fromstring(xml)
    except ET.ParseError: return ET.Element('none')
def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds','')))
    adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2)
def find(root,text):
    return next((n for n in root.iter('node') if n.get('text','').casefold()==text.casefold() or n.get('content-desc','').casefold()==text.casefold()),None)
def check_folder_icon(root):
    parents = {child: parent for parent in root.iter() for child in parent}
    title = find(root, 'Auto folders')
    assert title is not None, 'Auto folders entry missing'
    row = title
    while row.get('clickable') != 'true' and row in parents:
        row = parents[row]
    icons = [node for node in root.iter('node') if node.get('resource-id') == 'android:id/icon']
    folder_icon = next((node for node in row.iter('node') if node in icons), None)
    assert folder_icon is not None, 'Auto folders has no visible native icon'
    def layout(node):
        left, top, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds', '')))
        return left, right - left, bottom - top
    assert any(layout(folder_icon) == layout(other) for other in icons if other is not folder_icon), 'Icon size or alignment differs from neighboring settings'
    return title

def onboard():
    favorite_selected=False
    goal_selected=False
    alphabet_gestures=0
    for i in range(30):
        root=snapshot('onboarding-%02d'%i)
        texts=[n.get('text','') for n in root.iter('node') if n.get('text')]
        print('UI:',texts,flush=True)
        # A supported older APK can receive the live source's update notice
        # during setup. Dismiss it in this disposable emulator for this run.
        ignore_update = find(root, 'Ignore this update')
        if ignore_update is not None and find(root, 'Open Morphe') is not None:
            tap_node(ignore_update)
            time.sleep(2)
            continue
        prefs=adb('shell','cat','/data/user/0/bitpit.launcher/shared_prefs/*.xml',check=False)
        if re.search(r'<int name="bitpit.launcher.key.BOOT_STATE" value="1"',prefs): return
        if not list(root.iter('node')):
            time.sleep(3)
            continue
        chosen=None
        # Select an existing app once, so the no-favorites confirmation cannot
        # obscure Done. Dialog actions also take priority over background rows.
        if find(root,'Your favorites') is not None and not favorite_selected:
            chosen=find(root,'Phone')
            if chosen is not None: favorite_selected=True
        if chosen is None and not goal_selected:
            chosen=find(root,'Declutter my phone')
            if chosen is not None: goal_selected=True
        for text in ['No thanks','Not now','Got it','Skip','Agree & continue','Agree and continue','Get started','Start fresh','Continue','Next',"Let's Go",'Done','Finish','Allow','Okay']:
            if chosen is not None: break
            chosen=find(root,text)
            if chosen is not None and chosen.get('enabled')=='false': chosen=None
        if chosen is None:
            # Favorite-selection completion is an icon button in some builds.
            chosen=next((n for n in root.iter('node') if n.get('clickable')=='true' and any(k in n.get('resource-id','').lower() for k in ['done','confirm','finish'])),None)
        if chosen is None:
            wave=next((n for n in root.iter('node') if n.get('resource-id','').endswith('/alphabet_wave_view')),None)
            if wave is not None and alphabet_gestures<3:
                # The alphabet tutorial is drawn on a canvas without accessible
                # buttons. Follow its animated finger along the right edge.
                x1,y1,x2,y2=map(int,re.findall(r'\d+',wave.get('bounds','')))
                x=x2-20
                adb('shell','input','swipe',x,(y1+y2)//2,x,y1+(y2-y1)*3//4,800)
                alphabet_gestures+=1
                time.sleep(2)
                continue
            time.sleep(3)
            continue
        tap_node(chosen);time.sleep(2)
    else:
        raise AssertionError('Onboarding did not complete; see onboarding screenshots')
def sign(source,target):
    run(BT/'apksigner','sign','--ks',OUT/'test.jks','--ks-pass','pass:android','--key-pass','pass:android','--out',target,source)
def fixture(suffix,label,category,version=1):
    pkg='ca.mlkem.niagara.fixture.'+suffix
    folder=OUT/(suffix+str(version));folder.mkdir(exist_ok=True)
    (folder/'Fixture.java').write_text('package '+pkg+'; public class Fixture extends android.app.Activity {}')
    cat='' if category is None else ' android:appCategory="'+category+'"'
    (folder/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+pkg+'" android:versionCode="'+str(version)+'" android:versionName="1"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="'+label+'"'+cat+'><activity android:name=".Fixture" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>')
    classes=folder/'classes';classes.mkdir(exist_ok=True)
    run('javac','--release','8','-cp',AJ,'-d',classes,folder/'Fixture.java')
    dex=folder/'dex';dex.mkdir(exist_ok=True)
    run(BT/'d8','--min-api','26','--lib',AJ,'--output',dex,*classes.rglob('*.class'))
    unsigned=folder/'unsigned.apk';run(BT/'aapt2','link','-I',AJ,'--manifest',folder/'AndroidManifest.xml','-o',unsigned)
    with zipfile.ZipFile(unsigned,'a') as z: z.write(dex/'classes.dex','classes.dex')
    signed=folder/'fixture.apk';sign(unsigned,signed)
    adb('install','-r',signed)
    return pkg
def database():
    # Never combine a fresh database with a WAL left over from an earlier pull.
    target=Path(tempfile.mkdtemp(prefix='db-',dir=OUT))
    adb('pull','/data/user/0/bitpit.launcher/databases/.',target)
    for path in target.iterdir():
        if path.name.endswith(('-wal','-shm','-journal')): continue
        try:
            db=sqlite3.connect(path)
            names={r[0] for r in db.execute("select name from sqlite_master where type='table'")}
            if {'App','Feed','Shortcut'}<=names: return db
            db.close()
        except sqlite3.DatabaseError: pass
    raise AssertionError('Native launcher database not found')
def check_sorted(pkg,title,hidden=True):
    with closing(database()) as db:
        app=db.execute('select id from App where package_name=?',(pkg,)).fetchone()
        assert app, 'App not discovered: '+pkg
        folders=db.execute('select id from Shortcut where label=? and type=3',(title,)).fetchall()
        print('CHECK',pkg,title,'folders',folders,'shortcuts',db.execute('select id,label,type from Shortcut').fetchall(),flush=True)
        assert len(folders)==1, ('Expected one folder',title,folders)
        fid=folders[0][0]
        assert db.execute('select 1 from Feed where id=? and type=1 and item_type=0 and item_id=?',(fid+(2<<24),app[0])).fetchone(),'App missing from folder'
        assert db.execute('select 1 from Feed where id=4 and type=0 and item_type=2 and item_id=?',(fid,)).fetchone(),'Folder not reachable from Favorites'
        is_hidden=bool(db.execute('select 1 from Feed where id=9 and type=0 and item_type=0 and item_id=?',(app[0],)).fetchone())
        assert is_hidden==hidden, ('Hidden state',is_hidden,hidden)
def stop():
    # Leave Home first. Android immediately restarts a force-stopped default
    # launcher when Home is foreground, racing the database/WAL snapshot.
    adb('shell','am','start','-a','android.settings.SETTINGS');time.sleep(2)
    adb('shell','am','force-stop','bitpit.launcher');time.sleep(1)
    assert not adb('shell','pidof','bitpit.launcher',check=False),'Launcher restarted during database snapshot'
def home():
    adb('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.HOME','-n','bitpit.launcher/.ui.HomeActivity');time.sleep(3)
    assert adb('shell','pidof','bitpit.launcher',check=False), 'Niagara failed to stay running; see logcat.txt'
def main():
    run('keytool','-genkeypair','-keystore',OUT/'test.jks','-storepass','android','-keypass','android','-alias','test','-dname','CN=Disposable Niagara Test','-keyalg','RSA','-validity','2')
    sign(Path(sys.argv[1]),OUT/'launcher.apk')
    for _ in range(3):
        adb('wait-for-device',timeout=20,check=False)
        adb('root',timeout=10,check=False)
        adb('wait-for-device',timeout=20,check=False)
        if adb('shell','id','-u',check=False).strip()=='0': break
        time.sleep(2)
    else:
        raise RuntimeError('The test emulator did not reconnect with root access')
    adb('shell','settings','put','secure','immersive_mode_confirmations','confirmed')
    adb('install',OUT/'launcher.apk')
    adb('shell','cmd','role','add-role-holder','--user','0','android.app.role.HOME','bitpit.launcher')
    print('HOME ROLE',adb('shell','cmd','role','get-role-holders','android.app.role.HOME'),flush=True)
    home();time.sleep(5);onboard();time.sleep(4)
    # The name fallback is intentional: this synthetic Truckful declares no category.
    games=fixture('games','Truckful',None)
    home();time.sleep(8);snapshot('sorted-games');stop();check_sorted(games,'Games')
    home();fixture('games','Truckful',None,2);home();time.sleep(5);stop();check_sorted(games,'Games')
    home();social=fixture('social','Chat fixture','social');home();time.sleep(8);stop();check_sorted(social,'Social')
    # Live per-category toggle in the actual injected settings dialog.
    home();adb('shell','am','start','-n','bitpit.launcher/.settings.SettingsActivity');time.sleep(2)
    root=snapshot('settings');n=find(root,'Productivity');assert n is not None,'Productivity settings unavailable';tap_node(n);time.sleep(2)
    root=snapshot('productivity');tap_node(check_folder_icon(root));time.sleep(2)
    root=snapshot('auto-folders');n=find(root,'Games');assert n is not None,'Games switch missing';tap_node(n);time.sleep(4)
    stop();check_sorted(games,'Games',False);check_sorted(social,'Social',True)
    (OUT/'result.json').write_text(json.dumps({'native_install_sort_hide':True,'truckful_fallback':True,'second_category':True,'update_no_duplicate_folder':True,'live_category_toggle_restores':True,'phone_tested':False,'emulator_api':35},indent=2)+'\n')
    print('PASS: native install, folder persistence, hidden icons, update, Social and live Games toggle',flush=True)
try:
    main()
finally:
    (OUT/'logcat.txt').write_text(adb('logcat','-d','-s','NiagaraAutoFolders:E','AndroidRuntime:E',timeout=10,check=False))
    snapshot('final')
