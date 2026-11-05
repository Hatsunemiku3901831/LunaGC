"""RSA fixture encoding/signing for offline native-client interoperability tests.

Private material stays in memory. This module does not create production keys,
modify a running client, or replace a signature verification decision.
"""
import base64
import hashlib


def tlv(data,offset=0):
    if offset+2>len(data):raise ValueError('Truncated DER item')
    tag=data[offset];length=data[offset+1];offset+=2
    if length&128:
        count=length&127
        if not 1<=count<=4 or offset+count>len(data):raise ValueError('Invalid DER length')
        length=int.from_bytes(data[offset:offset+count],'big');offset+=count
    end=offset+length
    if end>len(data):raise ValueError('Truncated DER payload')
    return tag,data[offset:end],end


def private_numbers(data):
    """Accept PKCS#1 or PKCS#8 RSA fixture bytes at the file/input boundary."""
    tag,body,end=tlv(data)
    if tag!=0x30 or end!=len(data):raise ValueError('Expected one private-key sequence')
    tag,version,pos=tlv(body)
    if tag!=2 or version!=b'\0':raise ValueError('Unsupported private-key version')
    tag,_,end=tlv(body,pos)
    if tag==0x30:
        tag,inner,last=tlv(body,end)
        if tag!=4 or last!=len(body):raise ValueError('Expected PKCS8 private key')
        return private_numbers(inner)
    values=[0]
    while pos<len(body):
        tag,value,pos=tlv(body,pos)
        if tag!=2 or not value or value[0]&128:raise ValueError('Invalid private-key integer')
        values.append(int.from_bytes(value,'big'))
    if len(values)!=9 or values[1]!=values[4]*values[5]:raise ValueError('Invalid two-prime RSA fixture')
    return values


def der_item(tag,payload):
    size=len(payload)
    length=bytes([size]) if size<128 else b'\x82'+size.to_bytes(2,'big')
    return bytes([tag])+length+payload


def public_der(numbers):
    integers=[]
    for value in numbers[1:3]:
        data=value.to_bytes((value.bit_length()+7)//8,'big')
        integers.append(der_item(2,(b'\0' if data[0]&128 else b'')+data))
    return der_item(0x30,b''.join(integers))


def public_pem(der):
    encoded=base64.b64encode(der)
    lines=[encoded[i:i+64] for i in range(0,len(encoded),64)]
    return b'-----BEGIN RSA PUBLIC KEY-----\n'+b'\n'.join(lines)+b'\n-----END RSA PUBLIC KEY-----\n'


def sign_sha256(numbers,message):
    width=(numbers[1].bit_length()+7)//8
    digest=bytes.fromhex('3031300d060960864801650304020105000420')+hashlib.sha256(message).digest()
    block=b'\0\1'+b'\xff'*(width-len(digest)-3)+b'\0'+digest
    return pow(int.from_bytes(block,'big'),numbers[3],numbers[1]).to_bytes(width,'big')


def encrypt_seed(numbers,plain):
    width=(numbers[1].bit_length()+7)//8
    if len(plain)>width-11:raise ValueError('Fixture plaintext exceeds PKCS1 block')
    # Fixed nonzero padding is deliberate fixture data, never a production RNG.
    block=b'\0\2'+b'\x42'*(width-len(plain)-3)+b'\0'+plain
    return pow(int.from_bytes(block,'big'),numbers[2],numbers[1]).to_bytes(width,'big')
