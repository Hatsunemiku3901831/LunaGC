#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <intrin.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "shared.h"

volatile C48Descriptor g_c48Descriptor;

typedef struct Arguments {
    uint32_t caseId;
    uint8_t runId[16];
    const char *runIdText;
    const char *output;
} Arguments;

static int parse_arguments(int argc, char **argv, Arguments *args);
static uint32_t rva(const void *image, const void *address);
static int prepare_ymm(uint8_t **arena);
static int write_result(const Arguments *args, uint64_t result, const uint8_t *before, const uint8_t *arena);

int main(int argc, char **argv)
{
    Arguments args;
    uint8_t *arena = NULL;
    uint8_t before[3u * C48_BLOCK_BYTES];
    const void *image;
    const uint8_t *leaf;
    const NT_TIB *tib;
    uint64_t result;
    uint32_t i;

    if (!parse_arguments(argc, argv, &args)) return 2;
    image = GetModuleHandleW(NULL);
    tib = (const NT_TIB *)NtCurrentTeb();
    for (i = 0; i < 8; ++i) g_c48Descriptor.magic[i] = C48_DESCRIPTOR_MAGIC[i];
    g_c48Descriptor.version = C48_VERSION;
    g_c48Descriptor.byteSize = (uint32_t)sizeof(C48Descriptor);
    g_c48Descriptor.caseId = args.caseId;
    for (i = 0; i < 16; ++i) g_c48Descriptor.runId[i] = args.runId[i];
    g_c48Descriptor.imageBase = (uint64_t)(uintptr_t)image;
    g_c48Descriptor.pid = GetCurrentProcessId();
    g_c48Descriptor.tid = GetCurrentThreadId();
    g_c48Descriptor.armRva = rva(image, &C48Arm);
    g_c48Descriptor.callRva = rva(image, &C48Call);
    g_c48Descriptor.afterCallRva = rva(image, &C48AfterCall);
    g_c48Descriptor.stackLow = (uint64_t)(uintptr_t)tib->StackLimit;
    g_c48Descriptor.stackHigh = (uint64_t)(uintptr_t)tib->StackBase;

    if (args.caseId == C48_CASE_YMM) {
        if (!prepare_ymm(&arena)) return 3;
        memcpy(before, arena, sizeof(before));
        leaf = &C48YmmLeaf;
        g_c48Descriptor.leafBytes = (uint32_t)sizeof(C48_YMM_CODE);
    } else if (args.caseId == C48_CASE_RETURN) {
        leaf = &C48ReturnLeaf;
        g_c48Descriptor.leafBytes = (uint32_t)sizeof(C48_RETURN_CODE);
    } else {
        leaf = &C48FaultLeaf;
        g_c48Descriptor.leafBytes = (uint32_t)sizeof(C48_FAULT_CODE);
    }
    g_c48Descriptor.leafRva = rva(image, leaf);
    g_c48Descriptor.childState = C48_STATE_READY;
    result = C48Wrapper(leaf, arena ? arena + C48_PAYLOAD_OFFSET : NULL,
        arena ? arena + C48_BLOCK_BYTES + C48_PAYLOAD_OFFSET : NULL,
        arena ? arena + 2u * C48_BLOCK_BYTES + C48_PAYLOAD_OFFSET : NULL);
    g_c48Descriptor.continuedToC = 1;
    g_c48Descriptor.childState = C48_STATE_RETURNED;
    if (args.caseId == C48_CASE_FAULT) return 4;
    if (!write_result(&args, result, before, arena)) return 5;
    g_c48Descriptor.childResultReady = 1;
    g_c48Descriptor.childState = C48_STATE_REPORTED;
    if (arena != NULL) VirtualFree(arena, 0, MEM_RELEASE);
    return 0;
}

static int parse_arguments(int argc, char **argv, Arguments *args)
{
    size_t i;
    if (argc != 7 || strcmp(argv[1], "--case") != 0 ||
        strcmp(argv[3], "--run-id") != 0 || strcmp(argv[5], "--output") != 0 ||
        strlen(argv[2]) != 1 || argv[2][0] < '1' || argv[2][0] > '3' ||
        strlen(argv[4]) != 32 || argv[6][0] == '\0') return 0;
    args->caseId = (uint32_t)(argv[2][0] - '0');
    args->runIdText = argv[4];
    args->output = argv[6];
    for (i = 0; i < 32; ++i) {
        char c = argv[4][i];
        unsigned value;
        if (c >= '0' && c <= '9') value = (unsigned)(c - '0');
        else if (c >= 'a' && c <= 'f') value = (unsigned)(c - 'a') + 10u;
        else return 0;
        if ((i & 1u) == 0) args->runId[i / 2] = (uint8_t)(value << 4);
        else args->runId[i / 2] |= (uint8_t)value;
    }
    return 1;
}

static uint32_t rva(const void *image, const void *address)
{
    return (uint32_t)((uintptr_t)address - (uintptr_t)image);
}

