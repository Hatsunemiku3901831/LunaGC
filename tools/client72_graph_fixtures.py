"""Controlled wire values and expected results, independent of schema decoding."""
try:
    from .trace_client72_token import varint
except ImportError:
    from trace_client72_token import varint


def scalar(tag,value):return varint(tag<<3)+varint(value&((1<<64)-1))
def message(tag,data):return varint(tag<<3|2)+varint(len(data))+data
def hml(data):return message(470,data)
def branch(case,data,which=3):return message(which,message(case,data))
def bhex(value):return dict(kind='bytes',hex=value)


def fixtures():
    cases=[]
    def add(name,kind,wire,expected,**kw):cases.append(dict(name=name,typeIndex=kind,wire=wire.hex(),expected=expected,**kw))
    add('token_nested_merge',37481,bytes.fromhex('b21c0808ac0210011a0141b21c051002220142'),
        {'KMLEGLIGNPH':dict(JJLJMLEJIIK=300,EBMBKOOKKPD=2,BFOFKNFHPJN='A',HLFMBMBLHME='B')})
    add('token_nested_explicit_zero',37481,message(454,scalar(1,9)+scalar(2,5))+message(454,scalar(1,0))+scalar(14,-7),
        {'KMLEGLIGNPH':dict(EBMBKOOKKPD=5),'LDLIEKOAFBP':-7})
    add('token_list_encodings',37481,bytes.fromhex('c21703018001c017ac02')+scalar(376,0xffffffff),{'GNHGKGCEFIO':[1,128,300,0xffffffff]})
    add('token_list_capacity',37481,b''.join(scalar(376,i) for i in range(64)),{'GNHGKGCEFIO':list(range(64))})
    mdkg=scalar(1,42)+scalar(2,1)+b''.join(message(t,bytes([64+t])) for t in range(3,8))
    add('login_singular_leaves',40503,message(61,mdkg)+message(1337,scalar(1,77)+message(7,b'Z'))+
        message(288,message(1,b'A')+scalar(2,300)+scalar(3,0xffffffff))+message(1314,scalar(1,1)+message(4,b'N')),
        {'FHCEHICKAIP':dict(KDMKODCHHIP=42,FBJOBGOBDIE=True,MFHJIGAMNFK='C',GFCEGMNDCKH='D',DIDEGEOAEJN='E',MCKEMGKBDKG='F',LMMCPBMDBKP='G'),
         'MONEPNFKIML':dict(KDMKODCHHIP=77,LMMCPBMDBKP='Z'),'ACKCOFBGOBG':dict(ACPEPMIINBF='A',LCNMJGGNIND=300,GICIOGGEAFG=0xffffffff),
         'LBDHEONCLNL':dict(JJLJMLEJIIK=1,HLFMBMBLHME='N')})
    add('message_list_append',40503,bytes.fromhex('d23f0a6dffffffff7d2c010000d23f056d01000000f2220408011002f222020803'),
        {'LCILAPOPGFA':[dict(PGEMKKCNEMJ=-1,EMBPDEHFOJK=300),dict(PGEMKKCNEMJ=1)],'FJJDOOJKBDN':[dict(OGAGAEJHBCA=1,ILJLGHBLKMP=2),dict(OGAGAEJHBCA=3)]})
    add('string_map_duplicate_and_order',40503,message(4,scalar(2,-1)+message(1,b'A'))+message(4,message(1,b'B')+scalar(2,9))+
        message(4,message(1,b'A')+scalar(2,-7)),{'GIKCIPFKPIH':{'A':-7,'B':9}})
    big=9007199254740993
    dkf=scalar(1,big)+message(2,b'\0\xff')+message(3,b'B')+scalar(4,300)+scalar(5,0xffffffff)+scalar(6,1)+scalar(21,1)+scalar(22,1)
    andf=scalar(1,0xffffffff)+scalar(2,300)+message(3,b'\0\xff')+scalar(4,1)
    add('message_maps_all_leaf_fields',40503,message(265,scalar(1,big)+message(2,dkf))+message(1218,scalar(1,0xffffffff)+message(2,andf)),
        {'HIHJANHKAMO':{str(big):dict(KMGDOFBGCOK=big,ENLMLLBMNCP=bhex('00ff'),LGAMIBMFBPL=bhex('42'),HENCCAHJOKD=300,LDAODKOLJOM=0xffffffff,CBDIBPCLADH=True,JIMIAMABPAN=True,LMOMIPMKKNI=True)},
         'BMKPNLLLMAO':{str(0xffffffff):dict(AGNINNCKBIG=0xffffffff,LDAODKOLJOM=300,JFMBDNNPKCM=bhex('00ff'),LMOMIPMKKNI=True)}})
    for tag,field,first,last in [(265,'HIHJANHKAMO',scalar(1,big),scalar(4,2)),(1218,'BMKPNLLLMAO',scalar(1,3),scalar(2,4))]:
        value=dict(HENCCAHJOKD=2) if tag==265 else dict(LDAODKOLJOM=4)
        add(f'map_{tag}_duplicate_entry_replaces',40503,message(tag,scalar(1,1)+message(2,first))+message(tag,scalar(1,1)+message(2,last)),{field:{'1':value}})
        add(f'map_{tag}_duplicate_value_replaces',40503,message(tag,scalar(1,1)+message(2,first)+message(2,last)),{field:{'1':value}})
    add('map_defaults',40503,message(4,b'')+message(265,b'')+message(1218,scalar(1,3)),{'GIKCIPFKPIH':{'':0},'HIHJANHKAMO':{'0':{}},'BMKPNLLLMAO':{'3':{}}})
    add('map_duplicate_key_in_entry',40503,message(265,scalar(1,1)+scalar(1,big)+message(2,scalar(4,6))),{'HIHJANHKAMO':{str(big):{'HENCCAHJOKD':6}}})
    add('map_max_key_and_empty_replacement',40503,message(265,scalar(1,(1<<64)-1)+message(2,scalar(4,6))+message(2,b'')),
        {'HIHJANHKAMO':{str((1<<64)-1):{}}})
    alternatives={11:(scalar(1,7)+message(2,varint(big)+varint((1<<64)-1)),dict(EDDLNIEOJEB=7,JBFFIJNPDIL=[big,(1<<64)-1])),
                  12:(scalar(1,big)+scalar(2,1)+scalar(3,0xffffffff),dict(PDNGMNCDJKJ=big,NCHJPGAIOFK=True,CKOMAKMKPCH=0xffffffff)),
                  13:(scalar(1,1)+scalar(2,2),dict(JMHKHAPJADA=1,DOHJIJIJDEO=2)),
                  14:(scalar(1,1)+scalar(2,300),dict(EPMDKHGPKFG=True,IFJDHMHANFP=300))}
    other={11:(scalar(1,3)+scalar(2,4),dict(GOGFALHBANH=3,CDDBAGEFIPP=4)),12:(scalar(1,-1),dict(ILOMBFODOME=-1))}
    for case,(payload,expected) in alternatives.items():
        for second,(payload2,expected2) in other.items():
            add(f'hml_{case}_{second}',40503,hml(scalar(1,1)+branch(case,payload)+branch(second,payload2,2)+scalar(4,-2)),
                {'HIAJLCOHEFP':dict(OKAMAEKCEHL=True,AIAFEGAILDI={f'case_{case}':expected},DAGHAJIBDPA={f'case_{second}':expected2},KFPHCDOLFLO=-2)})
    add('oneof_same_message_merge',40503,hml(branch(12,scalar(1,big)+scalar(2,1)))+hml(branch(12,scalar(2,0)+scalar(3,9))),
        {'HIAJLCOHEFP':{'AIAFEGAILDI':{'case_12':dict(PDNGMNCDJKJ=big,CKOMAKMKPCH=9)}}})
    add('oneof_same_list_append',40503,hml(branch(11,scalar(1,7)+message(2,varint(big))))+hml(branch(11,scalar(2,300))),
        {'HIAJLCOHEFP':{'AIAFEGAILDI':{'case_11':dict(EDDLNIEOJEB=7,JBFFIJNPDIL=[big,300])}}})
    add('oneof_switch_and_return',40503,hml(branch(12,scalar(1,big))+branch(13,scalar(1,7))+branch(12,scalar(3,9))),
        {'HIAJLCOHEFP':{'AIAFEGAILDI':{'case_12':dict(CKOMAKMKPCH=9)}}})
    add('oneof_empty_presence',40503,hml(branch(13,b'')),{'HIAJLCOHEFP':{'AIAFEGAILDI':{'case_13':{}}}})
    add('oneof_pair_merge',40503,hml(branch(13,scalar(1,9))+branch(13,scalar(2,5))),{'HIAJLCOHEFP':{'AIAFEGAILDI':{'case_13':dict(JMHKHAPJADA=9,DOHJIJIJDEO=5)}}})
    add('oneof_bool_merge',40503,hml(branch(14,scalar(2,9))+branch(14,scalar(1,1))),{'HIAJLCOHEFP':{'AIAFEGAILDI':{'case_14':dict(IFJDHMHANFP=9,EPMDKHGPKFG=True)}}})
    add('oneof_list_to_bool_replaces',40503,hml(branch(11,scalar(1,9)+message(2,varint(big)))+branch(14,scalar(1,1))),
        {'HIAJLCOHEFP':{'AIAFEGAILDI':{'case_14':dict(EPMDKHGPKFG=True)}}})
    add('second_oneof_same_message_merge',40503,hml(branch(11,scalar(1,3),2)+branch(11,scalar(2,4),2)),
        {'HIAJLCOHEFP':{'DAGHAJIBDPA':{'case_11':dict(GOGFALHBANH=3,CDDBAGEFIPP=4)}}})
    add('second_oneof_same_enum_overwrites',40503,hml(branch(12,scalar(1,-1),2)+branch(12,scalar(1,2),2)),
        {'HIAJLCOHEFP':{'DAGHAJIBDPA':{'case_12':dict(ILOMBFODOME=2)}}})
    add('second_oneof_same_and_switch',40503,hml(branch(11,scalar(1,3),2)+branch(11,scalar(2,4),2)+branch(12,scalar(1,-3),2)),
        {'HIAJLCOHEFP':{'DAGHAJIBDPA':{'case_12':dict(ILOMBFODOME=-3)}}})
    add('nested_unknown_and_wrong_wire',37481,message(454,scalar(1,300)+message(1,b'X')+scalar(99,7))+scalar(14,-7),
        {'KMLEGLIGNPH':{'JJLJMLEJIIK':300},'LDLIEKOAFBP':-7})
    return cases


def invalid_fixtures():
    return [dict(name='nested_truncated',typeIndex=37481,wire=(varint(454<<3|2)+b'\x05\x08\x01').hex(),error='Native error branch: truncated-message'),
            dict(name='nested_negative_length',typeIndex=37481,wire=(varint(454<<3|2)+varint(0xffffffff)).hex(),error='Native error branch: negative-length'),
            dict(name='child_varint_cannot_read_parent',typeIndex=37481,wire=(message(454,b'\x08\x80')+scalar(14,1)).hex(),error='Truncated or invalid length'),
            dict(name='recursion_limit',typeIndex=40503,wire=hml(branch(13,scalar(1,1))).hex(),recursionLimit=2,error='Native error branch: recursion-limit'),
            dict(name='map_entry_length_exceeds_root',typeIndex=40503,wire=(varint(265<<3|2)+b'\x10\x08\x01').hex(),error='Native error branch: truncated-message'),
            dict(name='map_value_crosses_entry_limit',typeIndex=40503,wire=message(265,b'\x08\x01\x12\x08\x08\x01').hex(),error='Native error branch: truncated-message')]
