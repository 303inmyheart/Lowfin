#!/usr/bin/env python3
"""Exercise a real, isolated Jellyfin 12.2 server. Never targets a LAN/production URL."""
import json, pathlib, time, urllib.request, urllib.error, urllib.parse, uuid, shutil
ROOT=pathlib.Path(__file__).resolve().parents[1]
BASE='http://127.0.0.1:18096'
token=''
device='companion-live-test'
def request(path,method='GET',body=None,expected=200):
    header='MediaBrowser Client="Lowfin", Device="Companion Test", DeviceId="'+device+'", Version="0.10.0"'
    if token: header+=', Token="'+token+'"'
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Authorization':header,'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=15) as response:
            data=response.read(); return json.loads(data) if data else None
    except urllib.error.HTTPError as error:
        if error.code==expected: return None
        raise RuntimeError('HTTP '+str(error.code)+' for '+method+' '+path.split('?')[0]) from None
for attempt in range(30):
    try: public=request('/System/Info/Public'); break
    except Exception: time.sleep(1)
else: raise RuntimeError('Isolated test server did not start')
assert public['Version']=='12.2.0'
if not public['StartupWizardCompleted']:
    request('/Startup/Configuration','POST',{'UICulture':'en-US','MetadataCountryCode':'US','PreferredMetadataLanguage':'en'})
    request('/Startup/User')
    request('/Startup/User','POST',{'Name':'lowfin-test','Password':'test-only-lowfin'})
    request('/Startup/Complete','POST',{})
login=request('/Users/AuthenticateByName','POST',{'Username':'lowfin-test','Pw':'test-only-lowfin'})
token=login['AccessToken']; user=login['User']['Id']
cap=request('/Lowfin/Companion/v1/capabilities'); assert cap['available'] and cap['apiVersion']==1
plugins=request('/Plugins'); companion=next(row for row in plugins if row['Name']=='Lowfin Companion'); assert companion['Version']==cap['pluginVersion']+'.0' and companion['HasImage']
with urllib.request.urlopen(BASE+'/Plugins/68abfd24-882d-4937-a104-44d55181b41e/'+companion['Version']+'/Image') as image:
    assert image.read(8)==b'\x89PNG\r\n\x1a\n'
config=request('/System/Configuration'); config['MaxResumePct']=90
request('/System/Configuration','POST',config)
folders=request('/Library/VirtualFolders')
if not any(folder['Name']=='Companion Tests' for folder in folders):
    request('/Library/VirtualFolders?name=Companion%20Tests&collectionType=movies&refreshLibrary=true','POST',{'LibraryOptions':{'PathInfos':[{'Path':str(ROOT/'.tools/companion-test/media')}],'EnableInternetProviders':False,'EnableRealtimeMonitor':False}})
for attempt in range(50):
    items=request('/Users/'+user+'/Items?Recursive=true&IncludeItemTypes=Movie&Fields=MediaSources,Path')['Items']
    fixture=next((row for row in items if row.get('Path')==str(ROOT/'.tools/companion-test/media/Lowfin Sample (2026).mp4')),None)
    if fixture and fixture.get('RunTimeTicks',0)>0: break
    time.sleep(1)
else: raise RuntimeError('Fixture was not scanned')
item=fixture['Id']; duration=fixture['RunTimeTicks']; assert duration>=43190*10000000
path='/Lowfin/Companion/v1/items/'+item
state=request(path)
state=request(path+'/rule','PUT',{'expectedRevision':state['revision'],'rule':{'mode':'RemainingTime','remainingSeconds':120,'percentage':99.5,'persistentResume':True}})
request('/Users/'+user+'/PlayedItems/'+item,'DELETE')
state=request(path); lease=str(uuid.uuid4())
def report(event,sequence,position,lease_id=lease,revision=None):
    body={'leaseId':lease_id,'expectedRevision':state['revision'] if revision is None else revision,'sequence':sequence,'event':event,'playback':{'ItemId':item,'MediaSourceId':item,'PlaySessionId':lease_id,'PositionTicks':position,'IsPaused':False,'CanSeek':True,'PlayMethod':'DirectPlay','EventName':'TimeUpdate'}}
    return request(path+'/playback','POST',body)
