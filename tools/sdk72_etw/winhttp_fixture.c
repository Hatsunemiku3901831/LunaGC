#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <winhttp.h>
#include <evntrace.h>
#include <evntcons.h>
#include <evntprov.h>
#include <tdh.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static const GUID provider={0x64de121b,0x5f08,0x5853,{0xab,0x48,0x77,0x58,0xf2,0xea,0x2d,0xd3}};
static volatile LONG event_count, version0, version1, wrong_pid, wrong_id, unknown_version, tdh_errors, schema_errors, path_query, field_header, field_body, code207, code208;
static DWORD owner_pid; static SOCKET listener=INVALID_SOCKET; static volatile LONG served, server_errors, request_checks, listener_close_error=-1;
static int contains(const BYTE *b, ULONG n, const char *s) {
    size_t m=strlen(s); if(n<m) return 0;
    for(ULONG i=0;i<=n-m;i++) if(!memcmp(b+i,s,m)) return 1;
    return 0;
}
static int contains_wide(const BYTE *b,ULONG n,const char *s) {
    size_t m=strlen(s);if(n<m*2)return 0;
    for(ULONG i=0;i<=n-m*2;i++) {size_t j=0;while(j<m && b[i+j*2]==(BYTE)s[j] && b[i+j*2+1]==0)j++;if(j==m)return 1;}
    return 0;
}
static void inspect_field(EVENT_RECORD *r,LPCWSTR field) {
    PROPERTY_DATA_DESCRIPTOR p={0}; p.PropertyName=(ULONGLONG)(ULONG_PTR)field;p.ArrayIndex=ULONG_MAX;
    ULONG n=0,rc=TdhGetPropertySize(r,0,NULL,1,&p,&n); if(rc!=ERROR_SUCCESS) {InterlockedIncrement(&tdh_errors);return;}
    if(n>8192) {InterlockedIncrement(&tdh_errors);return;}
    BYTE data[8193]={0}; rc=TdhGetProperty(r,0,NULL,1,&p,n,data); if(rc!=ERROR_SUCCESS) {InterlockedIncrement(&tdh_errors);return;}
    if(!wcscmp(field,L"Path") && contains(data,n,"FAKE_QUERY")) InterlockedIncrement(&path_query);
    if(contains(data,n,"FAKE_HEADER")||contains_wide(data,n,"FAKE_HEADER")) InterlockedIncrement(&field_header);
    if(contains(data,n,"FAKE_BODY")||contains_wide(data,n,"FAKE_BODY")) InterlockedIncrement(&field_body);
    if(!wcscmp(field,L"HttpStatus") && n==sizeof(ULONG)) { ULONG x;memcpy(&x,data,sizeof(x));if(x==207)InterlockedIncrement(&code207);if(x==208)InterlockedIncrement(&code208); }
}
static VOID WINAPI callback(EVENT_RECORD *r) {
    if(memcmp(&r->EventHeader.ProviderId,&provider,sizeof(provider))) return;
    if(r->EventHeader.ProcessId!=owner_pid) {InterlockedIncrement(&wrong_pid);return;}
    if(r->EventHeader.EventDescriptor.Id!=0) {InterlockedIncrement(&wrong_id);return;}
    BYTE metadata[8192]={0};ULONG n=sizeof(metadata),rc=TdhGetEventInformation(r,0,NULL,(PTRACE_EVENT_INFO)metadata,&n);
    if(rc!=ERROR_SUCCESS) {InterlockedIncrement(&tdh_errors);return;}
    UCHAR version=r->EventHeader.EventDescriptor.Version;
    if(version>1) {InterlockedIncrement(&unknown_version);return;}
    InterlockedIncrement(&event_count); if(version==0)InterlockedIncrement(&version0);else InterlockedIncrement(&version1);
    LPCWSTR names[]={L"ProcessName",L"ModuleName",L"EventGuid",L"HttpProtVersion",L"Transport",L"Host",L"Path",L"HttpMethod",L"HttpStatus",L"FaultReason",L"FaultCode",L"ErrorCode",L"ParseBuffer"};
    USHORT types[]={TDH_INTYPE_UNICODESTRING,TDH_INTYPE_UNICODESTRING,TDH_INTYPE_GUID,TDH_INTYPE_UINT32,TDH_INTYPE_UNICODESTRING,TDH_INTYPE_ANSISTRING,TDH_INTYPE_ANSISTRING,TDH_INTYPE_ANSISTRING,TDH_INTYPE_UINT32,TDH_INTYPE_UNICODESTRING,TDH_INTYPE_UNICODESTRING,TDH_INTYPE_UINT32,TDH_INTYPE_UINT64};
    PTRACE_EVENT_INFO info=(PTRACE_EVENT_INFO)metadata;
    if(info->TopLevelPropertyCount!=(ULONG)(13+version))InterlockedIncrement(&schema_errors);
    for(unsigned i=0;i<sizeof(names)/sizeof(names[0]);i++) {
        unsigned j=i+(version && i>=2);if(j>=info->TopLevelPropertyCount)continue;
        EVENT_PROPERTY_INFO *item=&info->EventPropertyInfoArray[j];
        if(wcscmp((LPCWSTR)(metadata+item->NameOffset),names[i]) || (item->Flags&PropertyStruct) || item->nonStructType.InType!=types[i])InterlockedIncrement(&schema_errors);
    }
    if(version==1 && info->TopLevelPropertyCount>2) {EVENT_PROPERTY_INFO *item=&info->EventPropertyInfoArray[2];if(wcscmp((LPCWSTR)(metadata+item->NameOffset),L"SessionGuid") || (item->Flags&PropertyStruct) || item->nonStructType.InType!=TDH_INTYPE_GUID)InterlockedIncrement(&schema_errors);}
    for(unsigned i=0;i<sizeof(names)/sizeof(names[0]);i++) inspect_field(r,names[i]);
    if(version==1) inspect_field(r,L"SessionGuid");
}
static DWORD WINAPI consume(LPVOID p) { TRACEHANDLE t=*(TRACEHANDLE*)p;return ProcessTrace(&t,1,NULL,NULL); }
static DWORD WINAPI serve(LPVOID unused) {
    (void)unused;
    for(int i=0;i<2;i++) {
        SOCKET s=accept(listener,NULL,NULL); if(s==INVALID_SOCKET) {InterlockedIncrement(&server_errors);break;}
        DWORD timeout=5000;setsockopt(s,SOL_SOCKET,SO_RCVTIMEO,(const char*)&timeout,sizeof(timeout));
        char buf[8192]={0};int used=0;char *end=NULL;
        while(used<(int)sizeof(buf)-1 && !(end=strstr(buf,"\r\n\r\n"))) {int n=recv(s,buf+used,(int)sizeof(buf)-1-used,0);if(n<=0)break;used+=n;}
        if(end && i==1) {while(!strstr(end+4,"FAKE_BODY") && used<(int)sizeof(buf)-1) {int n=recv(s,buf+used,(int)sizeof(buf)-1-used,0);if(n<=0)break;used+=n;}}
        if(!end || !strstr(buf,"FAKE_QUERY") || !strstr(buf,"FAKE_HEADER") || (i==1 && !strstr(buf,"FAKE_BODY"))) InterlockedIncrement(&server_errors);
        else InterlockedIncrement(&request_checks);
        const char *response=i==0?"HTTP/1.1 207 Multi-Status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n":"HTTP/1.1 208 Already Reported\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        if(send(s,response,(int)strlen(response),0)!=(int)strlen(response)) InterlockedIncrement(&server_errors);
        closesocket(s);InterlockedIncrement(&served);
    }
    int close_rc=closesocket(listener);listener_close_error=close_rc==0?0:WSAGetLastError();listener=INVALID_SOCKET;return 0;
}
static int request(HINTERNET conn,int post) {
    LPCWSTR path=post?L"/query_cur_region/os_usa?test=FAKE_QUERY":L"/query_region_list?test=FAKE_QUERY";
    HINTERNET req=WinHttpOpenRequest(conn,post?L"POST":L"GET",path,NULL,WINHTTP_NO_REFERER,WINHTTP_DEFAULT_ACCEPT_TYPES,0);
    if(!req) {printf("open_error=%lu\n",GetLastError());return 0;}
    char body[]="FAKE_BODY"; LPCWSTR header=L"X-Synthetic: FAKE_HEADER\r\n";
    BOOL ok=WinHttpSendRequest(req,header,(DWORD)-1,post?body:WINHTTP_NO_REQUEST_DATA,post?(DWORD)strlen(body):0,post?(DWORD)strlen(body):0,0);
    if(ok) ok=WinHttpReceiveResponse(req,NULL);
    DWORD status=0,size=sizeof(status);if(ok)ok=WinHttpQueryHeaders(req,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&status,&size,WINHTTP_NO_HEADER_INDEX);
    printf("request_%s_ok=%d status=%lu error=%lu\n",post?"post":"get",ok?1:0,status,ok?0:GetLastError());
    WinHttpCloseHandle(req);return ok && status==(post?208u:207u);
}
int main(void) {
    int result=1, trace_started=0,ws_started=0;HANDLE server_thread=NULL,consumer=NULL; HINTERNET session_http=NULL,conn=NULL;SOCKADDR_IN addr={0};
    TRACEHANDLE session=0,trace=INVALID_PROCESSTRACE_HANDLE;EVENT_TRACE_PROPERTIES *prop=NULL; WCHAR name[96];
    owner_pid=GetCurrentProcessId();swprintf_s(name,96,L"LunaGC72-winhttp-fixture-%lu-%llu",owner_pid,GetTickCount64());printf("session=%ls\n",name);
    WSADATA w;int wrc=WSAStartup(MAKEWORD(2,2),&w);if(wrc) {printf("wsa_error=%d\n",wrc);goto cleanup;}ws_started=1;
    listener=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);if(listener==INVALID_SOCKET)goto cleanup;
    addr.sin_family=AF_INET;addr.sin_addr.s_addr=htonl(INADDR_LOOPBACK);addr.sin_port=0;
    if(bind(listener,(SOCKADDR*)&addr,sizeof(addr)) || listen(listener,2)) {printf("listen_error=%d\n",WSAGetLastError());goto cleanup;}
    int addr_len=sizeof(addr);if(getsockname(listener,(SOCKADDR*)&addr,&addr_len))goto cleanup;
    unsigned short port=ntohs(addr.sin_port);if(port==8088||port==22101||port==27017||port==27027) {printf("excluded_port\n");goto cleanup;}
    printf("loopback_port=%hu\n",port);
    size_t bytes=sizeof(EVENT_TRACE_PROPERTIES)+sizeof(name);prop=calloc(1,bytes);if(!prop)goto cleanup;
    prop->Wnode.BufferSize=(ULONG)bytes;prop->Wnode.Flags=WNODE_FLAG_TRACED_GUID;prop->Wnode.ClientContext=1;prop->LogFileMode=EVENT_TRACE_REAL_TIME_MODE;prop->LoggerNameOffset=sizeof(EVENT_TRACE_PROPERTIES);
    ULONG rc=StartTraceW(&session,name,prop);printf("start=%lu\n",rc);if(rc)goto cleanup;trace_started=1;
    EVENT_TRACE_LOGFILEW log={0};log.LoggerName=name;log.ProcessTraceMode=PROCESS_TRACE_MODE_REAL_TIME|PROCESS_TRACE_MODE_EVENT_RECORD;log.EventRecordCallback=callback;
    trace=OpenTraceW(&log);if(trace==INVALID_PROCESSTRACE_HANDLE) {printf("trace_open_error=%lu\n",GetLastError());goto cleanup;}
    consumer=CreateThread(NULL,0,consume,&trace,0,NULL);if(!consumer)goto cleanup;
    EVENT_FILTER_EVENT_ID ids={0};ids.FilterIn=TRUE;ids.Count=1;ids.Events[0]=0;
    EVENT_FILTER_DESCRIPTOR filters[2]={{0}};filters[0].Ptr=(ULONGLONG)(ULONG_PTR)&owner_pid;filters[0].Size=sizeof(owner_pid);filters[0].Type=EVENT_FILTER_TYPE_PID;
    filters[1].Ptr=(ULONGLONG)(ULONG_PTR)&ids;filters[1].Size=sizeof(ids);filters[1].Type=EVENT_FILTER_TYPE_EVENT_ID;
    ENABLE_TRACE_PARAMETERS params={0};params.Version=ENABLE_TRACE_PARAMETERS_VERSION_2;params.EnableFilterDesc=filters;params.FilterDescCount=2;
    rc=EnableTraceEx2(session,&provider,EVENT_CONTROL_CODE_ENABLE_PROVIDER,TRACE_LEVEL_VERBOSE,0,0,5000,&params);printf("enable=%lu\n",rc);if(rc)goto cleanup;
    server_thread=CreateThread(NULL,0,serve,NULL,0,NULL);if(!server_thread)goto cleanup;
    session_http=WinHttpOpen(L"LunaGC72 synthetic fixture",WINHTTP_ACCESS_TYPE_NO_PROXY,WINHTTP_NO_PROXY_NAME,WINHTTP_NO_PROXY_BYPASS,0);
    if(!session_http)goto cleanup;WinHttpSetTimeouts(session_http,5000,5000,5000,5000);
    conn=WinHttpConnect(session_http,L"127.0.0.1",port,0);if(!conn)goto cleanup;
    int get_ok=request(conn,0),post_ok=request(conn,1);
    DWORD server_wait=WaitForSingleObject(server_thread,10000);printf("server_wait=%lu served=%ld request_checks=%ld server_errors=%ld listener_close_error=%ld\n",server_wait,served,request_checks,server_errors,listener_close_error);
    ControlTraceW(session,name,prop,EVENT_TRACE_CONTROL_FLUSH);Sleep(1500);
    printf("events=%ld version0=%ld version1=%ld wrong_pid=%ld wrong_id=%ld unknown_version=%ld tdh_errors=%ld schema_errors=%ld path_query=%ld field_header=%ld field_body=%ld status207=%ld status208=%ld\n",event_count,version0,version1,wrong_pid,wrong_id,unknown_version,tdh_errors,schema_errors,path_query,field_header,field_body,code207,code208);
    result=(get_ok&&post_ok&&server_wait==WAIT_OBJECT_0&&served==2&&request_checks==2&&!server_errors&&listener_close_error==0&&!wrong_pid&&!wrong_id&&!unknown_version&&!tdh_errors&&!schema_errors)?0:1;
