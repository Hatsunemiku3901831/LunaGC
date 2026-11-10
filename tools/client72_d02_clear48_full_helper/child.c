#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <bcrypt.h>
#include <intrin.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include "shared.h"
#include "cases.generated.h"
#ifndef C48F_SHARED_HEADER_SHA256_HEX
#error C48F_SHARED_HEADER_SHA256_HEX_must_come_from_build_epoch
#endif
#pragma comment(lib, "bcrypt.lib")

volatile C48FullDescriptor g_c48FullDescriptor;
typedef struct Args { const char *helper, *output, *runText; uint8_t run[16]; const C48FCaseConfig *config; } Args;
typedef struct ApiRecord { const char *api; uint64_t address, size, result, process; DWORD type, protect, oldProtect, error; BOOL ok; } ApiRecord;
typedef struct Setup { ApiRecord rows[12]; unsigned count; uint8_t *arena, *buffer; } Setup;
static int parse_args(int argc, char **argv, Args *a);
static int load_source(const char *path, uint8_t source[904]);
static int sha256(const uint8_t *data, ULONG size, uint8_t digest[32]);
static int parse_hex(const char *s, uint8_t *out, size_t size);
static int host_gate(void);
static int prepare(Setup *s, const C48FCaseConfig *c, const uint8_t source[904]);
static int write_result(const Args *a, const Setup *s, const ApiRecord releases[2], uint64_t rax, const uint8_t before[128], const uint8_t after[128]);
static uint32_t image_rva(const void *image, const void *p) { return (uint32_t)((uintptr_t)p-(uintptr_t)image); }
static uint64_t ptr(const void *p) { return (uint64_t)(uintptr_t)p; }