report('Start',0,0)
position=11*3600*10000000
ack=report('Progress',1,position); assert ack['accepted'] and not ack['state']['played']
native=request('/Users/'+user+'/Items/'+item)['UserData']; assert not native['Played'] and native['PlaybackPositionTicks']==position
resume=request('/Lowfin/Companion/v1/resume?limit=18')['items']; assert any(row['itemId']==item and row['state']['resumeTicks']==position for row in resume)
sessions=request('/Sessions'); current=next(row for row in sessions if row.get('DeviceId')==device and row.get('NowPlayingItem',{}).get('Id')==item)
assert current['PlayState']['PositionTicks']==position
old=report('Progress',1,2*3600*10000000); assert old['duplicate'] and request(path)['resumeTicks']==position
# The server's normal interpolation must not invent another second while the client has crashed.
time.sleep(2); assert request('/Users/'+user+'/Items/'+item)['UserData']['PlaybackPositionTicks']==position
request('/Users/'+user+'/PlayedItems/'+item,'POST')
report('Progress',2,position+10000000)
assert request(path)['resumeTicks']==0 and request('/Users/'+user+'/Items/'+item)['UserData']['Played']
reconciled=request('/Lowfin/Companion/v1/resume?limit=18&knownItems='+item)
assert not any(row['itemId']==item for row in reconciled['items'])
assert any(row['itemId']==item and row['state']['resumeTicks']==0 and row['state']['manualFence'] for row in reconciled['states'])
report('Stopped',3,position+20000000)
state=request(path); request(path+'/actions','POST',{'expectedRevision':state['revision'],'action':'ResetResume'})
state=request(path); state=request(path+'/rule','PUT',{'expectedRevision':state['revision'],'rule':{'mode':'CustomPercentage','percentage':99.5,'remainingSeconds':120,'persistentResume':True}})
request('/Users/'+user+'/PlayedItems/'+item,'DELETE'); state=request(path)
second=str(uuid.uuid4()); report('Start',0,0,second,state['revision'])
report('Progress',1,duration-120*10000000,second)
assert request(path)['played'] and request(path)['resumeTicks']==duration-120*10000000
report('Stopped',2,duration-120*10000000,second)
# Default restores Jellyfin's native percentage rule without changing the global configuration.
state=request(path); request(path+'/rule','PUT',{'expectedRevision':state['revision'],'rule':{'mode':'Default','percentage':99.5,'remainingSeconds':120,'persistentResume':True}})
request('/Users/'+user+'/PlayedItems/'+item,'DELETE'); state=request(path)
third=str(uuid.uuid4()); report('Start',0,0,third,state['revision']); report('Progress',1,position,third)
assert request('/Users/'+user+'/Items/'+item)['UserData']['Played']
report('Stopped',2,position,third)
assert request('/System/Configuration')['MaxResumePct']==90
# Unconfigured playback is entirely native, not an automatically managed independent-resume session.
state=request(path); request(path+'/rule','PUT',{'expectedRevision':state['revision'],'rule':{'mode':'Default','persistentResume':False}})
state=request(path); assert not state['active']
disabled=report('Start',0,0,str(uuid.uuid4()),state['revision']); assert not disabled['accepted'] and disabled['reason']=='disabled'
request('/Users/'+user+'/PlayedItems/'+item,'DELETE')
native_session=str(uuid.uuid4()); native_body={'ItemId':item,'MediaSourceId':item,'PlaySessionId':native_session,'PositionTicks':0,'CanSeek':True,'PlayMethod':'DirectPlay','IsPaused':False}
request('/Sessions/Playing','POST',native_body); native_body['PositionTicks']=position; request('/Sessions/Playing/Progress','POST',native_body)
assert request('/Users/'+user+'/Items/'+item)['UserData']['Played'] and not request(path)['active']
request('/Sessions/Playing/Stopped','POST',native_body)
# Two real library roots must not be mixed, even when both have independent resume entries.
second_folder=ROOT/'.tools/companion-test/media-second'; second_folder.mkdir(exist_ok=True)
shutil.copy2(ROOT/'.tools/companion-test/media/Lowfin Sample (2026).mp4',second_folder/'Lowfin Hidden (2026).mp4')
folders=request('/Library/VirtualFolders')
if not any(folder['Name']=='Companion Hidden Library' for folder in folders):
    request('/Library/VirtualFolders?name=Companion%20Hidden%20Library&collectionType=movies&refreshLibrary=true','POST',{'LibraryOptions':{'PathInfos':[{'Path':str(second_folder)}],'EnableInternetProviders':False,'EnableRealtimeMonitor':False}})
