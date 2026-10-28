#!/usr/bin/env python3
"""Check Companion dashboard against the isolated loopback Jellyfin test fixture only."""
import json,urllib.request,urllib.error,pathlib,os
base='http://127.0.0.1:18096'
token=''
def call(path,body=None,method=None,expected=200,raw=False):
    headers={'Content-Type':'application/json','Authorization':'MediaBrowser Client="Local Test", Device="Companion Test", DeviceId="companion-dashboard-test", Version="0.7.0"'+(', Token="'+token+'"' if token else '')}
    req=urllib.request.Request(base+path,headers=headers,data=None if body is None else json.dumps(body).encode(),method=method)
    try:
        with urllib.request.urlopen(req,timeout=10) as r:
            assert r.status==expected,(path,r.status)
            value=r.read()
            return value if raw else json.loads(value) if value else None
    except urllib.error.HTTPError as e:
        assert e.code==expected,(path,e.code)
plugin='68abfd24-882d-4937-a104-44d55181b41e'
assert call('/Lowfin/Companion/v1/dashboard/mark.woff2',raw=True)[:4]==b'wOF2'
assert b'<svg' in call('/Lowfin/Companion/v1/dashboard/mark.svg',raw=True)
login=call('/Users/AuthenticateByName',{'Username':'lowfin-test','Pw':'test-only-lowfin'},'POST');token=login['AccessToken'];user_id=login['User']['Id']
configpath='/Plugins/'+plugin+'/Configuration'
config=call(configpath)
original_config=json.loads(json.dumps(config))
assert config['DiscordRpc']['Users'] and config['DiscordRpc']['Users'][0]['DiscordToken'].startswith('lowfin-protected:v1:')
assert not config['DiscordRpc']['Enabled'] and not config['DiscordRpc']['UploadArtwork']
assert config['DiscordRpc']['NameMode']=='Default',config['DiscordRpc']['NameMode']
pages=call('/web/ConfigurationPages?enableInMainMenu=true')
page=next(p for p in pages if p['Name']=='lowfincompanion')
assert page['MenuIcon']=='\uefff'
branding=call('/System/Configuration/branding')
original=branding['CustomCss'];assert 'unicode-range:U+EFFF' in original
try:
    branding['CustomCss']=original+'\nbody { --companion-test: 1; }'
    call('/System/Configuration/branding',branding,'POST',204)
    config['DashboardLogoEnabled']=False;call(configpath,config,'POST',204)
    css=call('/System/Configuration/branding')['CustomCss']
    assert 'unicode-range:U+EFFF' not in css and '--companion-test: 1' in css
    config['DashboardLogoEnabled']=True;call(configpath,config,'POST',204)
    css=call('/System/Configuration/branding')['CustomCss']
    assert css.count('unicode-range:U+EFFF')==1 and '--companion-test: 1' in css
    html=call('/web/ConfigurationPage?name=lowfincompanion',raw=True)
    assert b'Discord Rich Presence' in html and b'Cover tags &amp; media index' in html
    assert b'importRpc' not in html
    call('/Lowfin/Companion/v1/discord/import-legacy',method='POST',expected=404)
    config['DiscordRpc']['Enabled']=False
    config['DiscordRpc']['Users']=[{'JellyfinUserId':user_id,'DiscordToken':'native-api-fake-discord-token','Enabled':True}]
    call(configpath,config,'POST',204)
    saved=call(configpath)['DiscordRpc']['Users'][0]['DiscordToken']
    assert saved.startswith('lowfin-protected:v1:') and 'native-api-fake-discord-token' not in saved
    root=pathlib.Path(__file__).resolve().parents[1]
    xml=(root/'.tools/companion-test/data/plugins/configurations/Lowfin.Companion.xml').read_text()
    assert 'native-api-fake-discord-token' not in xml and 'native-migration-fake-discord-token' not in xml
    keys=root/'.tools/companion-test/data/data/lowfin-companion/secret-keys'
    assert list(keys.glob('*.xml'))
    if os.name!='nt':
        assert keys.stat().st_mode&0o777==0o700
        assert all(path.stat().st_mode&0o777==0o600 for path in keys.glob('*.xml'))

finally:
    call(configpath,original_config,'POST',204)
    branding['CustomCss']=original;call('/System/Configuration/branding',branding,'POST',204)
print('PASS: native plugin load, sidebar glyph, embedded assets, defaults, managed CSS preservation, admin configuration removed standalone import endpoint, startup token migration, API/XML encryption and key permissions.')
