import collections, hashlib, json, re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
out = root/'local/migration72/offline-sol-terra/chapter-audit'; out.mkdir(parents=True,exist_ok=True)
source = root/'src/main/java/emu/grasscutter/data/ResourceLoader.java'; text=source.read_text(encoding='utf-8-sig')
body=text.split('public static void loadAll() {',1)[1].split('\n    public static void loadResources()',1)[0]
steps=[line.strip() for line in body.splitlines() if re.match(r'^(?:[\w.]+)\([^;]*\);$',line.strip())]
paths=[{'line':text.count('\n',0,m.start())+1,'base':m.group(1),'path':m.group(2)} for m in re.finditer(r'get(Resource|Data|Excel)Path\("([^"]+)"\)',text)]
tree_path=root/'local/migration72/evidence/resource-source-ExcelBinOutput-tree.json'; tree=json.loads(tree_path.read_text(encoding='utf-8'))
assert tree['sha']=='a7767d2efe82980a7dbefe716853e76e9c9a4276'
blobs={x['path']:x['sha'] for x in tree['tree'] if x['type']=='blob'}
gamedata=(root/'src/main/java/emu/grasscutter/data/GameData.java').read_text(encoding='utf-8-sig')
map_fields={m.group(2):m.group(1).strip() for m in re.finditer(r'\bInt2ObjectMap\s*<\s*([^>]+)\s*>\s+(\w+)\s*=',gamedata)}
candidate=root/'local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/ExcelBinOutput'
r3=root/'local/migration72/d02-r3-candidate/ExcelBinOutput'
rows=[]
for p in sorted((root/'src/main/java').rglob('*.java')):
 s=p.read_text(encoding='utf-8-sig'); s=re.sub(r'//[^\n]*|/\*.*?\*/', '', s, flags=re.S)
 for m in re.finditer(r'@ResourceType\s*\((.*?)\)\s*(?:(?:@\w+(?:\([^)]*\))?\s*)|(?:public|final|abstract)\s+)*class\s+(\w+)',s,re.S):
  a,cls=m.groups(); names=re.findall(r'"([^\"]+)"',a); priority=re.search(r'loadPriority\s*=\s*(?:ResourceType\.)?LoadPriority\.(\w+)',a)
  field=cls[0].lower()+cls[1:]+'Map'; entries=[]
  for name in names:
   e={'name':name,'tree_blob':blobs.get(name),'files':[]}
   for origin,base in [('fixed_candidate',candidate),('r3_mixed_fixture',r3),('chapter_audit_fixed_copy',out)]:
    file=base/name
    if not file.is_file(): continue
    b=file.read_bytes(); sha=hashlib.sha256(b).hexdigest(); git=hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
    f={'origin':origin,'sha256':sha,'git_blob':git,'matches_tree':git==e['tree_blob']}
    if name.endswith('.json'):
     try:
      value=json.loads(b); f['json_top_level']=type(value).__name__; f['row_count']=len(value) if isinstance(value,list) else None
      if isinstance(value,list):
       ids=[x['id'] for x in value if isinstance(x,dict) and isinstance(x.get('id'),int) and not isinstance(x.get('id'),bool)]
       counts=collections.Counter(ids); f['literal_id_present']=len(ids); f['literal_id_duplicates']={str(k):v for k,v in sorted(counts.items()) if v>1}
     except (ValueError,UnicodeError) as ex: f['json_error']=str(ex)
    e['files'].append(f)
   entries.append(e)
  rows.append({'class':cls,'source':p.relative_to(root).as_posix(),'priority':priority.group(1) if priority else 'NORMAL','expected_map_field':field,'declared_map_type':map_fields.get(field),'files':entries})
order={'HIGHEST':0,'HIGH':1,'NORMAL':2,'LOW':3,'LOWEST':4}
rows.sort(key=lambda x:(order[x['priority']],x['class']))
manifest={'scope':'source annotations, not runtime reflection; literal id only; unavailable data remains unknown; R3 is mixed fixture, not target provenance', 'loadAll_order':steps,'resource_loader_path_literals':paths,'tree_sha':tree['sha'],'tree_file_sha256':hashlib.sha256(tree_path.read_bytes()).hexdigest(),'annotation_count':len(rows),'annotations':rows}
(out/'static-manifest.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print('annotations',len(rows),'missing_map',sum(x['declared_map_type'] is None for x in rows),'table_refs',sum(len(x['files']) for x in rows),'candidate_local',sum(bool(e['files']) for x in rows for e in x['files']),'absent_tree',sum(e['tree_blob'] is None for x in rows for e in x['files']))