else: request('/Library/Refresh','POST',{})
for attempt in range(50):
    rows=request('/Users/'+user+'/Items?Recursive=true&IncludeItemTypes=Movie&Fields=Path')['Items']
    hidden=next((row for row in rows if row.get('Path')==str(second_folder/'Lowfin Hidden (2026).mp4')),None)
    if hidden and hidden.get('RunTimeTicks',0)>0: break
    time.sleep(1)
else: raise RuntimeError('Second library fixture was not scanned')
folders=request('/Library/VirtualFolders'); first_library=next(row for row in folders if row['Name']=='Companion Tests')['ItemId']; second_library=next(row for row in folders if row['Name']=='Companion Hidden Library')['ItemId']
second_item=hidden['Id']; second_path='/Lowfin/Companion/v1/items/'+second_item
assert not request(second_path)['active'] or request(second_path).get('rule') is not None
for target in [item,second_item]:
    target_path='/Lowfin/Companion/v1/items/'+target; previous=request(target_path)
    request(target_path+'/rule','PUT',{'expectedRevision':previous['revision'],'rule':{'mode':'RemainingTime','remainingSeconds':120,'persistentResume':True}})
    request('/Users/'+user+'/PlayedItems/'+target,'DELETE')
    request('/Users/'+user+'/Items/'+target+'/UserData','POST',{'PlaybackPositionTicks':7200*10000000,'Played':False})
