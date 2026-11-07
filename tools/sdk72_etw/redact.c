#include "redact.h"
#include <string.h>
safe_record sanitize(const char *host,const char *path,const char *method,unsigned status,uint64_t timestamp) {
    safe_record r={0}; size_t n=0; r.timestamp=timestamp;
    if(host && strcmp(host,"sdk-static.mihoyo.com")==0) r.host=1;
    else if(host && strcmp(host,"127.0.0.1")==0) r.host=2;
    if(path) { while(n<4096 && path[n] && path[n]!='?' && path[n]!='#') n++; }
    if(n==18 && strncmp(path,"/query_region_list",18)==0) r.route=1;
    else if(n==24 && strncmp(path,"/query_cur_region/os_usa",24)==0) r.route=2;
    if(r.route) {
        if(method && strcmp(method,"GET")==0) r.method=1;
        else if(method && strcmp(method,"POST")==0) r.method=2;
        r.status=status;
    } else {
        r.host=0; r.length=(unsigned)n;
    }
    return r;
}
