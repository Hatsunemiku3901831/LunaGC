"""Native map adapters/setters with bounded managed collection storage models."""
import json
from pathlib import Path
try:
    from .client72_emulator import GLOBAL, STREAM
    from .extract_client72 import HASHES
except ImportError:
    from client72_emulator import GLOBAL, STREAM
    from extract_client72 import HASHES


class MapRuntime72:
    CONFIG = {
        4: dict(owner=17961, concrete=314, shared=146, adapter=17964, child=None, key='string', static=0x2FEB0,
                lookup=0x15787CDC0, insert=0x15787D2A0, dictionaryOffset=24, listOffset=16, entriesOffset=48, nodeOffset=24),
        265: dict(owner=66210, concrete=32153, shared=59, adapter=66214, child=56599, key='uint64', static=0x2FEC8,
                  lookup=0x156F6F510, insert=0x156F6F850, dictionaryOffset=24, listOffset=16, entriesOffset=40, nodeOffset=16),
        1218: dict(owner=66210, concrete=32160, shared=1541, adapter=66214, child=67060, key='uint32', static=0x2FEB8,
                   lookup=0x1578B66F0, insert=0x1578B6A20, dictionaryOffset=16, listOffset=24, entriesOffset=24, nodeOffset=40)}

    def __init__(self, graph, directory):
        self.g, self.e = graph, graph.e; e=self.e
        evidence=json.loads((Path(directory)/'map-aot-observations.json').read_text('utf8'))
        if evidence['inputSHA256']!=HASHES:raise ValueError('Map AOT input mismatch')
        self.bindings=evidence['bindings'];self.config={};self.entry_classes={};self.by_entry={};self.static_values={}
        self.reader_class=e.synthetic_type(0x1458AD6D0)
        empty_string=e.alloc();self.static_values[empty_string]=dict(kind='string',hex='')
        self.empty_array=e.alloc(33);e.write(self.empty_array+24,1,'I')
        initialized=self.klass();block=e.alloc();e.write(initialized+0x38,block);e.write(block,self.empty_array)
        self.initialized=initialized
        self.add_stub=e.model_stub(self.append_node)
        for tag, spec in self.CONFIG.items():
            c=dict(spec,tag=tag)
            c['read']=self.binding(c['owner'],c['concrete'],'FNNADLCNGFO')
            # AOT tables explicitly bind the concrete and shared reader to the same body.
            if c['read']!=self.binding(c['owner'],c['shared'],'FNNADLCNGFO'):raise ValueError('Shared map reader differs')
            c['set']=self.binding(c['owner'],c['shared'],'set_Item')
            c['merge']=self.binding(c['adapter'],c['shared'],'MOMMACBEMEK')
            c['ctor']=self.binding(c['adapter'],c['shared'],'.ctor')
            for address in (c['read'],c['set'],c['merge'],c['ctor']):e.allow_unwind(address)
            entry_info, entry_ctx=self.context()
            e.write(entry_ctx+0x10,e.alloc());e.write(entry_ctx+0x18,initialized);e.write(entry_ctx+0x20,initialized)
            klass=self.klass(1024);iface=e.alloc(16);e.write(iface,graph.interface)
            e.write(klass+0x80,iface);e.write(klass+0xC0,1,'H');e.write(klass+0xBE,32,'H')
            e.write(klass+0xE8,c['merge']);e.write(klass+0xE8+32*8,entry_info)
            c['entryClass']=klass;self.entry_classes[klass]=c
            c['info'],ctx=self.context()
            ctor_info=e.alloc();e.write(ctor_info+8,c['ctor'])
            for off in ((0x10,) if tag==4 else (0xC0,)):e.write(ctx+off,klass)
            for off in ((0x18,) if tag==4 else (0xC8,)):e.write(ctx+off,ctor_info)
            e.write(ctx+(0x148 if tag==4 else 0xD0),e.alloc())
            set_info,set_ctx=self.context();c['setInfo']=set_info
            e.write(ctx+(0x140 if tag==4 else 0x20),set_info)
            self.prepare_set_context(set_ctx,tag,initialized)
            codec,key_codec,value_codec=e.alloc(),e.alloc(),e.alloc()
            key_reader=0x147500E70 if tag==4 else 0x1475202D0 if tag==265 else 0x147520110
            delegate=self.delegate(key_reader)
            if tag==4:
                e.write(key_codec+0x30,10,'I');e.write(key_codec+0x28,delegate);e.write(key_codec+0x18,empty_string)
                e.write(value_codec+0x2C,16,'I');e.write(value_codec+0x20,self.delegate(0x147520110))
                e.write(codec+0x10,value_codec);e.write(codec+0x18,key_codec)
            else:
                e.write(key_codec+(0x28 if tag==265 else 0x34),8,'I');e.write(key_codec+0x18,delegate)
                e.write(value_codec+0x10,18,'I');e.write(value_codec+0x18,graph.classes[c['child']])
                e.write(codec+0x10,key_codec);e.write(codec+0x18,value_codec)
            e.write(codec+0x20,tag<<3|2,'I');e.write(GLOBAL+c['static'],codec);c['codec']=codec
            self.config[tag]=c;self.by_entry[c['read']]=c;self.by_entry[c['merge']]=c;self.by_entry[c['set']]=c
            e.models[c['lookup']]=self.lookup;e.models[c['insert']]=self.insert

    def binding(self,owner,instance,method):
        hits={int(r['address'],16) for r in self.bindings if (r['owner'],r['classInstance'],r['method'])==(owner,instance,method)}
        if len(hits)!=1:raise ValueError('Ambiguous/missing map AOT binding')
        return hits.pop()

    def klass(self,size=256):
        p=self.e.alloc(size);self.e.write(p+0xC7,1,'B');self.e.write(p+0xC8,1,'B');return p

    def context(self):
        info=self.e.alloc();klass=self.e.alloc();ctx=self.e.alloc(512)
        self.e.write(info,klass);self.e.write(klass+0x30,ctx);return info,ctx

    def delegate(self,entry):
        p=self.e.alloc();self.e.write(p+0x18,entry);self.e.write(p+0x40,STREAM);return p

    def prepare_set_context(self,ctx,tag,initialized):
        e=self.e;policy=self.klass();e.write(policy+0x38,e.alloc())
        e.write(ctx+(0x40 if tag==4 else 0x50),policy)
        if tag==4:
            meta,sub=self.context();e.write(sub+0x78,e.alloc());e.write(ctx+0xF8,meta)
            meta,sub=self.context();e.write(sub+0x88,e.alloc());e.write(ctx+0x1E0,meta)
        else:
            e.write(ctx+0x68,e.alloc());meta,sub=self.context();e.write(sub+0x88,e.alloc());e.write(ctx+0x70,meta)

    def reset(self):self.dictionaries={};self.linked_lists={}

    def create(self,pointer,field_type):
        e=self.e;args=field_type['arguments'];key=args[0]['kind'];child=args[1].get('typeDefinition')
        matches=[c for c in self.config.values() if c['key']==key and c['child']==child]
        if len(matches)!=1:raise ValueError('Unmodeled map arguments')
        c=matches[0];dictionary,linked=e.alloc(),e.alloc();entries=e.alloc(32+64*24)
        e.write(entries+24,64,'I');e.write(dictionary+c['entriesOffset'],entries)
        e.write(pointer+c['dictionaryOffset'],dictionary);e.write(pointer+c['listOffset'],linked)
        klass=self.klass(512);e.write(klass+0x138 if c['tag']==4 else klass+0x110,self.add_stub);e.write(linked,klass)
        self.dictionaries[dictionary]=dict(config=c,entries=entries,nodes=[],indices={})
        self.linked_lists[linked]=c
        self.g.containers[pointer]['mapDictionary']=dictionary

    def allocate(self,klass):
        if klass==self.reader_class:
            p=self.e.alloc();self.g.streams[p]=None;return p
        c=self.entry_classes.get(klass)
        if c is None:return None
        p=self.e.alloc(512);self.e.write(p,klass);self.g.objects[p]=c['adapter'];return p

    def before(self,address):
        c=self.by_entry.get(address)
        if not c:return
        if address==c['read']:
            if self.e.reg('r8')!=c['codec']:raise ValueError('Wrong map codec')
            self.e.uc.reg_write(self.e.R.UC_X86_REG_R9,c['info'])
        self.g.entered.append(dict(typeIndex=c['adapter'] if address==c['merge'] else c['owner'],entry=hex(address),mapTag=c['tag']))

    def canonical(self,value,c):
        if c['key']=='string':return self.g.values[value]['hex']
        return value&((1<<(64 if c['key']=='uint64' else 32))-1)

    def lookup(self):
        d=self.dictionaries[self.e.reg('rcx')];key=self.canonical(self.e.reg('rdx'),d['config'])
        return d['indices'].get(key,-1)

    def append_node(self):
        c=self.linked_lists[self.e.reg('rcx')];p=self.e.alloc(80)
        self.e.uc.mem_write(p+c['nodeOffset'],bytes(self.e.uc.mem_read(self.e.reg('rdx'),16)));return p

    def insert(self):
        e=self.e;d=self.dictionaries[e.reg('rcx')];key=self.canonical(e.reg('rdx'),d['config']);node=e.reg('r8')
        if key in d['indices'] or len(d['nodes'])>=64:raise ValueError('Synthetic dictionary insertion/capacity')
        index=len(d['nodes']);d['indices'][key]=index;d['nodes'].append(node);e.write(d['entries']+32+index*24+16,node);return 1

    def snapshot(self,dictionary,depth):
        d=self.dictionaries[dictionary];c=d['config'];e=self.e;result={}
        for node in d['nodes']:
            start=node+c['nodeOffset'];key=e.read(start);value=e.read(start+8)
            if c['key']=='string':
                key=bytes.fromhex(self.g.values[key]['hex']).decode('ascii');value=e.read(start+8,'i')
            else:
                key=str(key&((1<<(64 if c['key']=='uint64' else 32))-1));value=self.g.snapshot(value,depth+1)
            result[key]=value
        return result

    def observe_store(self,address,size):
        for d in self.dictionaries.values():
            c=d['config']
            for node in d['nodes']:
                if node+c['nodeOffset']<=address<node+c['nodeOffset']+16:
                    return dict(mapTag=c['tag'],nodeOffset=address-node,size=size,source=hex(self.e.reg('rip')))
        return None