first_scoped=request('/Lowfin/Companion/v1/resume?limit=18&parentId='+first_library+'&knownItems='+item+','+second_item)
second_scoped=request('/Lowfin/Companion/v1/resume?limit=18&parentId='+second_library+'&knownItems='+item+','+second_item)
assert first_scoped['parentId']==first_library and {row['itemId'] for row in first_scoped['items']}=={item}
assert second_scoped['parentId']==second_library and {row['itemId'] for row in second_scoped['items']}=={second_item}
# The same user's rule is available to a separately authenticated bedroom device.
original_device=device; original_token=token; device='companion-bedroom-test'; token=''
bedroom=request('/Users/AuthenticateByName','POST',{'Username':'lowfin-test','Pw':'test-only-lowfin'}); token=bedroom['AccessToken']
assert bedroom['User']['Id']==user and request(second_path)['effectiveRule']['mode']=='RemainingTime'
device=original_device; token=original_token
# Mirror the web client's library exclusion setting, while retaining the actual saved resume position.
configuration=request('/Users/'+user)['Configuration']; configuration['LatestItemsExcludes']=[second_library]
request('/Users/'+user+'/Configuration','POST',configuration)
excluded=request('/Lowfin/Companion/v1/resume?limit=18&knownItems='+second_item)
assert second_item not in {row['itemId'] for row in excluded['items']}
hidden_state=request(second_path); assert not hidden_state['resumeVisible'] and hidden_state['hasResume'] and hidden_state['resumeTicks']==7200*10000000
explicit_library=request('/Lowfin/Companion/v1/resume?limit=18&parentId='+second_library)
assert {row['itemId'] for row in explicit_library['items']}=={second_item}
configuration['LatestItemsExcludes']=[]; request('/Users/'+user+'/Configuration','POST',configuration)
# Existing authentication is mandatory; no companion-specific token or unauthenticated configuration route.
saved_token=token; token=''; request('/Lowfin/Companion/v1/capabilities',expected=401); token=saved_token
# Cache bootstrap is constant-size, and a native metadata save is delivered as a native PascalCase DTO.
assert 'incrementalCache' in cap['features']
cache_path='/Lowfin/Companion/v1/cache/changes'
baseline=request(cache_path); assert not baseline['reset'] and not baseline['changes'] and baseline['cursor']
request(cache_path+'?limit=21',expected=400)
assert request(cache_path+'?cursor=invalid')['reset']
current=request('/Users/'+user+'/Items/'+item); original_overview=current.get('Overview','')
expected_overview='Lowfin cache journal integration verification '+str(uuid.uuid4())
current['Overview']=expected_overview; request('/Items/'+item,'POST',current,expected=204)
cursor=baseline['cursor']; matched=None
for _ in range(12):
    delta=request(cache_path+'?waitSeconds=1&cursor='+urllib.parse.quote(cursor,safe='')); cursor=delta['cursor']
    if delta['reset']: continue
    for change in delta['changes']:
        if change['item']['Id']==item: matched=change
    if matched: break
assert matched and matched['item']['Type']=='Movie' and matched['item']['Overview']==expected_overview,matched
assert first_library in matched['libraries']
# Changed permissions/preferences invalidate this user's cursor instead of exposing a stale view.
configuration=request('/Users/'+user)['Configuration']; configuration['LatestItemsExcludes']=[second_library]; request('/Users/'+user+'/Configuration','POST',configuration)
assert request(cache_path+'?cursor='+urllib.parse.quote(cursor,safe=''))['reset']
configuration['LatestItemsExcludes']=[]; request('/Users/'+user+'/Configuration','POST',configuration)
current['Overview']=original_overview; request('/Items/'+item,'POST',current,expected=204)
saved_token=token; token=''; request(cache_path,expected=401); token=saved_token
# Admin configuration is separate from the authenticated client API; selected-user access is enforced server-side.
plugin_configuration='/Plugins/68abfd24-882d-4937-a104-44d55181b41e/Configuration'
original_config=request(plugin_configuration); config=dict(original_config)
config.update(ProvidersAllUsers=True,ProviderUserIds=[],OmdbKey='integration-test-secret')
request(plugin_configuration,'POST',config,expected=204)
assert 'serverProviders' in request('/Lowfin/Companion/v1/capabilities')['features']
provider_status=request('/Lowfin/Companion/v1/providers'); assert provider_status['omdb'] and 'integration-test-secret' not in json.dumps(provider_status)
assert request('/Lowfin/Companion/v1/items/'+item+'/ratings?sources=tmdb')['scores']=={}
config['ProvidersAllUsers']=False; config['ProviderUserIds']=[]; request(plugin_configuration,'POST',config,expected=204)
assert 'serverProviders' not in request('/Lowfin/Companion/v1/capabilities')['features']
request('/Lowfin/Companion/v1/providers',expected=403); request('/Lowfin/Companion/v1/items/'+item+'/ratings?sources=tmdb',expected=403)
config['ProviderUserIds']=[user]; request(plugin_configuration,'POST',config,expected=204)
assert 'serverProviders' in request('/Lowfin/Companion/v1/capabilities')['features']
# Frame-based cover tags: only compact facts, no source image mutation or alternative authentication.
assert request('/Lowfin/Companion/v1/capabilities')['imageBadges']['version']==1
config.update(ImageBadgesEnabled=True,ShowDvdBadge=True,ShowSurroundBadge=True)
request(plugin_configuration,'POST',config,expected=204)
assert {k:request('/Lowfin/Companion/v1/capabilities')['imageBadges'][k] for k in ('enabled','dvd','surround','hdr','version')}=={'enabled':True,'dvd':True,'surround':True,'hdr':True,'version':1}
badge_path='/Lowfin/Companion/v1/badges?ids='
for _ in range(30):
    facts=request(badge_path+item+','+item)['items']
    if facts: break
    time.sleep(.5)
