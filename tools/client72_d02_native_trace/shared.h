#ifndef CLIENT72_C48_TRACE_SHARED_H
#define CLIENT72_C48_TRACE_SHARED_H

/* Root-owned ABI v1. Witnesses only; no original 904-byte helper. */
#include <stddef.h>
#include <stdint.h>

#define C48_VERSION 1u
#define C48_CASE_RETURN 1u
#define C48_CASE_YMM 2u
#define C48_CASE_FAULT 3u
#define C48_CASE_MASK 7u
#define C48_STATE_INIT 0u
#define C48_STATE_READY 1u
#define C48_STATE_RETURNED 2u
#define C48_STATE_REPORTED 3u
#define C48_TF 0x100u
#define C48_MAX_EVENTS 128u
#define C48_MAX_GET_CONTEXT 32u
#define C48_MAX_SET_CONTEXT 16u
#define C48_WALL_MILLISECONDS 10000u
#define C48_WAIT_MILLISECONDS 50u
#define C48_ABORT_EXIT 90u
#define C48_PARENT_EXPECTED_FAULT_EXIT 30u
#define C48_BLOCK_BYTES 96u
#define C48_PAYLOAD_OFFSET 32u
#define C48_PAYLOAD_BYTES 32u
#define C48_BLOCK_COUNT 3u
#define C48_WRAPPER_BYTES 25u
#define C48_ARM_OFFSET 16u
#define C48_CALL_OFFSET 17u
#define C48_AFTER_CALL_OFFSET 20u

#pragma pack(push, 8)
typedef struct C48Descriptor {
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
    uint32_t leafRva;
    uint32_t leafBytes;
    uint32_t blockCount;
    uint64_t blockBase[3];
    uint64_t stackLow;
    uint64_t stackHigh;
    uint64_t arenaBase;
    uint64_t arenaReserved;
    uint64_t bufferDomainBase;
    uint64_t bufferDomainBytes;
    uint32_t hostGateValid;
    uint32_t cpuidCount;
    uint32_t xgetbvCount;
    uint32_t reservedU32;
    uint64_t xcr0;
    uint32_t cpuidLeaf0[4];
    uint32_t cpuidLeaf1[4];
    uint32_t continuedToC;
    uint32_t childResultReady;
    uint8_t reserved[40];
} C48Descriptor;

typedef struct C48Bootstrap {
    uint8_t magic[8];
    uint32_t version;
    uint32_t byteSize;
    uint32_t descriptorRva;
    uint32_t allowedCaseMask;
    uint64_t imageBytes;
    uint8_t childExeSHA256[32];
    uint8_t sharedHeaderSHA256[32];
    uint32_t armRva;
    uint32_t callRva;
    uint32_t afterCallRva;
    uint32_t wrapperEndRva;
    uint32_t returnLeafRva;
    uint32_t ymmLeafRva;
    uint32_t faultLeafRva;
    uint32_t wrapperBeginRva;
} C48Bootstrap;
#pragma pack(pop)

