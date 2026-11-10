#ifndef CLIENT72_C48_FULL_HELPER_SHARED_H
#define CLIENT72_C48_FULL_HELPER_SHARED_H
/* Root-published full-helper ABI v1. The older witness header is unchanged. */
#include <stddef.h>
#include <stdint.h>
#define C48F_VERSION 1u
#define C48F_CASE_SSE_LONG 257u
#define C48F_CASE_AVX_LONG 258u
#define C48F_CASE_MASK 3u
#define C48F_SOURCE_BYTES 904u
#define C48F_HELPER_RVA 0x01fdf9c0u
#define C48F_ARENA_RESERVED UINT64_C(0x06d20000)
#define C48F_TF 0x100u
#define C48F_MAX_EVENTS 128u
#define C48F_MAX_SOURCE_RETIRED 128u
#define C48F_MAX_GET_CONTEXT 85u
#define C48F_MAX_SET_CONTEXT 42u
#define C48F_MAX_READS 64u
#define C48F_MAX_READ_BYTES 4096u
#define C48F_MAX_SINGLE_READ 904u
#define C48F_MAX_PAGE_QUERIES 13u
#define C48F_MAX_INSTRUCTION_BYTES 15u
#define C48F_WALL_MS 10000u
#define C48F_WAIT_MS 50u
#define C48F_ABORT_EXIT 90u
#define C48F_STATE_INIT 0u
#define C48F_STATE_READY 1u
#define C48F_STATE_RETURNED 2u
#define C48F_STATE_REPORTED 3u
#define C48F_WRAPPER_BYTES 27u
#define C48F_ARM_OFFSET 18u
#define C48F_CALL_OFFSET 19u
#define C48F_AFTER_CALL_OFFSET 22u
#pragma pack(push, 8)
typedef struct C48FullDescriptor {
    uint8_t magic[8];
    uint32_t version;
    uint32_t byteSize;
    uint32_t caseId;
    uint32_t childState;
    uint8_t runId[16];
    uint64_t imageBase;
    uint32_t pid;
    uint32_t tid;
    uint32_t armRva;
    uint32_t callRva;
    uint32_t afterCallRva;
    uint32_t wrapperBeginRva;
    uint32_t wrapperEndRva;
    uint32_t sourceBytes;
    uint64_t arenaBase;
    uint64_t arenaReserved;
    uint64_t helperEntry;
    uint64_t bufferReserveBase;
    uint64_t bufferReserveBytes;
    uint64_t bufferPageBase;
    uint64_t bufferKnownBase;
    uint64_t bufferKnownBytes;
    uint64_t headBase;
    uint64_t destination;
    uint64_t stackLow;
    uint64_t stackHigh;
    uint32_t pageSize;
    uint32_t allocationGranularity;
    uint32_t hostGateValid;
    uint32_t cpuidCount;
    uint32_t xgetbvCount;
    uint32_t continuedToC;
    uint32_t childResultReady;
    uint32_t reservedU32;
    uint64_t xcr0;
    uint32_t cpuidLeaf0[4];
    uint32_t cpuidLeaf1[4];
    uint8_t sourceSHA256[32];
    uint8_t catalogSHA256[32];
    uint8_t casesSHA256[32];
    uint8_t reserved[40];
} C48FullDescriptor;
typedef struct C48FullBootstrap {
    uint8_t magic[8];
    uint32_t version;
    uint32_t byteSize;
    uint32_t descriptorRva;
    uint32_t allowedCaseMask;
    uint64_t imageBytes;
    uint8_t childExeSHA256[32];
    uint8_t sharedHeaderSHA256[32];
    uint8_t sourceSHA256[32];
    uint8_t catalogSHA256[32];
    uint8_t casesSHA256[32];
    uint32_t armRva;
    uint32_t callRva;
    uint32_t afterCallRva;
    uint32_t wrapperEndRva;
    uint32_t wrapperBeginRva;
    uint32_t descriptorBytes;
    uint32_t helperRva;
    uint32_t helperBytes;
    uint64_t arenaReserved;
    uint32_t exitPolicyVersion;
    uint32_t eflagsPolicyVersion;
    uint32_t caseCount;
    uint32_t wrapperBytes;
    uint8_t reserved[8];
} C48FullBootstrap;
#pragma pack(pop)
#define C48F_OFFSET(type, field, n) _Static_assert(offsetof(type, field) == (n), #type "." #field)
_Static_assert(sizeof(C48FullDescriptor) == 384, "C48FullDescriptor bytes");
C48F_OFFSET(C48FullDescriptor, magic, 0);
C48F_OFFSET(C48FullDescriptor, version, 8);
C48F_OFFSET(C48FullDescriptor, byteSize, 12);
C48F_OFFSET(C48FullDescriptor, caseId, 16);
C48F_OFFSET(C48FullDescriptor, childState, 20);
C48F_OFFSET(C48FullDescriptor, runId, 24);
C48F_OFFSET(C48FullDescriptor, imageBase, 40);
C48F_OFFSET(C48FullDescriptor, pid, 48);
C48F_OFFSET(C48FullDescriptor, tid, 52);
C48F_OFFSET(C48FullDescriptor, armRva, 56);
C48F_OFFSET(C48FullDescriptor, callRva, 60);
C48F_OFFSET(C48FullDescriptor, afterCallRva, 64);
C48F_OFFSET(C48FullDescriptor, wrapperBeginRva, 68);
C48F_OFFSET(C48FullDescriptor, wrapperEndRva, 72);
C48F_OFFSET(C48FullDescriptor, sourceBytes, 76);
C48F_OFFSET(C48FullDescriptor, arenaBase, 80);
C48F_OFFSET(C48FullDescriptor, arenaReserved, 88);
C48F_OFFSET(C48FullDescriptor, helperEntry, 96);
C48F_OFFSET(C48FullDescriptor, bufferReserveBase, 104);
C48F_OFFSET(C48FullDescriptor, bufferReserveBytes, 112);
C48F_OFFSET(C48FullDescriptor, bufferPageBase, 120);
C48F_OFFSET(C48FullDescriptor, bufferKnownBase, 128);
C48F_OFFSET(C48FullDescriptor, bufferKnownBytes, 136);
C48F_OFFSET(C48FullDescriptor, headBase, 144);
C48F_OFFSET(C48FullDescriptor, destination, 152);
C48F_OFFSET(C48FullDescriptor, stackLow, 160);
C48F_OFFSET(C48FullDescriptor, stackHigh, 168);
C48F_OFFSET(C48FullDescriptor, pageSize, 176);
C48F_OFFSET(C48FullDescriptor, allocationGranularity, 180);
C48F_OFFSET(C48FullDescriptor, hostGateValid, 184);
C48F_OFFSET(C48FullDescriptor, cpuidCount, 188);
C48F_OFFSET(C48FullDescriptor, xgetbvCount, 192);
C48F_OFFSET(C48FullDescriptor, continuedToC, 196);
C48F_OFFSET(C48FullDescriptor, childResultReady, 200);
C48F_OFFSET(C48FullDescriptor, reservedU32, 204);
C48F_OFFSET(C48FullDescriptor, xcr0, 208);
C48F_OFFSET(C48FullDescriptor, cpuidLeaf0, 216);
C48F_OFFSET(C48FullDescriptor, cpuidLeaf1, 232);
C48F_OFFSET(C48FullDescriptor, sourceSHA256, 248);
C48F_OFFSET(C48FullDescriptor, catalogSHA256, 280);
C48F_OFFSET(C48FullDescriptor, casesSHA256, 312);
C48F_OFFSET(C48FullDescriptor, reserved, 344);
_Static_assert(sizeof(C48FullBootstrap) == 256, "C48FullBootstrap bytes");
C48F_OFFSET(C48FullBootstrap, magic, 0);
C48F_OFFSET(C48FullBootstrap, version, 8);
C48F_OFFSET(C48FullBootstrap, byteSize, 12);
C48F_OFFSET(C48FullBootstrap, descriptorRva, 16);
C48F_OFFSET(C48FullBootstrap, allowedCaseMask, 20);
C48F_OFFSET(C48FullBootstrap, imageBytes, 24);
C48F_OFFSET(C48FullBootstrap, childExeSHA256, 32);
C48F_OFFSET(C48FullBootstrap, sharedHeaderSHA256, 64);
C48F_OFFSET(C48FullBootstrap, sourceSHA256, 96);
C48F_OFFSET(C48FullBootstrap, catalogSHA256, 128);
C48F_OFFSET(C48FullBootstrap, casesSHA256, 160);
C48F_OFFSET(C48FullBootstrap, armRva, 192);
C48F_OFFSET(C48FullBootstrap, callRva, 196);
C48F_OFFSET(C48FullBootstrap, afterCallRva, 200);
C48F_OFFSET(C48FullBootstrap, wrapperEndRva, 204);
C48F_OFFSET(C48FullBootstrap, wrapperBeginRva, 208);
C48F_OFFSET(C48FullBootstrap, descriptorBytes, 212);
C48F_OFFSET(C48FullBootstrap, helperRva, 216);
C48F_OFFSET(C48FullBootstrap, helperBytes, 220);
C48F_OFFSET(C48FullBootstrap, arenaReserved, 224);
C48F_OFFSET(C48FullBootstrap, exitPolicyVersion, 232);
C48F_OFFSET(C48FullBootstrap, eflagsPolicyVersion, 236);
C48F_OFFSET(C48FullBootstrap, caseCount, 240);
C48F_OFFSET(C48FullBootstrap, wrapperBytes, 244);
C48F_OFFSET(C48FullBootstrap, reserved, 248);
#undef C48F_OFFSET
static const uint8_t C48F_DESCRIPTOR_MAGIC[8] = {0x43,0x34,0x38,0x46,0x55,0x4c,0x4c,0x31};
static const uint8_t C48F_BOOTSTRAP_MAGIC[8] = {0x43,0x34,0x38,0x46,0x42,0x4f,0x30,0x31};
static const uint8_t C48F_WRAPPER_CODE[27] = {0x48,0x83,0xec,0x28,0x49,0x89,0xcb,0x48,0x89,0xd1,0x31,0xd2,0x41,0xb8,0x30,0x00,0x00,0x00,0xcc,0x41,0xff,0xd3,0x48,0x83,0xc4,0x28,0xc3};
static const uint8_t C48F_XCR_CODE[13] = {0x31,0xc9,0x0f,0x01,0xd0,0x48,0xc1,0xe2,0x20,0x48,0x09,0xd0,0xc3};
/* Original 904B stays in ignored fixed file, never this header. Generated catalog/cases stay in private build. */
extern volatile C48FullDescriptor g_c48FullDescriptor;
extern uint64_t C48FullWrapper(const void *entry, uint8_t *D);
extern uint64_t C48FullReadXcr0(void);
extern uint8_t C48FullWrapperBegin, C48FullArm, C48FullCall, C48FullAfterCall, C48FullWrapperEnd;
extern uint8_t C48FullXcrEntry, C48FullXcrEnd;
#endif