int main(int argc, char **argv)
{
    Args args; Setup setup = {0}; ApiRecord releases[2]; uint8_t source[904], before[128], after[128];
    const void *image; const NT_TIB *tib; SYSTEM_INFO info; uint64_t result; unsigned i; int released = 1;
    if (!parse_args(argc, argv, &args) || !load_source(args.helper, source)) return 2;
    image = GetModuleHandleW(NULL); tib = (const NT_TIB *)NtCurrentTeb();
    memset((void *)&g_c48FullDescriptor, 0, sizeof(g_c48FullDescriptor));
    memcpy((void *)g_c48FullDescriptor.magic, C48F_DESCRIPTOR_MAGIC, 8);
    g_c48FullDescriptor.version=C48F_VERSION; g_c48FullDescriptor.byteSize=sizeof(g_c48FullDescriptor);
    g_c48FullDescriptor.caseId=args.config->id; memcpy((void *)g_c48FullDescriptor.runId,args.run,16);
    g_c48FullDescriptor.imageBase=ptr(image); g_c48FullDescriptor.pid=GetCurrentProcessId(); g_c48FullDescriptor.tid=GetCurrentThreadId();
    g_c48FullDescriptor.wrapperBeginRva=image_rva(image,&C48FullWrapperBegin); g_c48FullDescriptor.armRva=image_rva(image,&C48FullArm);
    g_c48FullDescriptor.callRva=image_rva(image,&C48FullCall); g_c48FullDescriptor.afterCallRva=image_rva(image,&C48FullAfterCall);
    g_c48FullDescriptor.wrapperEndRva=image_rva(image,&C48FullWrapperEnd); g_c48FullDescriptor.sourceBytes=C48F_SOURCE_BYTES;
    g_c48FullDescriptor.stackLow=ptr(tib->StackLimit); g_c48FullDescriptor.stackHigh=ptr(tib->StackBase);
    if (!parse_hex(C48F_SOURCE_SHA256_HEX,(uint8_t *)g_c48FullDescriptor.sourceSHA256,32) ||
        !parse_hex(C48F_CATALOG_SHA256_HEX,(uint8_t *)g_c48FullDescriptor.catalogSHA256,32) ||
        !parse_hex(C48F_CASES_SHA256_HEX,(uint8_t *)g_c48FullDescriptor.casesSHA256,32)) return 3;
    if (!host_gate()) { fprintf(stderr,"host gate failed cpuidCount=%u leaf0eax=%08x leaf1ecx=%08x xgetbvCount=%u xcr0=%016llx\n",(unsigned)g_c48FullDescriptor.cpuidCount,(unsigned)g_c48FullDescriptor.cpuidLeaf0[0],(unsigned)g_c48FullDescriptor.cpuidLeaf1[2],(unsigned)g_c48FullDescriptor.xgetbvCount,(unsigned long long)g_c48FullDescriptor.xcr0); return 3; }
    GetSystemInfo(&info); g_c48FullDescriptor.pageSize=info.dwPageSize; g_c48FullDescriptor.allocationGranularity=info.dwAllocationGranularity;
    if (info.dwPageSize != 4096 || info.dwAllocationGranularity != 65536) { fprintf(stderr,"unsupported system page geometry\n"); return 3; }
    if (!prepare(&setup,args.config,source)) return 4;
    g_c48FullDescriptor.arenaBase=ptr(setup.arena); g_c48FullDescriptor.arenaReserved=C48F_ARENA_RESERVED;
    g_c48FullDescriptor.helperEntry=ptr(setup.arena+C48F_HELPER_RVA);
    g_c48FullDescriptor.bufferReserveBase=ptr(setup.buffer); g_c48FullDescriptor.bufferReserveBytes=65536;
    g_c48FullDescriptor.bufferPageBase=ptr(setup.buffer+4096); g_c48FullDescriptor.bufferKnownBase=ptr(setup.buffer+4096);
    g_c48FullDescriptor.bufferKnownBytes=128; g_c48FullDescriptor.headBase=ptr(setup.buffer+4096+32);
    g_c48FullDescriptor.destination=ptr(setup.buffer+4096+48);
    memcpy(before,setup.buffer+4096,128);
    g_c48FullDescriptor.childState=C48F_STATE_READY;
    result=C48FullWrapper(setup.arena+C48F_HELPER_RVA,setup.buffer+4096+48);
    g_c48FullDescriptor.continuedToC=1; g_c48FullDescriptor.childState=C48F_STATE_RETURNED;
    memcpy(after,setup.buffer+4096,128);
    memset(releases,0,sizeof(releases));
    for (i=0; i<2; ++i) {
        ApiRecord *r=&releases[i]; void *address=i==0 ? (void *)setup.buffer : (void *)setup.arena;
        r->api="VirtualFree"; r->address=ptr(address); r->size=0; r->type=MEM_RELEASE;
        r->ok=VirtualFree(address,0,MEM_RELEASE); if (!r->ok) { r->error=GetLastError(); released=0; }
    }
    if (!released) fprintf(stderr,"VirtualFree failed: buffer=%lu arena=%lu\n",(unsigned long)releases[0].error,(unsigned long)releases[1].error);
    if (!write_result(&args,&setup,releases,result,before,after)) return 6;
    if (!released) return 5;
    g_c48FullDescriptor.childResultReady=1; g_c48FullDescriptor.childState=C48F_STATE_REPORTED;
    return 0;
}