assert len(facts)==1 and facts[0]['id']==item and facts[0]['mask']==1 and 'dvd' in facts[0]['tags'],facts
request(badge_path+'invalid',expected=400)
request(badge_path+','.join([item]*65),expected=400)
saved_token=token; token=''; request(badge_path+item,expected=401); token=saved_token
config['ShowDvdBadge']=False; request(plugin_configuration,'POST',config,expected=204)
assert not request('/Lowfin/Companion/v1/capabilities')['imageBadges']['dvd']
assert request(badge_path+item)['items'][0]['mask']==1  # facts stay reusable; presentation switches are independent
config['ImageBadgesEnabled']=False; request(plugin_configuration,'POST',config,expected=204)
assert request(badge_path+item)['items']==[]
config['ImageBadgesEnabled']=True;request(plugin_configuration,'POST',config,expected=204)
admin_token=token; admin_device=device
created=request('/Users/New','POST',{'Name':'companion-permission-'+str(uuid.uuid4())}); other_id=created['Id']
token=''; device='companion-denied-test'; other=request('/Users/AuthenticateByName','POST',{'Username':created['Name'],'Pw':''}); token=other['AccessToken']
request(plugin_configuration,expected=403); request('/Lowfin/Companion/v1/providers',expected=403)
assert request('/Lowfin/Companion/v1/capabilities')['imageBadges']['enabled']
# A regular user may read facts only for media visible through their library policy.
other_token=token;token=admin_token
policy=request('/Users/'+other_id)['Policy'];policy['EnableAllFolders']=False;policy['EnabledFolders']=[second_library]
request('/Users/'+other_id+'/Policy','POST',policy,expected=204)
token=other_token
assert request(badge_path+item)['items']==[]
assert request(badge_path+second_item)['items'][0]['id']==second_item


assert 'serverProviders' not in request('/Lowfin/Companion/v1/capabilities')['features']
token=admin_token; device=admin_device; request('/Users/'+other_id,'DELETE',expected=204); request(plugin_configuration,'POST',original_config,expected=204)
pages=request('/web/ConfigurationPages'); assert any(page['Name']=='lowfincompanion' for page in pages)
summary={'serverVersion':public['Version'],'pluginVersion':cap['pluginVersion'],'runtimeSeconds':duration//10000000,'checks':['discovery','existing-auth','long-video-correction','normal-session-progress','persistent-resume-list','duplicate-report','no-synthetic-progress','manual-watched-fence','custom-percentage','default-policy','global-threshold-preserved','explicit-opt-in','native-playback-without-rule','two-library-scope','cross-device-same-user-rule','jellyfin-resume-exclusions','hidden-resume-retained','plugin-metadata-and-artwork','incremental-cache-bootstrap','native-metadata-event-delivery','cache-permission-cursor-reset','cache-authentication','admin-plugin-configuration','provider-user-allowlist','provider-secret-protection','ordinary-user-config-denied','dashboard-page-registration','cover-tag-auth-and-limits','cover-tag-config-and-facts','cover-tag-disable-and-independent-switches','cover-tag-library-visibility']}
(ROOT/'companion/artifacts').mkdir(parents=True,exist_ok=True)
(ROOT/'companion/artifacts/live-test.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary))
