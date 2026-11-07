#ifndef SDK72_OBSERVER_H
#define SDK72_OBSERVER_H
#include <windows.h>
#include <winhttp.h>
typedef HINTERNET (WINAPI *connect_fn)(HINTERNET,LPCWSTR,INTERNET_PORT,DWORD);
typedef HINTERNET (WINAPI *request_fn)(HINTERNET,LPCWSTR,LPCWSTR,LPCWSTR,LPCWSTR,LPCWSTR *,DWORD);
typedef BOOL (WINAPI *query_fn)(HINTERNET,DWORD,LPCWSTR,LPVOID,LPDWORD,LPDWORD);
typedef struct { unsigned event, host, method, route, length, status, has_status; } observation;
typedef void (*observer_sink)(const observation *);
HINTERNET observed_connect(connect_fn,observer_sink,HINTERNET,LPCWSTR,INTERNET_PORT,DWORD);
HINTERNET observed_request(request_fn,observer_sink,HINTERNET,LPCWSTR,LPCWSTR,LPCWSTR,LPCWSTR,LPCWSTR *,DWORD);
BOOL observed_query(query_fn,observer_sink,HINTERNET,DWORD,LPCWSTR,LPVOID,LPDWORD,LPDWORD);
#endif