static int absolute_path(const char *s) { return (s[0] && s[1]==':' && (s[2]=='\\' || s[2]=='/')) || (s[0]=='\\' && s[1]=='\\' && s[2]); }
static int parse_hex(const char *s, uint8_t *out, size_t size)
{
    size_t i; if (strlen(s)!=size*2) return 0;
    for (i=0;i<size*2;i++) { unsigned v; char ch=s[i]; if(ch>='0'&&ch<='9') v=(unsigned)(ch-'0'); else if(ch>='a'&&ch<='f') v=(unsigned)(ch-'a')+10; else return 0; if(!(i&1)) out[i/2]=(uint8_t)(v<<4); else out[i/2]|=(uint8_t)v; } return 1;
}
static int parse_args(int argc, char **argv, Args *a)
{
    if(argc!=9 || strcmp(argv[1],"--helper") || strcmp(argv[3],"--case") || strcmp(argv[5],"--run-id") || strcmp(argv[7],"--output") || !absolute_path(argv[2]) || !absolute_path(argv[8]) || !parse_hex(argv[6],a->run,16)) return 0;
    if(!strcmp(argv[4],"257")) a->config=&g_c48fCases[0]; else if(!strcmp(argv[4],"258")) a->config=&g_c48fCases[1]; else return 0;
    a->helper=argv[2]; a->runText=argv[6]; a->output=argv[8]; return 1;
}
static int sha256(const uint8_t *data, ULONG size, uint8_t digest[32])
{
    BCRYPT_ALG_HANDLE alg=NULL; NTSTATUS status=BCryptOpenAlgorithmProvider(&alg,BCRYPT_SHA256_ALGORITHM,NULL,0); int ok;
    if(status<0) return 0; status=BCryptHash(alg,NULL,0,(PUCHAR)data,size,digest,32); ok=status>=0;
    if(BCryptCloseAlgorithmProvider(alg,0)<0) ok=0; return ok;
}
static int load_source(const char *path, uint8_t source[904])
{
    HANDLE f=CreateFileA(path,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL); LARGE_INTEGER size; DWORD n=0; uint8_t digest[32], expected[32]; int ok;
    if(f==INVALID_HANDLE_VALUE) { fprintf(stderr,"source open error %lu\n",(unsigned long)GetLastError()); return 0; }
    ok=GetFileSizeEx(f,&size) && size.QuadPart==904 && ReadFile(f,source,904,&n,NULL) && n==904;
    if(!CloseHandle(f)) ok=0;
    if(!ok || !sha256(source,904,digest) || !parse_hex(C48F_SOURCE_SHA256_HEX,expected,32) || memcmp(digest,expected,32)) { fprintf(stderr,"source length/read/SHA failure\n"); return 0; } return 1;
}
static int host_gate(void)
{
    int cpu[4]; unsigned i;
    __cpuid(cpu,0); ++g_c48FullDescriptor.cpuidCount; for(i=0;i<4;i++) g_c48FullDescriptor.cpuidLeaf0[i]=(uint32_t)cpu[i];
    if(g_c48FullDescriptor.cpuidLeaf0[0]<1) return 0;
    __cpuid(cpu,1); ++g_c48FullDescriptor.cpuidCount; for(i=0;i<4;i++) g_c48FullDescriptor.cpuidLeaf1[i]=(uint32_t)cpu[i];
    if((g_c48FullDescriptor.cpuidLeaf1[2]&0x1c000000u)!=0x1c000000u) return 0;
    g_c48FullDescriptor.xcr0=C48FullReadXcr0(); ++g_c48FullDescriptor.xgetbvCount;
    if((g_c48FullDescriptor.xcr0&6u)!=6u) return 0; g_c48FullDescriptor.hostGateValid=1; return 1;
}
static void *alloc_record(Setup *s, void *address, SIZE_T size, DWORD type, DWORD protect)
{
    ApiRecord *r=&s->rows[s->count++]; void *p; r->api="VirtualAlloc"; r->address=ptr(address); r->size=(uint64_t)size; r->type=type; r->protect=protect;
    p=VirtualAlloc(address,size,type,protect); r->result=ptr(p); if(!p) {r->error=GetLastError(); fprintf(stderr,"VirtualAlloc error %lu\n",(unsigned long)r->error);} return p;
}
static int protect_record(Setup *s, void *address, SIZE_T size, DWORD protect)
{
    ApiRecord *r=&s->rows[s->count++]; DWORD old; r->api="VirtualProtect"; r->address=ptr(address); r->size=(uint64_t)size; r->protect=protect;
    r->ok=VirtualProtect(address,size,protect,&old); if(r->ok) r->oldProtect=old; else {r->error=GetLastError(); fprintf(stderr,"VirtualProtect error %lu\n",(unsigned long)r->error);} return r->ok!=0;
}
static int prepare(Setup *s, const C48FCaseConfig *c, const uint8_t source[904])
{
    static const uint32_t pages[4]={0x01fdf000u,0x056af000u,0x05eee000u,0x06d19000u}; unsigned i; uint8_t digest[32],expected[32]; ApiRecord *r;
    s->arena=(uint8_t *)alloc_record(s,NULL,(SIZE_T)C48F_ARENA_RESERVED,MEM_RESERVE,PAGE_NOACCESS);
    if(!s->arena || (ptr(s->arena)&0xffffu)) return 0;
    for(i=0;i<4;i++) if(alloc_record(s,s->arena+pages[i],4096,MEM_COMMIT,PAGE_READWRITE)!=s->arena+pages[i]) return 0;
    memcpy(s->arena+C48F_HELPER_RVA,source,904);
    if(!sha256(s->arena+C48F_HELPER_RVA,904,digest) || !parse_hex(C48F_SOURCE_SHA256_HEX,expected,32) || memcmp(digest,expected,32)) { fprintf(stderr,"copied source SHA failure\n"); return 0; }
    for(i=0;i<c->controlCount;i++) memcpy(s->arena+c->controls[i].rva,c->controls[i].bytes,c->controls[i].width);
    if(!protect_record(s,s->arena+pages[0],4096,PAGE_EXECUTE_READ)) return 0;
    for(i=1;i<4;i++) if(!protect_record(s,s->arena+pages[i],4096,PAGE_READONLY)) return 0;
    r=&s->rows[s->count++]; r->api="FlushInstructionCache"; r->process=ptr(GetCurrentProcess()); r->address=ptr(s->arena+C48F_HELPER_RVA); r->size=904;
    r->ok=FlushInstructionCache(GetCurrentProcess(),(void *)(uintptr_t)r->address,904); if(!r->ok) { r->error=GetLastError(); fprintf(stderr,"FlushInstructionCache error %lu\n",(unsigned long)r->error); return 0; }
    s->buffer=(uint8_t *)alloc_record(s,NULL,65536,MEM_RESERVE,PAGE_NOACCESS); if(!s->buffer || (ptr(s->buffer)&0xffffu)) return 0;
    if(alloc_record(s,s->buffer+4096,4096,MEM_COMMIT,PAGE_READWRITE)!=s->buffer+4096) return 0;
    memcpy(s->buffer+4096,c->initial128,128); return 1;
}
typedef struct Json { char text[12000]; size_t used; int ok; } Json;
static void append(Json *j, const char *format, ...)
{
    va_list ap; int n; if(!j->ok) return; va_start(ap,format); n=vsnprintf(j->text+j->used,sizeof(j->text)-j->used,format,ap); va_end(ap);
    if(n<0 || (size_t)n>=sizeof(j->text)-j->used) { j->ok=0; return; } j->used+=(size_t)n;
}
static void append_bytes(Json *j, const uint8_t *p, size_t n)
{
    static const char hex[]="0123456789abcdef"; size_t i; if(j->used+2*n>=sizeof(j->text)) {j->ok=0; return;} for(i=0;i<n;i++) {j->text[j->used++]=hex[p[i]>>4]; j->text[j->used++]=hex[p[i]&15];} j->text[j->used]=0;
}
static void append_record(Json *j, const ApiRecord *r)
{
    if(!strcmp(r->api,"VirtualAlloc")) {
        append(j,"{\"api\":\"VirtualAlloc\",\"address\":\"%016llx\",\"size\":\"%016llx\",\"allocationType\":%lu,\"protect\":%lu,\"result\":\"%016llx\",\"lastErrorIfNull\":",(unsigned long long)r->address,(unsigned long long)r->size,(unsigned long)r->type,(unsigned long)r->protect,(unsigned long long)r->result);
        if(r->result) append(j,"null}"); else append(j,"%lu}",(unsigned long)r->error);
    } else if(!strcmp(r->api,"VirtualProtect")) {
        append(j,"{\"api\":\"VirtualProtect\",\"address\":\"%016llx\",\"size\":\"%016llx\",\"newProtect\":%lu,\"BOOL\":%s,\"lastErrorIfFalse\":",(unsigned long long)r->address,(unsigned long long)r->size,(unsigned long)r->protect,r->ok?"true":"false");
        if(r->ok) append(j,"null,\"oldProtect\":%lu}",(unsigned long)r->oldProtect); else append(j,"%lu,\"oldProtect\":null}",(unsigned long)r->error);
    } else if(!strcmp(r->api,"FlushInstructionCache")) {
        append(j,"{\"api\":\"FlushInstructionCache\",\"process\":\"%016llx\",\"address\":\"%016llx\",\"size\":\"%016llx\",\"BOOL\":%s,\"lastErrorIfFalse\":",(unsigned long long)r->process,(unsigned long long)r->address,(unsigned long long)r->size,r->ok?"true":"false");
        if(r->ok) append(j,"null}"); else append(j,"%lu}",(unsigned long)r->error);
    } else {
        append(j,"{\"api\":\"VirtualFree\",\"address\":\"%016llx\",\"size\":\"%016llx\",\"freeType\":%lu,\"BOOL\":%s,\"lastErrorIfFalse\":",(unsigned long long)r->address,(unsigned long long)r->size,(unsigned long)r->type,r->ok?"true":"false");
        if(r->ok) append(j,"null}"); else append(j,"%lu}",(unsigned long)r->error);
    }
}
static int write_result(const Args *a, const Setup *s, const ApiRecord releases[2], uint64_t rax, const uint8_t before[128], const uint8_t after[128])
{
    Json j={{0},0,1}; unsigned i; HANDLE f; DWORD written=0; BOOL wrote, flushed, closed; DWORD writeError=0, flushError=0, closeError=0;
    append(&j,"{\"schema\":\"c48-full-helper-child-result-v1\",\"caseId\":%u,\"runId\":\"%s\",\"pid\":%lu,\"tid\":%lu,",(unsigned)a->config->id,a->runText,(unsigned long)g_c48FullDescriptor.pid,(unsigned long)g_c48FullDescriptor.tid);
    append(&j,"\"source904SHA256\":\"%s\",\"catalogSHA256\":\"%s\",\"casesSHA256\":\"%s\",",C48F_SOURCE_SHA256_HEX,C48F_CATALOG_SHA256_HEX,C48F_CASES_SHA256_HEX);
    append(&j,"\"hostGateValid\":true,\"cpuidCount\":%u,\"xgetbvCount\":%u,\"xcr0\":\"%016llx\",\"cpuidLeaf0\":[%u,%u,%u,%u],\"cpuidLeaf1\":[%u,%u,%u,%u],",(unsigned)g_c48FullDescriptor.cpuidCount,(unsigned)g_c48FullDescriptor.xgetbvCount,(unsigned long long)g_c48FullDescriptor.xcr0,(unsigned)g_c48FullDescriptor.cpuidLeaf0[0],(unsigned)g_c48FullDescriptor.cpuidLeaf0[1],(unsigned)g_c48FullDescriptor.cpuidLeaf0[2],(unsigned)g_c48FullDescriptor.cpuidLeaf0[3],(unsigned)g_c48FullDescriptor.cpuidLeaf1[0],(unsigned)g_c48FullDescriptor.cpuidLeaf1[1],(unsigned)g_c48FullDescriptor.cpuidLeaf1[2],(unsigned)g_c48FullDescriptor.cpuidLeaf1[3]);
    append(&j,"\"arenaBase\":\"%016llx\",\"arenaReserved\":\"%016llx\",\"bufferReserveBase\":\"%016llx\",\"headBase\":\"%016llx\",\"destination\":\"%016llx\",\"helperCallCount\":1,\"returnedRAX\":\"%016llx\",\"continuedToC\":true,\"initial128Hex\":\"",(unsigned long long)g_c48FullDescriptor.arenaBase,(unsigned long long)g_c48FullDescriptor.arenaReserved,(unsigned long long)g_c48FullDescriptor.bufferReserveBase,(unsigned long long)g_c48FullDescriptor.headBase,(unsigned long long)g_c48FullDescriptor.destination,(unsigned long long)rax);
    append_bytes(&j,before,128); append(&j,"\",\"final128Hex\":\""); append_bytes(&j,after,128); append(&j,"\",\"setupApiRecords\":[");
    for(i=0;i<s->count;i++) {if(i) append(&j,","); append_record(&j,&s->rows[i]);} append(&j,"],\"releaseApiRecords\":[");
    for(i=0;i<2;i++) {if(i) append(&j,","); append_record(&j,&releases[i]);} append(&j,"]}\n"); if(!j.ok) return 0;
    f=CreateFileA(a->output,GENERIC_WRITE,0,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL); if(f==INVALID_HANDLE_VALUE) {fprintf(stderr,"result create error %lu\n",(unsigned long)GetLastError()); return 0;}
    wrote=WriteFile(f,j.text,(DWORD)j.used,&written,NULL); if(!wrote) writeError=GetLastError();
    flushed=FlushFileBuffers(f); if(!flushed) flushError=GetLastError();
    closed=CloseHandle(f); if(!closed) closeError=GetLastError();
    if(!wrote || written!=(DWORD)j.used || !flushed || !closed) {fprintf(stderr,"result I/O failure write=%lu bytes=%lu/%lu flush=%lu close=%lu\n",(unsigned long)writeError,(unsigned long)written,(unsigned long)j.used,(unsigned long)flushError,(unsigned long)closeError); return 0;} return 1;
}
