#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <evntrace.h>
#include <evntcons.h>
#include <evntprov.h>
#include <tdh.h>
#include <TraceLoggingProvider.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "redact.h"

TRACELOGGING_DEFINE_PROVIDER(g_provider, "LunaGC72.Synthetic", (0x27f11d3b,0x9dfc,0x46ce,0xa4,0x01,0x0f,0x26,0x86,0x3c,0x8e,0x99));
static const GUID provider={0x27f11d3b,0x9dfc,0x46ce,{0xa4,0x01,0x0f,0x26,0x86,0x3c,0x8e,0x99}};
static volatile LONG self_count, child_count, bad_id, bad_decode, target_phase;
static DWORD self_pid, child_pid;
static HANDLE event_seen;
static safe_record observed;
static void emit(void) {
    TraceLoggingWrite(g_provider,"SyntheticHttp",TraceLoggingString("sdk-static.mihoyo.com","Host"),
        TraceLoggingString("/query_region_list?token=FAKE_SECRET","Path"),
        TraceLoggingString("GET","HttpMethod"), TraceLoggingUInt32(207,"HttpStatus"),
        TraceLoggingString("HEADER_FAKE_SECRET","SyntheticHeader"));
}
static ULONG property(EVENT_RECORD *r, LPCWSTR name, ULONG size, void *value) {
    PROPERTY_DATA_DESCRIPTOR d={0}; d.PropertyName=(ULONGLONG)(ULONG_PTR)name; d.ArrayIndex=ULONG_MAX;
    return TdhGetProperty(r,0,NULL,1,&d,size,(PBYTE)value);
}
static VOID WINAPI callback(EVENT_RECORD *r) {
    if(memcmp(&r->EventHeader.ProviderId,&provider,sizeof(provider))) return;
    if(r->EventHeader.EventDescriptor.Id!=0) { InterlockedIncrement(&bad_id); if(target_phase==3) SetEvent(event_seen); return; }
    if(r->EventHeader.EventDescriptor.Version!=0) { InterlockedIncrement(&bad_decode); return; }
    if(r->EventHeader.ProcessId==self_pid) InterlockedIncrement(&self_count);
    else if(r->EventHeader.ProcessId==child_pid) InterlockedIncrement(&child_count);
    else { InterlockedIncrement(&bad_decode); return; }
    char host[128]={0},path[128]={0},method[16]={0}; ULONG status=0;
    ULONG a=property(r,L"Host",sizeof(host),host), b=property(r,L"Path",sizeof(path),path);
    ULONG c=property(r,L"HttpMethod",sizeof(method),method),d=property(r,L"HttpStatus",sizeof(status),&status);
    if(a||b||c||d || strcmp(host,"sdk-static.mihoyo.com") || strncmp(path,"/query_region_list?",19) || strcmp(method,"GET") || status!=207) InterlockedIncrement(&bad_decode);
    if(!(a||b||c||d)) observed=sanitize(host,path,method,status,(uint64_t)r->EventHeader.TimeStamp.QuadPart);
    if((target_phase==1 && r->EventHeader.ProcessId==self_pid) || (target_phase==2 && r->EventHeader.ProcessId==child_pid)) SetEvent(event_seen);
}
static DWORD WINAPI consume(LPVOID data) { TRACEHANDLE h=*(TRACEHANDLE*)data; return ProcessTrace(&h,1,NULL,NULL); }
static ULONG enable(TRACEHANDLE session,DWORD pid,int filter_id) {
    EVENT_FILTER_EVENT_ID ids={0}; ids.FilterIn=TRUE; ids.Count=1; ids.Events[0]=0;
    EVENT_FILTER_DESCRIPTOR filters[2]={{0}};
    filters[0].Ptr=(ULONGLONG)(ULONG_PTR)&pid; filters[0].Size=sizeof(pid); filters[0].Type=EVENT_FILTER_TYPE_PID;
    filters[1].Ptr=(ULONGLONG)(ULONG_PTR)&ids; filters[1].Size=sizeof(ids); filters[1].Type=EVENT_FILTER_TYPE_EVENT_ID;
    ENABLE_TRACE_PARAMETERS params={0}; params.Version=ENABLE_TRACE_PARAMETERS_VERSION_2; params.EnableFilterDesc=filters; params.FilterDescCount=filter_id ? 2 : 1;
    return EnableTraceEx2(session,&provider,EVENT_CONTROL_CODE_ENABLE_PROVIDER,TRACE_LEVEL_VERBOSE,0,0,5000,&params);
}
static void names(DWORD id,WCHAR *ready,WCHAR *go,WCHAR *done) {
    swprintf_s(ready,96,L"Local\\LunaGC72ETW-%lu-ready",id);
    swprintf_s(go,96,L"Local\\LunaGC72ETW-%lu-go",id);
    swprintf_s(done,96,L"Local\\LunaGC72ETW-%lu-done",id);
}
static int child(DWORD parent) {
    WCHAR r[96],g[96],d[96]; names(parent,r,g,d);
    HANDLE ready=OpenEventW(EVENT_MODIFY_STATE,FALSE,r),go=OpenEventW(SYNCHRONIZE,FALSE,g),done=OpenEventW(EVENT_MODIFY_STATE,FALSE,d);
    if(!ready||!go||!done) return 2;
    ULONG rc=TraceLoggingRegister(g_provider); if(rc) return 3;
    SetEvent(ready);
    if(WaitForSingleObject(go,10000)!=WAIT_OBJECT_0) return 4;
    emit(); SetEvent(done);
    if(WaitForSingleObject(go,10000)!=WAIT_OBJECT_0) return 5;
    emit(); SetEvent(done);
    TraceLoggingUnregister(g_provider); CloseHandle(ready); CloseHandle(go); CloseHandle(done); return 0;
}
int wmain(int argc,WCHAR **argv) {
    if(argc==3 && !wcscmp(argv[1],L"--child")) return child(wcstoul(argv[2],NULL,10));
    safe_record unknown=sanitize("unknown-host-secret","/private-secret?token=FAKE_SECRET","GET",401,123);
    safe_record known=sanitize("sdk-static.mihoyo.com","/query_region_list?token=FAKE_SECRET","GET",207,124);
    if(unknown.host||unknown.method||unknown.route||unknown.status||unknown.length!=15 || known.host!=1||known.method!=1||known.route!=1||known.status!=207||known.length) return 9;
    self_pid=GetCurrentProcessId(); WCHAR session_name[96],r[96],g[96],d[96],exe[32768],cmd[33000];
    swprintf_s(session_name,96,L"LunaGC72-fixture-%lu-%llu",self_pid,GetTickCount64()); names(self_pid,r,g,d);
    printf("session=%ls\n",session_name);
    HANDLE ready=CreateEventW(NULL,FALSE,FALSE,r),go=CreateEventW(NULL,FALSE,FALSE,g),done=CreateEventW(NULL,FALSE,FALSE,d);
    event_seen=CreateEventW(NULL,FALSE,FALSE,NULL);
    if(!ready||!go||!done||!event_seen) return 10;
    ULONG rc=TraceLoggingRegister(g_provider); if(rc) { printf("register=%lu\n",rc);return 11; }
    GetModuleFileNameW(NULL,exe,32768); swprintf_s(cmd,33000,L"\"%s\" --child %lu",exe,self_pid);
    STARTUPINFOW si={0}; si.cb=sizeof(si); PROCESS_INFORMATION pi={0};
    if(!CreateProcessW(exe,cmd,NULL,NULL,FALSE,CREATE_NO_WINDOW,NULL,NULL,&si,&pi)) { printf("child_error=%lu\n",GetLastError());return 12; }
    child_pid=pi.dwProcessId; CloseHandle(pi.hThread);
    if(WaitForSingleObject(ready,10000)!=WAIT_OBJECT_0) { printf("child_not_ready\n");return 13; }
    size_t bytes=sizeof(EVENT_TRACE_PROPERTIES)+sizeof(session_name); EVENT_TRACE_PROPERTIES *prop=calloc(1,bytes);
    prop->Wnode.BufferSize=(ULONG)bytes; prop->Wnode.Flags=WNODE_FLAG_TRACED_GUID; prop->Wnode.ClientContext=1;
    prop->LogFileMode=EVENT_TRACE_REAL_TIME_MODE; prop->LoggerNameOffset=sizeof(EVENT_TRACE_PROPERTIES);
    TRACEHANDLE session=0; rc=StartTraceW(&session,session_name,prop); printf("start=%lu\n",rc);
    if(rc) { SetEvent(go); WaitForSingleObject(done,1000); SetEvent(go); WaitForSingleObject(pi.hProcess,2000);CloseHandle(pi.hProcess);TraceLoggingUnregister(g_provider);return 14; }
    EVENT_TRACE_LOGFILEW log={0}; log.LoggerName=session_name; log.ProcessTraceMode=PROCESS_TRACE_MODE_REAL_TIME|PROCESS_TRACE_MODE_EVENT_RECORD; log.EventRecordCallback=callback;
    TRACEHANDLE trace=OpenTraceW(&log); HANDLE thread=NULL; REGHANDLE manual=0; int ok=0;
    if(trace==INVALID_PROCESSTRACE_HANDLE) { printf("open_error=%lu\n",GetLastError()); goto cleanup; }
    thread=CreateThread(NULL,0,consume,&trace,0,NULL); if(!thread) goto cleanup;
    rc=EventRegister(&provider,NULL,NULL,&manual); if(rc) goto cleanup;
    rc=enable(session,self_pid,1); printf("enable_self=%lu\n",rc); if(rc) goto cleanup;
    target_phase=1; emit(); if(WaitForSingleObject(event_seen,5000)!=WAIT_OBJECT_0) { printf("self_timeout\n");goto cleanup; }
    EVENT_DESCRIPTOR other={7,0,0,TRACE_LEVEL_INFORMATION,0,0,0}; EventWrite(manual,&other,0,NULL);
    SetEvent(go); if(WaitForSingleObject(done,5000)!=WAIT_OBJECT_0) goto cleanup;
    Sleep(300); LONG filtered_child=child_count, filtered_id=bad_id;
    rc=enable(session,child_pid,1); printf("enable_child=%lu\n",rc); if(rc) goto cleanup;
    target_phase=2; SetEvent(go); if(WaitForSingleObject(done,5000)!=WAIT_OBJECT_0) goto cleanup;
    if(WaitForSingleObject(event_seen,5000)!=WAIT_OBJECT_0) { printf("child_timeout\n");goto cleanup; }
    rc=enable(session,self_pid,0); printf("enable_id_control=%lu\n",rc); if(rc) goto cleanup;
    target_phase=3; EventWrite(manual,&other,0,NULL);
    if(WaitForSingleObject(event_seen,5000)!=WAIT_OBJECT_0) { printf("id_control_timeout\n");goto cleanup; }
    printf("self=%ld child_filtered=%ld child_positive=%ld filtered_id=%ld id_positive=%ld decode_errors=%ld\n",self_count,filtered_child,child_count,filtered_id,bad_id,bad_decode);
    DWORD child_code=~0u;
    if(WaitForSingleObject(pi.hProcess,5000)==WAIT_OBJECT_0) GetExitCodeProcess(pi.hProcess,&child_code);
    printf("child_exit=%lu\n",child_code);
    printf("record=host:%u method:%u route:%u status:%u timestamp:%llu\n",observed.host,observed.method,observed.route,observed.status,(unsigned long long)observed.timestamp);
    ok=child_code==0 && self_count==1 && filtered_child==0 && child_count==1 && filtered_id==0 && bad_id==1 && bad_decode==0 && observed.host==1 && observed.route==1 && observed.status==207 && observed.timestamp!=0;
cleanup:
    SetEvent(go);
    if(manual) EventUnregister(manual);
    if(trace!=INVALID_PROCESSTRACE_HANDLE) CloseTrace(trace);
    ULONG stop=ControlTraceW(session,session_name,prop,EVENT_TRACE_CONTROL_STOP); printf("stop=%lu\n",stop);
    ULONG after=ControlTraceW(0,session_name,prop,EVENT_TRACE_CONTROL_QUERY); printf("query_after_stop=%lu\n",after);
    if(thread) { WaitForSingleObject(thread,5000);CloseHandle(thread); }
    WaitForSingleObject(pi.hProcess,10000); CloseHandle(pi.hProcess);
    TraceLoggingUnregister(g_provider);CloseHandle(ready);CloseHandle(go);CloseHandle(done);CloseHandle(event_seen);free(prop);
    return ok && stop==ERROR_SUCCESS && after==ERROR_WMI_INSTANCE_NOT_FOUND ? 0 : 1;
}
