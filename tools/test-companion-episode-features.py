#!/usr/bin/env python3
"""Real metadata/count validation against the isolated local Jellyfin 12.2 fixture only."""
import json, pathlib, subprocess, time, urllib.request, urllib.parse, urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[1]
BASE='http://127.0.0.1:18096'
token=''
def call(path,method='GET',body=None,expected=200):
    auth='MediaBrowser Client="Lowfin", Device="Episode QA", DeviceId="episode-qa", Version="1.2.5"'
    if token: auth+=', Token="'+token+'"'
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),method=method,headers={'Authorization':auth,'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=15) as response:
            raw=response.read();return (json.loads(raw) if raw else None),response.headers
    except urllib.error.HTTPError as error:
        if error.code==expected:return None,error.headers
        raise RuntimeError('Unexpected HTTP '+str(error.code)+' for '+path.split('?')[0]) from None
login,_=call('/Users/AuthenticateByName','POST',{'Username':'lowfin-test','Pw':'test-only-lowfin'});token=login['AccessToken'];user=login['User']['Id']
directory=ROOT/'.tools/companion-test/media-mixed/Lowfin Mixed QA (2026)/Season 01';directory.mkdir(parents=True,exist_ok=True)
for index,size in [(1,'720x576'),(2,'720x480'),(3,'1280x720')]:
    file=directory/('Lowfin Mixed QA - S01E%02d.mp4'%index)
    if not file.exists():subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=black:s='+size+':r=1','-t','2','-c:v','libx264','-preset','ultrafast','-an',str(file)],check=True)
folders,_=call('/Library/VirtualFolders')
if not any(x['Name']=='Mixed Quality QA' for x in folders):
    call('/Library/VirtualFolders?name=Mixed%20Quality%20QA&collectionType=tvshows&refreshLibrary=true','POST',{'LibraryOptions':{'PathInfos':[{'Path':str(directory.parents[1])}],'EnableInternetProviders':False,'EnableRealtimeMonitor':False}})
call('/Library/Refresh','POST',{},expected=204)
def items(kind):return call('/Users/'+user+'/Items?Recursive=true&IncludeItemTypes='+kind+'&Fields=MediaStreams,Path,ProviderIds')[0]['Items']
for _ in range(50):
    episodes=[x for x in items('Episode') if x.get('Path','').startswith(str(directory))]
    if len(episodes)==3 and all(x.get('MediaStreams') for x in episodes):break
    time.sleep(.5)
else:raise AssertionError('Mixed-quality fixture was not scanned')
series=next(x for x in items('Series') if x['Id']==episodes[0]['SeriesId']);season=next(x for x in items('Season') if x['Id']==episodes[0]['SeasonId'])
path='/Plugins/68abfd24-882d-4937-a104-44d55181b41e/Configuration';original,_=call(path);config=dict(original);config.update(ImageBadgesEnabled=True,ShowDvdBadge=True,ShowSurroundBadge=True,ProvidersAllUsers=True,OmdbKey='',TmdbKey='',MdblistKey='')
try:
    call(path,'POST',config,expected=204)
    ids=[series['Id'],season['Id']]+[x['Id'] for x in episodes]
    for _ in range(40):
        result,_=call('/Lowfin/Companion/v1/badges?ids='+','.join(ids));masks={x['id']:x['mask'] for x in result['items']}
        if all(id in masks for id in ids): break
        time.sleep(.5)
    else: raise AssertionError('Background tag index did not populate')
    assert masks[series['Id']]==1 and masks[season['Id']]==1,masks
    for episode in episodes:
        video=next(x for x in episode['MediaStreams'] if x['Type']=='Video');assert masks[episode['Id']]==(1 if video['Width']<=1024 else 0)
        scores,headers=call('/Lowfin/Companion/v1/items/'+episode['Id']+'/ratings?sources=omdb,tmdb')
        assert scores['scores']=={} and headers.get('Cache-Control')=='no-store'
    status,_=call('/Lowfin/Companion/v1/badges/status');assert status['ready'] and status['items']>=5
    tasks,_=call('/ScheduledTasks');task=next(t for t in tasks if t['Key']=='LowfinCompanionBadgeIndex');assert any(t.get('IntervalTicks')==24*3600*10000000 for t in task['Triggers'])
    saved=token;token='';call('/Lowfin/Companion/v1/items/'+episodes[0]['Id']+'/ratings',expected=401);token=saved
finally:call(path,'POST',original,expected=204)
summary={'server':'12.2.0','plugin':'0.5.0','episodes':3,'sd':2,'hd':1,'seriesDvd':True,'seasonDvd':True,'episodeRatingsRouteAndNoStore':True,'noExternalCredentialsUsed':True}
(ROOT/'companion/artifacts/episode-features-live-test.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary))