#define C48_OFFSET(type, field, value) \
    _Static_assert(offsetof(type, field) == (value), #type "." #field)
_Static_assert(sizeof(C48Descriptor) == 256, "C48Descriptor bytes");
C48_OFFSET(C48Descriptor, magic, 0);
C48_OFFSET(C48Descriptor, version, 8);
C48_OFFSET(C48Descriptor, byteSize, 12);
C48_OFFSET(C48Descriptor, caseId, 16);
C48_OFFSET(C48Descriptor, childState, 20);
C48_OFFSET(C48Descriptor, runId, 24);
C48_OFFSET(C48Descriptor, imageBase, 40);
C48_OFFSET(C48Descriptor, pid, 48);
C48_OFFSET(C48Descriptor, tid, 52);
C48_OFFSET(C48Descriptor, armRva, 56);
C48_OFFSET(C48Descriptor, callRva, 60);
C48_OFFSET(C48Descriptor, afterCallRva, 64);
C48_OFFSET(C48Descriptor, leafRva, 68);
C48_OFFSET(C48Descriptor, leafBytes, 72);
C48_OFFSET(C48Descriptor, blockCount, 76);
C48_OFFSET(C48Descriptor, blockBase, 80);
C48_OFFSET(C48Descriptor, stackLow, 104);
C48_OFFSET(C48Descriptor, stackHigh, 112);
C48_OFFSET(C48Descriptor, arenaBase, 120);
C48_OFFSET(C48Descriptor, arenaReserved, 128);
C48_OFFSET(C48Descriptor, bufferDomainBase, 136);
C48_OFFSET(C48Descriptor, bufferDomainBytes, 144);
C48_OFFSET(C48Descriptor, hostGateValid, 152);
C48_OFFSET(C48Descriptor, cpuidCount, 156);
C48_OFFSET(C48Descriptor, xgetbvCount, 160);
C48_OFFSET(C48Descriptor, reservedU32, 164);
C48_OFFSET(C48Descriptor, xcr0, 168);
C48_OFFSET(C48Descriptor, cpuidLeaf0, 176);
C48_OFFSET(C48Descriptor, cpuidLeaf1, 192);
C48_OFFSET(C48Descriptor, continuedToC, 208);
C48_OFFSET(C48Descriptor, childResultReady, 212);
C48_OFFSET(C48Descriptor, reserved, 216);
_Static_assert(sizeof(C48Bootstrap) == 128, "C48Bootstrap bytes");
C48_OFFSET(C48Bootstrap, magic, 0);
C48_OFFSET(C48Bootstrap, version, 8);
C48_OFFSET(C48Bootstrap, byteSize, 12);
C48_OFFSET(C48Bootstrap, descriptorRva, 16);
C48_OFFSET(C48Bootstrap, allowedCaseMask, 20);
C48_OFFSET(C48Bootstrap, imageBytes, 24);
C48_OFFSET(C48Bootstrap, childExeSHA256, 32);
C48_OFFSET(C48Bootstrap, sharedHeaderSHA256, 64);
C48_OFFSET(C48Bootstrap, armRva, 96);
C48_OFFSET(C48Bootstrap, callRva, 100);
C48_OFFSET(C48Bootstrap, afterCallRva, 104);
C48_OFFSET(C48Bootstrap, wrapperEndRva, 108);
C48_OFFSET(C48Bootstrap, returnLeafRva, 112);
C48_OFFSET(C48Bootstrap, ymmLeafRva, 116);
C48_OFFSET(C48Bootstrap, faultLeafRva, 120);
C48_OFFSET(C48Bootstrap, wrapperBeginRva, 124);
#undef C48_OFFSET

static const uint8_t C48_DESCRIPTOR_MAGIC[8] = {0x43,0x34,0x38,0x54,0x52,0x43,0x31,0};
static const uint8_t C48_BOOTSTRAP_MAGIC[8] = {0x43,0x34,0x38,0x42,0x4f,0x4f,0x54,0x31};
static const uint8_t C48_WRAPPER_CODE[25] = {
    0x48,0x83,0xec,0x28,0x49,0x89,0xcb,0x48,0x89,0xd1,0x4c,0x89,0xc2,
    0x4d,0x89,0xc8,0xcc,0x41,0xff,0xd3,0x48,0x83,0xc4,0x28,0xc3
};
static const uint8_t C48_RETURN_CODE[11] = {0x48,0xb8,0xef,0xcd,0xab,0x89,0x67,0x45,0x23,0xf1,0xc3};
static const uint8_t C48_YMM_CODE[23] = {
    0xc5,0xfe,0x6f,0x01,0xc5,0xfe,0x7f,0x02,0xc4,0xe3,0x7d,0x18,
    0xc0,0x01,0xc4,0xc1,0x7e,0x7f,0x00,0xc5,0xf8,0x77,0xc3
};
static const uint8_t C48_FAULT_CODE[3] = {0x0f,0x0b,0xc3};
static const uint8_t C48_XCR_CODE[13] = {0x31,0xc9,0x0f,0x01,0xd0,0x48,0xc1,0xe2,0x20,0x48,0x09,0xd0,0xc3};

extern volatile C48Descriptor g_c48Descriptor;
extern uint64_t C48Wrapper(const void *leaf, const uint8_t *input, uint8_t *before, uint8_t *after);
extern uint64_t C48ReadXcr0(void);
extern uint8_t C48WrapperBegin, C48Arm, C48Call, C48AfterCall, C48WrapperEnd;
extern uint8_t C48ReturnLeaf, C48ReturnLeafEnd, C48YmmLeaf, C48YmmLeafEnd;
extern uint8_t C48FaultLeaf, C48FaultLeafEnd, C48XcrEntry, C48XcrEnd;

#endif
