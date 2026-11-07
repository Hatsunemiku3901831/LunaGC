#ifndef SDK72_ETW_REDACT_H
#define SDK72_ETW_REDACT_H
#include <stdint.h>
typedef struct { unsigned host, method, route, length, status; uint64_t timestamp; } safe_record;
safe_record sanitize(const char *host,const char *path,const char *method,unsigned status,uint64_t timestamp);
#endif