static int prepare_ymm(uint8_t **arena)
{
    int cpu[4];
    SYSTEM_INFO info;
    uint32_t i, block;
    static const uint8_t guards[3][2] = {{0xa1,0xa2},{0xb1,0xb2},{0xc1,0xc2}};

    __cpuid(cpu, 0);
    ++g_c48Descriptor.cpuidCount;
    for (i = 0; i < 4; ++i) g_c48Descriptor.cpuidLeaf0[i] = (uint32_t)cpu[i];
    if (g_c48Descriptor.cpuidLeaf0[0] < 1) return 0;
    __cpuid(cpu, 1);
    ++g_c48Descriptor.cpuidCount;
    for (i = 0; i < 4; ++i) g_c48Descriptor.cpuidLeaf1[i] = (uint32_t)cpu[i];
    if ((g_c48Descriptor.cpuidLeaf1[2] & 0x1c000000u) != 0x1c000000u) return 0;
    g_c48Descriptor.xcr0 = C48ReadXcr0();
    ++g_c48Descriptor.xgetbvCount;
    if ((g_c48Descriptor.xcr0 & 6u) != 6u) return 0;
    g_c48Descriptor.hostGateValid = 1;
    GetSystemInfo(&info);
    if (info.dwPageSize < 3u * C48_BLOCK_BYTES || info.dwPageSize > 65536u) return 0;
    *arena = (uint8_t *)VirtualAlloc(NULL, 65536u, MEM_RESERVE, PAGE_NOACCESS);
    if (*arena == NULL) return 0;
    if (VirtualAlloc(*arena, info.dwPageSize, MEM_COMMIT, PAGE_READWRITE) == NULL) return 0;
    memset(*arena, 0xa7, info.dwPageSize);
    for (block = 0; block < C48_BLOCK_COUNT; ++block) {
        uint8_t *base = *arena + block * C48_BLOCK_BYTES;
        memset(base, guards[block][0], C48_PAYLOAD_OFFSET);
        memset(base + C48_PAYLOAD_OFFSET + C48_PAYLOAD_BYTES, guards[block][1], C48_PAYLOAD_OFFSET);
        g_c48Descriptor.blockBase[block] = (uint64_t)(uintptr_t)base;
    }
    for (i = 0; i < C48_PAYLOAD_BYTES; ++i) (*arena)[C48_PAYLOAD_OFFSET + i] = (uint8_t)(i + 1u);
    memset(*arena + C48_BLOCK_BYTES + C48_PAYLOAD_OFFSET, 0xcc, C48_PAYLOAD_BYTES);
    memset(*arena + 2u * C48_BLOCK_BYTES + C48_PAYLOAD_OFFSET, 0xdd, C48_PAYLOAD_BYTES);
    g_c48Descriptor.blockCount = C48_BLOCK_COUNT;
    g_c48Descriptor.arenaBase = (uint64_t)(uintptr_t)*arena;
    g_c48Descriptor.arenaReserved = 65536u;
    g_c48Descriptor.bufferDomainBase = (uint64_t)(uintptr_t)*arena;
    g_c48Descriptor.bufferDomainBytes = info.dwPageSize;
    return 1;
}

static int write_result(const Arguments *args, uint64_t result, const uint8_t *before, const uint8_t *arena)
{
    char blocksBefore[3u * C48_BLOCK_BYTES * 2u + 1u];
    char blocksAfter[3u * C48_BLOCK_BYTES * 2u + 1u];
    char json[2200];
    static const char hex[] = "0123456789abcdef";
    size_t i, bytes = arena ? 3u * C48_BLOCK_BYTES : 0;
    int length;
    DWORD written;
    HANDLE file;
    for (i = 0; i < bytes; ++i) {
        blocksBefore[2u * i] = hex[before[i] >> 4];
        blocksBefore[2u * i + 1u] = hex[before[i] & 15u];
        blocksAfter[2u * i] = hex[arena[i] >> 4];
        blocksAfter[2u * i + 1u] = hex[arena[i] & 15u];
    }
    blocksBefore[2u * bytes] = '\0';
    blocksAfter[2u * bytes] = '\0';
    length = snprintf(json, sizeof(json),
        "{\"schema\":\"c48-child-result-v1\",\"caseId\":%u,\"runId\":\"%s\","
        "\"pid\":%lu,\"tid\":%lu,\"imageBase\":\"%016llx\","
        "\"returnValue\":\"%016llx\",\"continuedToC\":true,"
        "\"blocksBefore\":\"%s\",\"blocksAfter\":\"%s\","
        "\"cpuidCount\":%u,\"xgetbvCount\":%u,\"xcr0\":\"%016llx\","
        "\"cpuidLeaf0\":[%u,%u,%u,%u],\"cpuidLeaf1\":[%u,%u,%u,%u],\"exit\":0}\n",
        (unsigned)args->caseId, args->runIdText,
        (unsigned long)g_c48Descriptor.pid, (unsigned long)g_c48Descriptor.tid,
        (unsigned long long)g_c48Descriptor.imageBase, (unsigned long long)result,
        blocksBefore, blocksAfter, (unsigned)g_c48Descriptor.cpuidCount,
        (unsigned)g_c48Descriptor.xgetbvCount, (unsigned long long)g_c48Descriptor.xcr0,
        (unsigned)g_c48Descriptor.cpuidLeaf0[0], (unsigned)g_c48Descriptor.cpuidLeaf0[1],
        (unsigned)g_c48Descriptor.cpuidLeaf0[2], (unsigned)g_c48Descriptor.cpuidLeaf0[3],
        (unsigned)g_c48Descriptor.cpuidLeaf1[0], (unsigned)g_c48Descriptor.cpuidLeaf1[1],
        (unsigned)g_c48Descriptor.cpuidLeaf1[2], (unsigned)g_c48Descriptor.cpuidLeaf1[3]);
    if (length < 0 || (size_t)length >= sizeof(json)) return 0;
    file = CreateFileA(args->output, GENERIC_WRITE, 0, NULL, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) return 0;
    if (!WriteFile(file, json, (DWORD)length, &written, NULL) || written != (DWORD)length) {
        CloseHandle(file);
        return 0;
    }
    return CloseHandle(file) != 0;
}