cleanup:
    if(conn)WinHttpCloseHandle(conn);if(session_http)WinHttpCloseHandle(session_http);
    if(listener!=INVALID_SOCKET) {closesocket(listener);listener=INVALID_SOCKET;}
    if(server_thread){WaitForSingleObject(server_thread,6000);CloseHandle(server_thread);}
    if(trace!=INVALID_PROCESSTRACE_HANDLE)CloseTrace(trace);
    if(trace_started) {ULONG stop=ControlTraceW(session,name,prop,EVENT_TRACE_CONTROL_STOP);ULONG after=ControlTraceW(0,name,prop,EVENT_TRACE_CONTROL_QUERY);printf("stop=%lu query_after_stop=%lu\n",stop,after);if(stop||after!=ERROR_WMI_INSTANCE_NOT_FOUND)result=1;}
    if(consumer){WaitForSingleObject(consumer,5000);CloseHandle(consumer);}
    if(ws_started){SOCKET probe=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);if(probe!=INVALID_SOCKET){SOCKADDR_IN a={0};a.sin_family=AF_INET;a.sin_addr.s_addr=htonl(INADDR_LOOPBACK);a.sin_port=addr.sin_port;int rebind=bind(probe,(SOCKADDR*)&a,sizeof(a));printf("port_rebind=%s error=%d\n",rebind==0?"ok":"failed",rebind==0?0:WSAGetLastError());closesocket(probe);}WSACleanup();}
    free(prop);return result;
}
