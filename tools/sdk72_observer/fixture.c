#include "observer.h"
#include <stdint.h>
#include <stdio.h>
#include <string.h>
static HINTERNET handle=(HINTERNET)(uintptr_t)0x12345678;
static LPCWSTR host,verb,path,version,ref; static LPCWSTR *expected_accept; static DWORD option,flags; static void *buf; static LPDWORD sizep,indexp; static LPCWSTR header;
static int checks,failures; static observation rows[16]; static LARGE_INTEGER times[16]; static unsigned count;
#define CHECK(x) do { ++checks; if(!(x)) ++failures; } while(0)
static void emit(const observation *o) { if(count<16) { rows[count]=*o; QueryPerformanceCounter(&times[count]); count++; } SetLastError(0xabcdef); }
static HINTERNET WINAPI fake_connect(HINTERNET h,LPCWSTR p,INTERNET_PORT port,DWORD reserved) {
    CHECK(GetLastError()==0x9999); CHECK(h==handle); CHECK(p==host); CHECK(port==443); CHECK(reserved==17); SetLastError(0x4321); return (HINTERNET)(uintptr_t)0x87654321;
}
static HINTERNET WINAPI fake_request(HINTERNET h,LPCWSTR a,LPCWSTR b,LPCWSTR c,LPCWSTR d,LPCWSTR *e,DWORD f) {
    CHECK(GetLastError()==0x9999); CHECK(h==handle); CHECK(a==verb); CHECK(b==path); CHECK(c==version); CHECK(d==ref); CHECK(e==expected_accept); CHECK(f==flags); SetLastError(0x1234); return NULL;
}
static BOOL WINAPI fake_query(HINTERNET h,DWORD a,LPCWSTR b,LPVOID c,LPDWORD d,LPDWORD e) {
    CHECK(GetLastError()==0x9999); CHECK(h==handle); CHECK(a==option); CHECK(b==header); CHECK(c==buf); CHECK(d==sizep); CHECK(e==indexp);
    if(a==(WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER)) { *(DWORD*)c=207; *d=sizeof(DWORD); SetLastError(0x5678); return TRUE; }
    SetLastError(0x8765); return FALSE;
}
int fixture_run(void) {
    LPCWSTR accepts[]={L"header-sentinel-SECRET",NULL}; DWORD value=0,length=sizeof(value),index=3;
    host=L"sdk-static.mihoyo.com";
    SetLastError(0x9999); CHECK(observed_connect(fake_connect,emit,handle,host,443,17)==(HINTERNET)(uintptr_t)0x87654321); CHECK(GetLastError()==0x4321); CHECK(rows[count-1].host==1);
    host=L"unknown-user-secret.invalid";
    SetLastError(0x9999); CHECK(observed_connect(fake_connect,emit,handle,host,443,17)!=NULL); CHECK(GetLastError()==0x4321); CHECK(rows[count-1].host==0);
    verb=L"GET"; path=L"/query_region_list?token=SENTINEL_SECRET#password"; version=L"HTTP/1.1"; ref=L"password-secret"; expected_accept=accepts; flags=0x800000;
    SetLastError(0x9999); CHECK(observed_request(fake_request,emit,handle,verb,path,version,ref,expected_accept,flags)==NULL); CHECK(GetLastError()==0x1234); CHECK(rows[count-1].method==1 && rows[count-1].route==1 && rows[count-1].length==0);
    verb=L"POST"; path=L"/query_cur_region/os_usa?password=SENTINEL_SECRET";
    SetLastError(0x9999); CHECK(observed_request(fake_request,emit,handle,verb,path,version,ref,expected_accept,flags)==NULL); CHECK(GetLastError()==0x1234); CHECK(rows[count-1].method==2 && rows[count-1].route==2);
    verb=L"custom-secret"; path=L"/unknown/secret?token=SENTINEL_SECRET";
    SetLastError(0x9999); CHECK(observed_request(fake_request,emit,handle,verb,path,version,ref,expected_accept,flags)==NULL); CHECK(GetLastError()==0x1234); CHECK(rows[count-1].method==0 && rows[count-1].route==0 && rows[count-1].length>0);
    path=L"/unknown/\u79d8\u5bc6?token=SENTINEL_SECRET";
    SetLastError(0x9999); CHECK(observed_request(fake_request,emit,handle,verb,path,version,ref,expected_accept,flags)==NULL); CHECK(GetLastError()==0x1234); CHECK(rows[count-1].route==0);
    option=WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER; header=L"header-sentinel-SECRET";buf=&value;sizep=&length;indexp=&index;
    SetLastError(0x9999); CHECK(observed_query(fake_query,emit,handle,option,header,buf,sizep,indexp)); CHECK(GetLastError()==0x5678); CHECK(rows[count-1].has_status && rows[count-1].status==207);
    option=WINHTTP_QUERY_RAW_HEADERS; SetLastError(0x9999); CHECK(!observed_query(fake_query,emit,handle,option,header,buf,sizep,indexp)); CHECK(GetLastError()==0x8765); CHECK(!rows[count-1].has_status);
    for(unsigned i=0;i<count;i++) {
        CHECK(i==0 || times[i].QuadPart>=times[i-1].QuadPart);
        printf("tick=%lld event=%u host=%u method=%u route=%u length=%u status=%u has_status=%u\n",times[i].QuadPart,rows[i].event,rows[i].host,rows[i].method,rows[i].route,rows[i].length,rows[i].status,rows[i].has_status);
    }
    printf("checks=%d failures=%d\n",checks,failures); return failures==0;
}
extern int abi_check(void);
int main(void) { int ok=abi_check(); printf("nonvolatile_gpr_and_fixture=%d\n",ok); return ok?0:1; }
