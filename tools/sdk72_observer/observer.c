#include "observer.h"
#include <wchar.h>
_Static_assert(_Generic(&WinHttpConnect, connect_fn: 1, default: 0), "WinHttpConnect SDK signature mismatch");
_Static_assert(_Generic(&WinHttpOpenRequest, request_fn: 1, default: 0), "WinHttpOpenRequest SDK signature mismatch");
_Static_assert(_Generic(&WinHttpQueryHeaders, query_fn: 1, default: 0), "WinHttpQueryHeaders SDK signature mismatch");
static unsigned bounded_len(LPCWSTR s) { unsigned n=0; if(s) while(n<4096 && s[n]) ++n; return n; }
static unsigned host_id(LPCWSTR s) {
    if(!s) return 0;
    if(wcscmp(s,L"sdk-static.mihoyo.com")==0) return 1;
    if(wcscmp(s,L"127.0.0.1")==0) return 2;
    return 0;
}
static unsigned method_id(LPCWSTR s) {
    if(!s) return 0;
    if(wcscmp(s,L"GET")==0) return 1;
    if(wcscmp(s,L"POST")==0) return 2;
    return 0;
}
static unsigned route_id(LPCWSTR s) {
    static const WCHAR *routes[]={L"/query_region_list",L"/query_cur_region/os_usa",L"/hk4e_cn/mdk/shield/api/loadConfig"};
    unsigned i,n; if(!s) return 0;
    n=0; while(n<4096 && s[n] && s[n]!=L'?' && s[n]!=L'#') ++n;
    if(n==4096) return 0;
    for(i=0;i<3;i++) if(wcslen(routes[i])==n && wcsncmp(s,routes[i],n)==0) return i+1;
    return 0;
}
HINTERNET observed_connect(connect_fn call,observer_sink sink,HINTERNET h,LPCWSTR host,INTERNET_PORT port,DWORD reserved) {
    HINTERNET result=call(h,host,port,reserved); DWORD error=GetLastError();
    observation o={1,host_id(host),0,0,0,0,0}; if(!o.host) o.length=bounded_len(host); sink(&o); SetLastError(error); return result;
}
HINTERNET observed_request(request_fn call,observer_sink sink,HINTERNET h,LPCWSTR verb,LPCWSTR path,LPCWSTR version,LPCWSTR ref,LPCWSTR *accept,DWORD flags) {
    HINTERNET result=call(h,verb,path,version,ref,accept,flags); DWORD error=GetLastError();
    observation o={2,0,method_id(verb),route_id(path),0,0,0}; if(!o.route) { o.length=bounded_len(path); o.method=0; } sink(&o); SetLastError(error); return result;
}
BOOL observed_query(query_fn call,observer_sink sink,HINTERNET h,DWORD info,LPCWSTR name,LPVOID buffer,LPDWORD length,LPDWORD index) {
    BOOL result=call(h,info,name,buffer,length,index); DWORD error=GetLastError();
    observation o={3,0,0,0,0,0,0};
    if(result && info==(WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER) && buffer && length && *length==sizeof(DWORD)) { o.status=*(DWORD*)buffer; o.has_status=1; }
    sink(&o); SetLastError(error); return result;
}
