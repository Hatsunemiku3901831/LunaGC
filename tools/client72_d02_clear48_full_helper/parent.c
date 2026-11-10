/* Bounded owned full-helper parent. Root build and independent review required. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "shared.h"
#include "catalog.generated.h"
#include "cases.generated.h"
#include "build-epoch.h"

/* The caller must supply bytes read from the stopped child, and the actual
 * CONTROL|INTEGER context. This function does not read, write, or execute. */
typedef struct C48FPreflight {
    uint32_t ordinal;
    uint64_t nextPc;
    uint32_t direction;
    uint64_t address;
    uint32_t width;
    uint64_t spanBase;
    uint32_t spanBytes;
} C48FPreflight;

static int c48f_condition(uint32_t condition, DWORD flags, int *taken)
{
    int carry = (flags & 1u) != 0;
    int zero = (flags & 0x40u) != 0;
    switch (condition) {
    case C48F_COND_A: *taken = !carry && !zero; return 1;
    case C48F_COND_B: *taken = carry; return 1;
    case C48F_COND_BE: *taken = carry || zero; return 1;
    case C48F_COND_NE: *taken = !zero; return 1;
    default: return 0;
    }
}

static int c48f_preflight(const C48FCaseConfig *config, uint32_t caseStep,
    const C48FullDescriptor *descriptor, uint64_t stackC, const CONTEXT *pre,
    const uint8_t *actualCode, C48FPreflight *out)
{
    const C48FInstruction *insn;
    const C48FControl *control = NULL;
    const C48FStore *store = NULL;
    uint64_t selected, expected, ea = 0;
    uint32_t ordinal, i;
    int taken;
    int32_t displacement;
    if (caseStep >= config->helperCount) return 0;
    ordinal = config->ordinals[caseStep];
    if (ordinal >= C48F_CATALOG_COUNT) return 0;
    insn = &g_c48fInstructions[ordinal];
    if (insn->ordinal != ordinal || !(insn->caseMask & config->caseMask) ||
        insn->size == 0 || insn->size > C48F_MAX_INSTRUCTION_BYTES ||
        pre->Rip != descriptor->arenaBase + insn->rva ||
        memcmp(actualCode, insn->code, insn->size)) return 0;
    memset(out, 0, sizeof(*out));
    out->ordinal = ordinal;
    if (caseStep + 1u < config->helperCount) {
        uint32_t nextOrdinal = config->ordinals[caseStep + 1u];
        if (nextOrdinal >= C48F_CATALOG_COUNT) return 0;
        expected = descriptor->arenaBase + g_c48fInstructions[nextOrdinal].rva;
    } else expected = descriptor->imageBase + descriptor->afterCallRva;
    switch (insn->flowKind) {
    case C48F_FLOW_SEQUENTIAL:
        selected = descriptor->arenaBase + insn->fallthroughRva;
        break;
    case C48F_FLOW_CONDITIONAL:
        if (!c48f_condition(insn->condition, pre->EFlags, &taken)) return 0;
        selected = descriptor->arenaBase + (taken ? insn->takenRva : insn->fallthroughRva);
        break;
    case C48F_FLOW_INDIRECT:
        selected = pre->R11;
        if (selected != descriptor->arenaBase + config->tailRva) return 0;
        break;
    case C48F_FLOW_RET:
        selected = descriptor->imageBase + descriptor->afterCallRva;
        break;
    default: return 0;
    }
    /* This comparison rejects 9a0 before aac/c80 can execute, even though
     * that address occupies the same physical RX page as the helper. */
    out->nextPc = selected;
    if (selected != expected) return 0;
    if (insn->operandDirection == C48F_OPERAND_NONE) return 1;
    for (i = 0; i < config->controlCount; ++i)
        if (config->controls[i].rva == insn->operandRva ||
            ((ordinal == 87u || ordinal == 163u) && config->controls[i].rva == config->tableRva))
            control = &config->controls[i];
    for (i = 0; i < config->storeCount; ++i)
        if (config->stores[i].ordinal == ordinal) store = &config->stores[i];
    if (control) {
        out->direction = insn->operandDirection; out->width = insn->operandWidth;
        if (ordinal == 87u || ordinal == 163u) {
            ea = pre->R10 + pre->R11 * 4u + (ordinal == 87u ? 0x06d190f0u : 0x06d19138u);
        } else {
            /* RIP-relative CMP/TEST: the immediate byte follows disp32 for
             * I/F; the other controls end in disp32. */
            uint32_t dispOffset = (ordinal == 51u || ordinal == 57u || ordinal == 136u) ? 2u : 3u;
            if (dispOffset + 4u > insn->size) return 0;
            memcpy(&displacement, actualCode + dispOffset, sizeof(displacement));
            ea = pre->Rip + insn->size + (int64_t)displacement;
        }
        out->address = ea;
        out->spanBase = descriptor->arenaBase + control->rva;
        out->spanBytes = control->width;
        if (((ordinal == 87u || ordinal == 163u) &&
             (pre->R10 != descriptor->arenaBase || pre->R11 != (ordinal == 87u ? 1u : 2u))) ||
            insn->operandDirection != C48F_OPERAND_READ || out->width != control->width || ea != out->spanBase) return 0;
    } else if (store) {
        out->direction = insn->operandDirection; out->width = insn->operandWidth;
        switch (ordinal) {
        case 97u: ea = pre->Rcx + pre->R8 - 0x20u; break;
        case 98u: ea = pre->Rax; break;
        case 172u: ea = pre->Rcx + pre->R9 - 0x20u; break;
        case 173u: ea = pre->Rcx + pre->R8 - 0x10u; break;
        case 174u: ea = pre->Rax; break;
        default: return 0;
        }
        out->address = ea;
        out->spanBase = descriptor->headBase + store->headOffset;
        out->spanBytes = store->width;
        if (insn->operandDirection != C48F_OPERAND_WRITE || out->width != store->width || ea != out->spanBase) return 0;
    } else if (insn->flowKind == C48F_FLOW_RET) {
        out->direction = insn->operandDirection; out->width = insn->operandWidth;
        ea = pre->Rsp;
        out->address = ea;
        out->spanBase = stackC - 8u;
        out->spanBytes = 8u;
        if (insn->operandDirection != C48F_OPERAND_READ || out->width != 8u || ea != out->spanBase) return 0;
    } else return 0;
    out->address = ea;
    return 1;
}


/* One bounded query per declared interval. A caller must record every result,
 * including a failed API call, before accepting or abandoning the run. */
typedef struct C48FPageRecord {
    const char *purpose;
    uint64_t address, allowedBase, allowedBytes;
    SIZE_T returnedBytes;
    DWORD errorIfZero;
    MEMORY_BASIC_INFORMATION mbi;
} C48FPageRecord;
typedef int (*C48FPageSink)(void *opaque, const C48FPageRecord *record);
typedef int (*C48FPageGuard)(void *opaque);

static int c48f_query_pages(HANDLE process, const C48FullDescriptor *d,
    uint64_t stackC, C48FPageGuard guard, C48FPageSink sink, void *opaque)
{
    static const uint32_t arenaStart[9] = {
        0,0x01fdf000u,0x01fe0000u,0x056af000u,0x056b0000u,
        0x05eee000u,0x05eef000u,0x06d19000u,0x06d1a000u
    };
    static const uint32_t arenaSize[9] = {
        0x01fdf000u,0x1000u,0x036cf000u,0x1000u,0x0083e000u,
        0x1000u,0x00e2a000u,0x1000u,0x6000u
    };
    static const char *names[13] = {"arena0","arena1","arena2","arena3","arena4",
        "arena5","arena6","arena7","arena8","buffer0","buffer1","buffer2","stack"};
    static const uint32_t bufferStart[3] = {0,0x1000u,0x2000u};
    static const uint32_t bufferSize[3] = {0x1000u,0x1000u,0xe000u};
    uint32_t i;
    for (i = 0; i < 13u; ++i) {
        C48FPageRecord r;
        uint64_t allocation;
        DWORD state, protect;
        int accepted;
        memset(&r, 0, sizeof(r));
        r.purpose = names[i];
        if (i < 9u) {
            allocation = d->arenaBase;
            r.allowedBase = allocation + arenaStart[i];
            r.allowedBytes = arenaSize[i];
            state = (i == 1u || i == 3u || i == 5u || i == 7u) ? MEM_COMMIT : MEM_RESERVE;
            protect = i == 1u ? PAGE_EXECUTE_READ : PAGE_READONLY;
        } else if (i < 12u) {
            allocation = d->bufferReserveBase;
            r.allowedBase = allocation + bufferStart[i - 9u];
            r.allowedBytes = bufferSize[i - 9u];
            state = i == 10u ? MEM_COMMIT : MEM_RESERVE;
            protect = PAGE_READWRITE;
        } else {
            allocation = 0;
            r.allowedBase = stackC - 8u;
            r.allowedBytes = 40u;
            state = MEM_COMMIT;
            protect = PAGE_READWRITE;
        }
        r.address = r.allowedBase;
        if (!guard(opaque)) return 0;
        r.returnedBytes = VirtualQueryEx(process, (LPCVOID)(uintptr_t)r.address, &r.mbi, sizeof(r.mbi));
        r.errorIfZero = r.returnedBytes ? 0 : GetLastError();
        accepted = r.returnedBytes == sizeof(r.mbi);
        if (accepted) {
            uint64_t base = (uint64_t)(uintptr_t)r.mbi.BaseAddress;
            uint64_t size = (uint64_t)r.mbi.RegionSize;
            accepted = r.mbi.State == state && base <= r.allowedBase &&
                r.allowedBase - base <= size && r.allowedBytes <= size - (r.allowedBase - base) &&
                (i == 12u || ((uint64_t)(uintptr_t)r.mbi.AllocationBase == allocation &&
                    base == r.allowedBase && size == r.allowedBytes)) &&
                (state != MEM_COMMIT || (r.mbi.Protect == protect));
        }
        if (!sink(opaque, &r) || !accepted) return 0;
    }
    return 1;
}

/* The enclosing ARM handler is responsible for exact-width reads, bootstrap
 * hashes and logging before it calls this pure identity/layout comparison. */
static int c48f_descriptor_matches(const C48FullBootstrap *b,
    const C48FullDescriptor *d, const C48FCaseConfig *config,
    const uint8_t runId[16], DWORD pid, DWORD tid, uint64_t imageBase,
    const CONTEXT *context)
{
    static const uint8_t zero[40] = {0};
    if (memcmp(d->magic, C48F_DESCRIPTOR_MAGIC, 8) || d->version != C48F_VERSION ||
        d->byteSize != sizeof(*d) || d->caseId != config->id || d->childState != C48F_STATE_READY ||
        memcmp(d->runId, runId, 16) || d->imageBase != imageBase || d->pid != pid || d->tid != tid ||
        d->armRva != b->armRva || d->callRva != b->callRva || d->afterCallRva != b->afterCallRva ||
        d->wrapperBeginRva != b->wrapperBeginRva || d->wrapperEndRva != b->wrapperEndRva ||
        d->sourceBytes != C48F_SOURCE_BYTES || d->arenaReserved != C48F_ARENA_RESERVED ||
        (d->arenaBase & 0xffffu) || d->arenaBase > UINT64_MAX - C48F_ARENA_RESERVED ||
        d->helperEntry != d->arenaBase + C48F_HELPER_RVA ||
        d->bufferReserveBytes != 65536u || (d->bufferReserveBase & 0xffffu) ||
        d->bufferReserveBase > UINT64_MAX - 65536u ||
        d->bufferPageBase != d->bufferReserveBase + 4096u || d->bufferKnownBase != d->bufferPageBase ||
        d->bufferKnownBytes != 128u || d->headBase != d->bufferKnownBase + 32u ||
        d->destination != d->headBase + 16u || (d->headBase & 31u) ||
        d->stackLow >= d->stackHigh || context->Rsp < d->stackLow ||
        context->Rsp - d->stackLow < 8u || context->Rsp > d->stackHigh ||
        d->stackHigh - context->Rsp < 32u || (context->Rsp & 15u) ||
        context->Rip != imageBase + b->callRva || context->R11 != d->helperEntry ||
        context->Rcx != d->destination || context->Rdx != 0 || context->R8 != 48u ||
        (context->EFlags & C48F_TF) || d->pageSize != 4096u || d->allocationGranularity != 65536u ||
        d->hostGateValid != 1u || d->cpuidCount != 2u || d->xgetbvCount != 1u ||
        d->cpuidLeaf0[0] < 1u || (d->cpuidLeaf1[2] & 0x1c000000u) != 0x1c000000u ||
        (d->xcr0 & 6u) != 6u || d->continuedToC || d->childResultReady || d->reservedU32 ||
        memcmp(d->reserved, zero, sizeof(d->reserved)) ||
        memcmp(d->sourceSHA256, b->sourceSHA256, 32) ||
        memcmp(d->catalogSHA256, b->catalogSHA256, 32) || memcmp(d->casesSHA256, b->casesSHA256, 32)) return 0;
    return 1;
}

typedef struct C48FWitnessState {
    uint8_t observed128[128];
    uint32_t storesCompleted;
    uint32_t controlsCompleted;
    uint32_t controlBytes;
    CONTEXT arm;
} C48FWitnessState;

/* Called only with actual bytes read at the stop. This checks the dynamic
 * overlap against the preceding observed write rather than a reconstructed
 * postimage. The caller owns the RPM ordinals and pending/continue state. */
static int c48f_operand_before(const C48FCaseConfig *config,
    const C48FullDescriptor *d, const C48FPreflight *plan,
    const C48FWitnessState *state, const uint8_t *actual)
{
    uint32_t i;
    if (!plan->width) return 1;
    if (g_c48fInstructions[plan->ordinal].flowKind == C48F_FLOW_RET) {
        uint64_t target;
        memcpy(&target, actual, 8);
        return target == d->imageBase + d->afterCallRva;
    }
    if (plan->direction == C48F_OPERAND_WRITE) {
        uint64_t offset = plan->address - d->bufferKnownBase;
        return offset <= 128u && plan->width <= 128u - offset &&
            !memcmp(actual, state->observed128 + offset, plan->width);
    }
    for (i = 0; i < config->controlCount; ++i) {
        const C48FControl *c = &config->controls[i];
        if (plan->address == d->arenaBase + c->rva)
            return plan->width == c->width && !memcmp(actual, c->bytes, c->width);
    }
    return 0;
}

static int c48f_post_matches(const C48FCaseConfig *config,
    const C48FullDescriptor *d, uint64_t stackC, const C48FPreflight *plan,
    const CONTEXT *post, const uint8_t *actualNew, C48FWitnessState *state)
{
    static const uint8_t zeros[32] = {0};
    uint32_t o = plan->ordinal;
    if (post->Rip != plan->nextPc || post->Rsp !=
        (g_c48fInstructions[o].flowKind == C48F_FLOW_RET ? stackC : stackC - 8u)) return 0;
    if ((o == 0u && post->Rax != d->destination) ||
        (o == 2u && post->R10 != d->arenaBase) ||
        ((o == 62u || o == 140u) && post->R9 != UINT64_MAX - 15u) ||
        ((o == 63u || o == 141u) && post->Rcx != d->headBase + 32u) ||
        ((o == 64u || o == 142u) && post->Rdx != 16u) ||
        ((o == 65u || o == 143u) && post->R8 != 32u) ||
        ((o == 87u || o == 163u) && post->R11 != config->tailRva) ||
        ((o == 88u || o == 164u) && post->R11 != d->arenaBase + config->tailRva)) return 0;
    if (g_c48fInstructions[o].flowKind == C48F_FLOW_RET &&
        (post->Rax != d->destination || post->Rbx != state->arm.Rbx ||
         post->Rbp != state->arm.Rbp || post->Rsi != state->arm.Rsi || post->Rdi != state->arm.Rdi ||
         post->R12 != state->arm.R12 || post->R13 != state->arm.R13 ||
         post->R14 != state->arm.R14 || post->R15 != state->arm.R15)) return 0;
    if (plan->direction == C48F_OPERAND_WRITE) {
        uint64_t offset = plan->address - d->bufferKnownBase;
        if (offset > 128u || plan->width > 128u - offset || plan->width > sizeof(zeros) ||
            memcmp(actualNew, zeros, plan->width)) return 0;
        memcpy(state->observed128 + offset, actualNew, plan->width);
        ++state->storesCompleted;
    } else if (plan->direction == C48F_OPERAND_READ &&
        g_c48fInstructions[o].flowKind != C48F_FLOW_RET) {
        ++state->controlsCompleted;
        state->controlBytes += plan->width;
    }
    return 1;
}

#include <bcrypt.h>
#include <wchar.h>

typedef struct C48FArguments {
    const wchar_t *child, *bootstrap, *helper, *runIdText, *output;
    uint32_t caseId;
    uint8_t runId[16];
} C48FArguments;

static int c48f_hex_value(wchar_t c)
{
    if (c >= L'0' && c <= L'9') return (int)(c - L'0');
    if (c >= L'a' && c <= L'f') return (int)(c - L'a') + 10;
    return -1;
}
static int c48f_absolute(const wchar_t *p)
{
    size_t n = wcslen(p);
    return n >= 3 && ((p[1] == L':' && (p[2] == L'\\' || p[2] == L'/')) ||
        (p[0] == L'\\' && p[1] == L'\\' && p[2] != 0));
}
static int c48f_arguments(int argc, wchar_t **argv, C48FArguments *a)
{
    size_t i;
    if (argc != 13 || wcscmp(argv[1], L"--child-exe") || wcscmp(argv[3], L"--bootstrap") ||
        wcscmp(argv[5], L"--helper") || wcscmp(argv[7], L"--case") ||
        wcscmp(argv[9], L"--run-id") || wcscmp(argv[11], L"--output") ||
        !c48f_absolute(argv[2]) || !c48f_absolute(argv[4]) || !c48f_absolute(argv[6]) ||
        !c48f_absolute(argv[12]) || wcschr(argv[2], L'"') || wcschr(argv[4], L'"') ||
        wcschr(argv[6], L'"') || wcschr(argv[12], L'"') ||
        (wcscmp(argv[8], L"257") && wcscmp(argv[8], L"258")) || wcslen(argv[10]) != 32) return 0;
    memset(a, 0, sizeof(*a));
    for (i = 0; i < 16; ++i) {
        int hi = c48f_hex_value(argv[10][2*i]), lo = c48f_hex_value(argv[10][2*i+1]);
        if (hi < 0 || lo < 0) return 0;
        a->runId[i] = (uint8_t)((hi << 4) | lo);
    }
    a->child = argv[2]; a->bootstrap = argv[4]; a->helper = argv[6];
    a->caseId = !wcscmp(argv[8], L"257") ? 257u : 258u;
    a->runIdText = argv[10]; a->output = argv[12];
    return 1;
}

static int c48f_digest_matches(const uint8_t digest[32], const char *hex)
{
    size_t i;
    if (strlen(hex) != 64) return 0;
    for (i = 0; i < 32; ++i) {
        int hi = c48f_hex_value((wchar_t)hex[2*i]);
        int lo = c48f_hex_value((wchar_t)hex[2*i+1]);
        if (hi < 0 || lo < 0 || digest[i] != (uint8_t)((hi << 4) | lo)) return 0;
    }
    return 1;
}

static int c48f_bootstrap_matches(const C48FullBootstrap *b)
{
    static const uint8_t zero[8] = {0};
    uint64_t image = b->imageBytes;
    return !memcmp(b->magic, C48F_BOOTSTRAP_MAGIC, 8) && b->version == C48F_VERSION &&
        b->byteSize == sizeof(*b) && b->allowedCaseMask == C48F_CASE_MASK &&
        b->descriptorBytes == sizeof(C48FullDescriptor) && b->helperRva == C48F_HELPER_RVA &&
        b->helperBytes == C48F_SOURCE_BYTES && b->arenaReserved == C48F_ARENA_RESERVED &&
        b->exitPolicyVersion == 2u && b->eflagsPolicyVersion == 2u && b->caseCount == C48F_CASE_COUNT &&
        b->wrapperBytes == C48F_WRAPPER_BYTES && !memcmp(b->reserved, zero, sizeof(zero)) &&
        c48f_digest_matches(b->sharedHeaderSHA256, C48F_SHARED_HEADER_SHA256_HEX) &&
        c48f_digest_matches(b->sourceSHA256, C48F_SOURCE_SHA256_HEX) &&
        c48f_digest_matches(b->catalogSHA256, C48F_CATALOG_SHA256_HEX) &&
        c48f_digest_matches(b->casesSHA256, C48F_CASES_SHA256_HEX) &&
        b->descriptorRva != 0 && (uint64_t)b->descriptorRva + sizeof(C48FullDescriptor) <= image &&
        b->wrapperBeginRva != 0 && (uint64_t)b->wrapperBeginRva + C48F_WRAPPER_BYTES <= image &&
        (uint64_t)b->wrapperBeginRva + C48F_ARM_OFFSET == b->armRva &&
        (uint64_t)b->wrapperBeginRva + C48F_CALL_OFFSET == b->callRva &&
        (uint64_t)b->wrapperBeginRva + C48F_AFTER_CALL_OFFSET == b->afterCallRva &&
        (uint64_t)b->wrapperBeginRva + C48F_WRAPPER_BYTES == b->wrapperEndRva;
}

static int c48f_sha256_handle(HANDLE file, uint8_t digest[32])
{
    BCRYPT_ALG_HANDLE algorithm = NULL;
    BCRYPT_HASH_HANDLE hash = NULL;
    DWORD objectBytes = 0, resultBytes = 0, readBytes = 0;
    LARGE_INTEGER zero;
    uint8_t *object = NULL;
    uint8_t buffer[16384];
    int ok = 0;
    zero.QuadPart = 0;
    if (!SetFilePointerEx(file, zero, NULL, FILE_BEGIN) ||
        BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM, NULL, 0) < 0 ||
        BCryptGetProperty(algorithm, BCRYPT_OBJECT_LENGTH, (PUCHAR)&objectBytes,
            (ULONG)sizeof(objectBytes), &resultBytes, 0) < 0 || resultBytes != sizeof(objectBytes)) goto done;
    object = (uint8_t *)HeapAlloc(GetProcessHeap(), 0, objectBytes);
    if (object == NULL || BCryptCreateHash(algorithm, &hash, object, objectBytes, NULL, 0, 0) < 0) goto done;
    for (;;) {
        if (!ReadFile(file, buffer, (DWORD)sizeof(buffer), &readBytes, NULL)) goto done;
        if (readBytes == 0) break;
        if (BCryptHashData(hash, buffer, readBytes, 0) < 0) goto done;
    }
    if (BCryptFinishHash(hash, digest, 32, 0) < 0) goto done;
    ok = 1;
done:
    if (hash != NULL && BCryptDestroyHash(hash) < 0) ok = 0;
    if (object != NULL && !HeapFree(GetProcessHeap(), 0, object)) ok = 0;
    if (algorithm != NULL && BCryptCloseAlgorithmProvider(algorithm, 0) < 0) ok = 0;
    return ok;
}

static int c48f_read_fixed_file(const wchar_t *path, void *bytes, DWORD length,
    uint8_t digest[32])
{
    HANDLE file = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL,
        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    LARGE_INTEGER size, zero;
    DWORD got = 0;
    int ok;
    if (file == INVALID_HANDLE_VALUE) return 0;
    zero.QuadPart = 0;
    ok = GetFileSizeEx(file, &size) && size.QuadPart == length &&
        c48f_sha256_handle(file, digest) && SetFilePointerEx(file, zero, NULL, FILE_BEGIN) &&
        ReadFile(file, bytes, length, &got, NULL) && got == length;
    if (!CloseHandle(file)) ok = 0;
    return ok;
}

/* Lifecycle behavior is adapted from the frozen main6 owned parent. */

#include <stdarg.h>
typedef struct C48Log {
    HANDLE file;
    uint64_t started;
    uint64_t sequence;
    DWORD pid;
    DWORD tid;
    DWORD eventType;
    uint64_t stopSeq;
    const char *phase;
    int failed;
} C48Log;

typedef struct C48Line {
    char text[16384];
    size_t length;
    int failed;
} C48Line;

static void line_add(C48Line *line, const char *format, ...)
{
    va_list ap;
    int n;
    if (line->failed) return;
    va_start(ap, format);
    n = vsnprintf(line->text + line->length, sizeof(line->text) - line->length, format, ap);
    va_end(ap);
    if (n < 0 || (size_t)n >= sizeof(line->text) - line->length) {
        line->failed = 1;
        return;
    }
    line->length += (size_t)n;
}

static void line_begin(C48Log *log, C48Line *line, const char *kind)
{
    uint64_t elapsed = (GetTickCount64() - log->started) * 1000u;
    memset(line, 0, sizeof(*line));
    ++log->sequence;
    line_add(line, "{\"kind\":\"%s\",\"seq\":%llu,\"phase\":\"%s\",",
        kind, (unsigned long long)log->sequence, log->phase);
    if (log->stopSeq != 0) {
        line_add(line, "\"eventPID\":%lu,\"eventTID\":%lu,\"eventType\":%lu,",
            (unsigned long)log->pid, (unsigned long)log->tid, (unsigned long)log->eventType);
    } else {
        line_add(line, "\"eventPID\":null,\"eventTID\":null,\"eventType\":null,");
    }
    line_add(line, "\"monotonicElapsedUs\":%llu", (unsigned long long)elapsed);
}

static int line_finish(C48Log *log, C48Line *line)
{
    DWORD written = 0;
    line_add(line, "}\n");
    if (line->failed || !WriteFile(log->file, line->text, (DWORD)line->length, &written, NULL) ||
        written != (DWORD)line->length) {
        log->failed = 1;
        return 0;
    }
    return 1;
}

static void line_hex(C48Line *line, const void *bytes, size_t count)
{
    static const char hex[] = "0123456789abcdef";
    const uint8_t *p = (const uint8_t *)bytes;
    size_t i;
    if (line->failed || count > (sizeof(line->text) - line->length - 1u) / 2u) {
        line->failed = 1;
        return;
    }
    for (i = 0; i < count; ++i) {
        line->text[line->length++] = hex[p[i] >> 4];
        line->text[line->length++] = hex[p[i] & 15u];
    }
    line->text[line->length] = 0;
}

static void line_control(C48Line *line, const CONTEXT *context)
{
    line_add(line, "{\"SegCs\":%u,\"SegSs\":%u,\"EFlags\":\"%08lx\","
        "\"Rsp\":\"%016llx\",\"Rip\":\"%016llx\"}",
        (unsigned)context->SegCs, (unsigned)context->SegSs, (unsigned long)context->EFlags,
        (unsigned long long)context->Rsp, (unsigned long long)context->Rip);
}

static void line_integer(C48Line *line, const CONTEXT *c)
{
    line_add(line, "{\"Rax\":\"%016llx\",\"Rbx\":\"%016llx\",\"Rcx\":\"%016llx\","
        "\"Rdx\":\"%016llx\",\"Rsi\":\"%016llx\",\"Rdi\":\"%016llx\","
        "\"Rbp\":\"%016llx\",\"R8\":\"%016llx\",\"R9\":\"%016llx\","
        "\"R10\":\"%016llx\",\"R11\":\"%016llx\",\"R12\":\"%016llx\","
        "\"R13\":\"%016llx\",\"R14\":\"%016llx\",\"R15\":\"%016llx\"}",
        (unsigned long long)c->Rax, (unsigned long long)c->Rbx, (unsigned long long)c->Rcx,
        (unsigned long long)c->Rdx, (unsigned long long)c->Rsi, (unsigned long long)c->Rdi,
        (unsigned long long)c->Rbp, (unsigned long long)c->R8, (unsigned long long)c->R9,
        (unsigned long long)c->R10, (unsigned long long)c->R11, (unsigned long long)c->R12,
        (unsigned long long)c->R13, (unsigned long long)c->R14, (unsigned long long)c->R15);
}

static int integer_equal(const CONTEXT *a, const CONTEXT *b)
{
    return a->Rax == b->Rax && a->Rbx == b->Rbx && a->Rcx == b->Rcx &&
        a->Rdx == b->Rdx && a->Rsi == b->Rsi && a->Rdi == b->Rdi &&
        a->Rbp == b->Rbp && a->R8 == b->R8 && a->R9 == b->R9 &&
        a->R10 == b->R10 && a->R11 == b->R11 && a->R12 == b->R12 &&
        a->R13 == b->R13 && a->R14 == b->R14 && a->R15 == b->R15;
}

typedef struct C48Thread {
    DWORD tid;
    int state; /* 1: creation observed, 2: live, 3: exited */
} C48Thread;

typedef struct C48Run {
    C48Log log;
    PROCESS_INFORMATION pi;
    C48FullBootstrap bootstrap;
    C48FullDescriptor descriptor;
    uint8_t sourceChildSHA[32];
    uint8_t sourceParentSHA[32];
    uint8_t sourceHelperSHA[32];
    uint8_t sourceBootstrapSHA[32];
    uint8_t sourceHeaderSHA[32];
    uint64_t sourceImageBytes;
    uint64_t imageBase;
    uint64_t stackC;
    uint32_t caseId;
    uint32_t eventCount;
    uint32_t waitCount;
    uint32_t getCount;
    uint32_t setCount;
    uint32_t readCount;
    uint32_t readBytes;
    uint32_t pageCount;
    uint32_t continueCount;
    uint32_t retired;
    uint32_t helperControlReads, helperControlReadBytes, helperStoreEvents;
    uint64_t returnRax, returnRsp;
    int returned;
    int originalTf;
    const char *failure;
    DEBUG_EVENT event;
    int outstanding;
    int systemBreakpointSeen;
    int createProcessSeen;
    int pendingValid;
    uint64_t ntdllBase;
    uint64_t ntdllEnd;
    int killAccepted;
    int killAttempted;
    int childCreated;
    int epochValidated;
    int pendingRetired;
    int pendingRetirementKnown;
    int firstHadException;
    int firstHadContext;
    int terminateOk;
    int exitSeen;
    int exitContinued;
    int cleanupInconsistent;
    C48Thread threads[C48F_MAX_EVENTS];
    uint32_t threadCount;
    int eventLifecycleError;
    uint64_t exitEventSeq;
    DWORD exitEventTID;
    DWORD eventExitCode;
    int processSignaled;
    int getExitOk;
    DWORD childExit;
    uint32_t cleanupContinues;
    CONTEXT lastContext;
    uint64_t lastContextStop;
    uint64_t firstFailureStop;
    DWORD firstRawCode;
    uint64_t firstExceptionAddress;
    uint64_t firstContextRip;
    uint32_t pendingId;
    int processCloseOk;
    int threadCloseOk;
} C48Run;

static int fail_run(C48Run *run, const char *failure)
{
    if (run->failure == NULL) {
        run->failure = failure;
        run->log.phase = "FAILED_STOP";
        run->firstFailureStop = run->log.stopSeq;
        if (run->outstanding && run->event.dwDebugEventCode == EXCEPTION_DEBUG_EVENT) {
            run->firstHadException = 1;
            run->firstRawCode = run->event.u.Exception.ExceptionRecord.ExceptionCode;
            run->firstExceptionAddress = (uint64_t)(uintptr_t)run->event.u.Exception.ExceptionRecord.ExceptionAddress;
        }
        if (run->lastContextStop == run->log.stopSeq && run->log.stopSeq != 0) {
            run->firstHadContext = 1;
            run->firstContextRip = run->lastContext.Rip;
        }
    }
    return 0;
}

static int within_wall(C48Run *run)
{
    return GetTickCount64() - run->log.started < C48F_WALL_MS ? 1 : fail_run(run, "wallBudget");
}

static int get_context(C48Run *run, CONTEXT *context)
{
    C48Line line;
    BOOL ok;
    DWORD error;
    if (!within_wall(run)) return 0;
    if (run->getCount >= (run->caseId == C48F_CASE_SSE_LONG ? 79u : 85u)) return fail_run(run, "getBudget");
    ++run->getCount;
    memset(context, 0, sizeof(*context));
    context->ContextFlags = CONTEXT_CONTROL | CONTEXT_INTEGER;
    ok = GetThreadContext(run->pi.hThread, context);
    error = ok ? 0 : GetLastError();
    if (ok) {
        run->lastContext = *context;
        run->lastContextStop = run->log.stopSeq;
    }
    line_begin(&run->log, &line, "get");
    line_add(&line, ",\"apiOrdinal\":%u,\"requestedGroups\":[\"CONTROL\",\"INTEGER\"],"
        "\"BOOL\":%s,\"lastErrorIfFalse\":", run->getCount, ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
    line_add(&line, ",\"threadHandleOrigin\":\"CreateProcess.PI.hThread\",\"stopEventSeq\":%llu,\"control\":",
        (unsigned long long)run->log.stopSeq);
    if (ok) line_control(&line, context); else line_add(&line, "null");
    line_add(&line, ",\"integer\":");
    if (ok) line_integer(&line, context); else line_add(&line, "null");
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    if (!ok) return fail_run(run, "GetThreadContext");
    return 1;
}

static int set_tf(C48Run *run, const CONTEXT *before, int tf, CONTEXT *readback)
{
    C48Line line;
    CONTEXT submitted;
    BOOL ok;
    DWORD error, eflagsXor = 0;
    const char *eflagsRepresentation = NULL;
    uint32_t setOrdinal, readbackOrdinal = 0, beforeGet;
    int unchanged = 0, got = 0, eflagsAccepted = 0;
    if (!within_wall(run)) return 0;
    if (run->setCount >= (run->caseId == C48F_CASE_SSE_LONG ? 39u : 42u)) return fail_run(run, "setBudget");
    ++run->setCount;
    setOrdinal = run->setCount;
    memset(&submitted, 0, sizeof(submitted));
    submitted.ContextFlags = CONTEXT_CONTROL;
    submitted.SegCs = before->SegCs;
    submitted.SegSs = before->SegSs;
    submitted.Rsp = before->Rsp;
    submitted.Rip = before->Rip;
    submitted.EFlags = (before->EFlags & ~C48F_TF) | (tf ? C48F_TF : 0u);
    ok = SetThreadContext(run->pi.hThread, &submitted);
    error = ok ? 0 : GetLastError();
    if (ok) {
        beforeGet = run->getCount;
        got = get_context(run, readback);
        if (run->getCount != beforeGet) readbackOrdinal = run->getCount;
        if (got) {
            eflagsXor = submitted.EFlags ^ readback->EFlags;
            if (eflagsXor == 0) {
                eflagsRepresentation = "exact";
                eflagsAccepted = 1;
            } else if ((submitted.EFlags & 2u) != 0 && eflagsXor == 2u) {
                eflagsRepresentation = "reserved_bit1_1_to_0";
                eflagsAccepted = 1;
            } else {
                eflagsRepresentation = "rejected";
            }
            unchanged = integer_equal(before, readback) &&
                readback->SegCs == submitted.SegCs && readback->SegSs == submitted.SegSs &&
                readback->Rsp == submitted.Rsp && readback->Rip == submitted.Rip &&
                eflagsAccepted;
        }
    }
    line_begin(&run->log, &line, "set");
    line_add(&line, ",\"apiOrdinal\":%u,\"stopEventSeq\":%llu,\"requestedGroups\":[\"CONTROL\"],"
        "\"beforeControl\":", setOrdinal, (unsigned long long)run->log.stopSeq);
    line_control(&line, before);
    line_add(&line, ",\"submittedControl\":");
    line_control(&line, &submitted);
    line_add(&line, ",\"BOOL\":%s,\"lastErrorIfFalse\":", ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
    line_add(&line, ",\"readbackGetOrdinal\":");
    if (readbackOrdinal) line_add(&line, "%u", readbackOrdinal); else line_add(&line, "null");
    line_add(&line, ",\"readbackControl\":");
    if (got) line_control(&line, readback); else line_add(&line, "null");
    line_add(&line, ",\"eflagsReadbackPolicy\":\"c48-tf-only-reserved-bit1-readback-v2\","
        "\"eflagsReadbackXor\":");
    if (got) line_add(&line, "\"%08lx\"", (unsigned long)eflagsXor); else line_add(&line, "null");
    line_add(&line, ",\"eflagsReadbackRepresentation\":");
    if (got) line_add(&line, "\"%s\"", eflagsRepresentation); else line_add(&line, "null");
    line_add(&line, ",\"integerUnchanged\":");
    if (got) line_add(&line, "%s", integer_equal(before, readback) ? "true" : "false");
    else line_add(&line, "null");
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    if (!ok) return fail_run(run, "SetThreadContext");
    if (!got || !unchanged) return fail_run(run, "setReadbackMismatch");
    return 1;
}


static int read_exact_at(HANDLE file, uint64_t offset, void *bytes, DWORD width)
{
    LARGE_INTEGER position;
    DWORD count = 0;
    position.QuadPart = (LONGLONG)offset;
    return SetFilePointerEx(file, position, NULL, FILE_BEGIN) &&
        ReadFile(file, bytes, width, &count, NULL) && count == width;
}


static void line_wide_json(C48Line *line, const wchar_t *value)
{
    char utf8[4096];
    int count, i;
    count = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, value, -1, utf8, (int)sizeof(utf8), NULL, NULL);
    if (!count) { line->failed = 1; return; }
    line_add(line, "\"");
    for (i = 0; i < count - 1; ++i) {
        unsigned char c = (unsigned char)utf8[i];
        if (c == '\\' || c == '"') line_add(line, "\\%c", c);
        else if (c < 0x20) line_add(line, "\\u%04x", (unsigned)c);
        else line_add(line, "%c", c);
    }
    line_add(line, "\"");
}

static uint64_t image_size_from_handle(HANDLE file)
{
    IMAGE_DOS_HEADER dos;
    IMAGE_NT_HEADERS64 nt;
    LARGE_INTEGER size;
    if (!GetFileSizeEx(file, &size) || !read_exact_at(file, 0, &dos, (DWORD)sizeof(dos)) ||
        dos.e_magic != IMAGE_DOS_SIGNATURE || dos.e_lfanew < 0 ||
        (uint64_t)dos.e_lfanew + sizeof(nt) > (uint64_t)size.QuadPart ||
        !read_exact_at(file, (uint64_t)dos.e_lfanew, &nt, (DWORD)sizeof(nt)) ||
        nt.Signature != IMAGE_NT_SIGNATURE || nt.FileHeader.Machine != IMAGE_FILE_MACHINE_AMD64 ||
        nt.OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR64_MAGIC) return 0;
    return nt.OptionalHeader.SizeOfImage;
}

static C48Thread *find_thread(C48Run *run, DWORD tid)
{
    uint32_t i;
    for (i = 0; i < run->threadCount; ++i)
        if (run->threads[i].tid == tid) return &run->threads[i];
    return NULL;
}

/* Observe each actual creation once, even when its phase will reject the event. */
static void observe_thread_origin(C48Run *run)
{
    DEBUG_EVENT *ev = &run->event;
    HANDLE handle;
    int creation = ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT ||
        ev->dwDebugEventCode == CREATE_THREAD_DEBUG_EVENT;
    run->eventLifecycleError = 0;
    if (!creation || ev->dwProcessId != run->pi.dwProcessId) return;
    handle = ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT ?
        ev->u.CreateProcessInfo.hThread : ev->u.CreateThread.hThread;
    if (!ev->dwThreadId || find_thread(run, ev->dwThreadId) ||
        run->threadCount >= C48F_MAX_EVENTS || handle == INVALID_HANDLE_VALUE ||
        (ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT &&
         (ev->dwThreadId != run->pi.dwThreadId ||
          ev->u.CreateProcessInfo.hProcess == INVALID_HANDLE_VALUE))) {
        run->eventLifecycleError = 1;
        run->cleanupInconsistent = 1;
        return;
    }
    run->threads[run->threadCount].tid = ev->dwThreadId;
    run->threads[run->threadCount++].state = 1;
}

static int known_live_thread(C48Run *run)
{
    C48Thread *thread = find_thread(run, run->event.dwThreadId);
    return thread != NULL && thread->state == 2;
}

static int log_event(C48Run *run)
{
    C48Line line;
    DEBUG_EVENT *ev = &run->event;
    HANDLE file = NULL;
    uint64_t moduleBase = 0, moduleBytes = 0;
    wchar_t path[2048];
    DWORD pathLength = 0, pathError = 0, closeError = 0, infoError = 0;
    BY_HANDLE_FILE_INFORMATION info;
    BOOL infoOk = FALSE, closeOk = FALSE;
    DWORD i;
    int ntdll = 0, duplicateNtdll = 0, fileHashOk = 0;
    int owned = ev->dwProcessId == run->pi.dwProcessId;
    uint8_t fileHash[32];
    memset(&info, 0, sizeof(info));
    path[0] = 0;
    if (ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT) {
        file = ev->u.CreateProcessInfo.hFile;
        moduleBase = (uint64_t)(uintptr_t)ev->u.CreateProcessInfo.lpBaseOfImage;
    } else if (ev->dwDebugEventCode == LOAD_DLL_DEBUG_EVENT) {
        file = ev->u.LoadDll.hFile;
        moduleBase = (uint64_t)(uintptr_t)ev->u.LoadDll.lpBaseOfDll;
    }
    if (owned && file != NULL && file != INVALID_HANDLE_VALUE) {
        const wchar_t *base;
        pathLength = GetFinalPathNameByHandleW(file, path, (DWORD)(sizeof(path) / sizeof(path[0])), FILE_NAME_NORMALIZED);
        if (pathLength == 0 || pathLength >= sizeof(path) / sizeof(path[0])) {
            if (pathLength == 0) pathError = GetLastError();
            path[0] = 0;
        } else {
            base = wcsrchr(path, L'\\');
            ntdll = base != NULL && _wcsicmp(base + 1, L"ntdll.dll") == 0;
        }
        infoOk = GetFileInformationByHandle(file, &info);
        if (!infoOk) infoError = GetLastError();
        moduleBytes = image_size_from_handle(file);
        if (ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT) fileHashOk = c48f_sha256_handle(file, fileHash);
        closeOk = CloseHandle(file);
        if (!closeOk) closeError = GetLastError();
        if (ev->dwDebugEventCode == LOAD_DLL_DEBUG_EVENT && !strcmp(run->log.phase, "BOOT") &&
            !run->systemBreakpointSeen && ntdll && infoOk && closeOk && moduleBytes &&
            moduleBase && moduleBase <= UINT64_MAX - moduleBytes) {
            duplicateNtdll = run->ntdllBase != 0;
            if (!duplicateNtdll) {
                run->ntdllBase = moduleBase;
                run->ntdllEnd = moduleBase + moduleBytes;
            }
        }
    }
    line_begin(&run->log, &line, "event");
    run->log.stopSeq = run->log.sequence;
    if (owned && ev->dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT) {
        run->exitSeen = 1;
        run->eventExitCode = ev->u.ExitProcess.dwExitCode;
        run->exitEventSeq = run->log.stopSeq;
        run->exitEventTID = ev->dwThreadId;
    }
    line_add(&line, ",\"rawDebugEventCode\":%lu,\"firstChance\":", (unsigned long)ev->dwDebugEventCode);
    if (ev->dwDebugEventCode == EXCEPTION_DEBUG_EVENT) {
        const EXCEPTION_DEBUG_INFO *x = &ev->u.Exception;
        line_add(&line, "%s,\"exceptionCode\":%lu,\"exceptionAddress\":\"%016llx\","
            "\"exceptionFlags\":%lu,\"exceptionParameters\":[", x->dwFirstChance ? "true" : "false",
            (unsigned long)x->ExceptionRecord.ExceptionCode,
            (unsigned long long)(uintptr_t)x->ExceptionRecord.ExceptionAddress,
            (unsigned long)x->ExceptionRecord.ExceptionFlags);
        for (i = 0; i < x->ExceptionRecord.NumberParameters && i < EXCEPTION_MAXIMUM_PARAMETERS; ++i) {
            if (i) line_add(&line, ",");
            line_add(&line, "\"%016llx\"", (unsigned long long)x->ExceptionRecord.ExceptionInformation[i]);
        }
        line_add(&line, "],\"exceptionParameterCount\":%lu", (unsigned long)x->ExceptionRecord.NumberParameters);
    } else line_add(&line, "null,\"exceptionCode\":null,\"exceptionAddress\":null,\"exceptionFlags\":null,\"exceptionParameters\":null,\"exceptionParameterCount\":null");
    line_add(&line, ",\"continueStatus\":null,\"ContinueDebugEventBOOL\":null,\"continueRecordStatus\":\"separate\"");
    if (ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT || ev->dwDebugEventCode == LOAD_DLL_DEBUG_EVENT) {
        line_add(&line, ",\"moduleBase\":\"%016llx\",\"moduleImageBytes\":", (unsigned long long)moduleBase);
        if (moduleBytes) line_add(&line, "\"%016llx\"", (unsigned long long)moduleBytes); else line_add(&line, "null");
        line_add(&line, ",\"fileHandleRaw\":\"%016llx\",\"fileHandleOrigin\":\"%s\",\"fileHandleValid\":%s,\"filePath\":",
            (unsigned long long)(uintptr_t)file, ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT ? "CREATE_PROCESS.hFile" : "LOAD_DLL.hFile",
            file && file != INVALID_HANDLE_VALUE ? "true" : "false");
        if (path[0]) line_wide_json(&line, path); else line_add(&line, "null");
        line_add(&line, ",\"filePathError\":");
        if (owned && pathLength == 0 && file && file != INVALID_HANDLE_VALUE) line_add(&line, "%lu", (unsigned long)pathError);
        else line_add(&line, "null");
        line_add(&line, ",\"filePathRequiredLength\":");
        if (owned && file && file != INVALID_HANDLE_VALUE) line_add(&line, "%lu", (unsigned long)pathLength);
        else if (owned) line_add(&line, "0"); else line_add(&line, "null");
        line_add(&line, ",\"fileIdentityBOOL\":");
        if (owned && file && file != INVALID_HANDLE_VALUE) line_add(&line, "%s", infoOk ? "true" : "false");
        else line_add(&line, "null");
        line_add(&line, ",\"fileIdentityError\":");
        if (owned && file && file != INVALID_HANDLE_VALUE && !infoOk) line_add(&line, "%lu", (unsigned long)infoError);
        else line_add(&line, "null");
        line_add(&line, ",\"fileIdentity\":");
        if (infoOk) line_add(&line, "{\"volumeSerial\":%lu,\"indexHigh\":%lu,\"indexLow\":%lu}",
            (unsigned long)info.dwVolumeSerialNumber, (unsigned long)info.nFileIndexHigh, (unsigned long)info.nFileIndexLow);
        else line_add(&line, "null");
        line_add(&line, ",\"fileCloseBOOL\":");
        if (owned && file && file != INVALID_HANDLE_VALUE) line_add(&line, "%s", closeOk ? "true" : "false");
        else line_add(&line, "null");
        line_add(&line, ",\"fileCloseError\":");
        if (owned && file && file != INVALID_HANDLE_VALUE && !closeOk) line_add(&line, "%lu", (unsigned long)closeError);
        else line_add(&line, "null");
        if (ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT) {
            line_add(&line, ",\"debugProcessHandleRaw\":\"%016llx\",\"debugProcessHandleValid\":%s,"
                "\"debugThreadHandleRaw\":\"%016llx\",\"debugThreadHandleValid\":%s,"
                "\"debugProcessHandleOrigin\":\"CREATE_PROCESS.borrowed\","
                "\"debugThreadHandleOrigin\":\"CREATE_PROCESS.borrowed\",\"startAddress\":\"%016llx\","
                "\"threadLocalBase\":\"%016llx\"",
                (unsigned long long)(uintptr_t)ev->u.CreateProcessInfo.hProcess,
                ev->u.CreateProcessInfo.hProcess && ev->u.CreateProcessInfo.hProcess != INVALID_HANDLE_VALUE ? "true" : "false",
                (unsigned long long)(uintptr_t)ev->u.CreateProcessInfo.hThread,
                ev->u.CreateProcessInfo.hThread && ev->u.CreateProcessInfo.hThread != INVALID_HANDLE_VALUE ? "true" : "false",
                (unsigned long long)(uintptr_t)ev->u.CreateProcessInfo.lpStartAddress,
                (unsigned long long)(uintptr_t)ev->u.CreateProcessInfo.lpThreadLocalBase);
        }
        line_add(&line, ",\"ntdllNameMatch\":%s,\"fileSHA256\":", owned ? (ntdll ? "true" : "false") : "null");
        if (fileHashOk) { line_add(&line, "\""); line_hex(&line, fileHash, 32); line_add(&line, "\""); }
        else line_add(&line, "null");
    }
    if (ev->dwDebugEventCode == CREATE_THREAD_DEBUG_EVENT)
        line_add(&line, ",\"debugThreadHandleRaw\":\"%016llx\",\"debugThreadHandleValid\":%s,"
            "\"debugThreadHandleOrigin\":\"CREATE_THREAD.borrowed\",\"startAddress\":\"%016llx\",\"threadLocalBase\":\"%016llx\"",
            (unsigned long long)(uintptr_t)ev->u.CreateThread.hThread,
            ev->u.CreateThread.hThread && ev->u.CreateThread.hThread != INVALID_HANDLE_VALUE ? "true" : "false",
            (unsigned long long)(uintptr_t)ev->u.CreateThread.lpStartAddress,
            (unsigned long long)(uintptr_t)ev->u.CreateThread.lpThreadLocalBase);
    if (ev->dwDebugEventCode == EXIT_THREAD_DEBUG_EVENT)
        line_add(&line, ",\"exitCode\":%lu", (unsigned long)ev->u.ExitThread.dwExitCode);
    if (ev->dwDebugEventCode == UNLOAD_DLL_DEBUG_EVENT)
        line_add(&line, ",\"moduleBase\":\"%016llx\"", (unsigned long long)(uintptr_t)ev->u.UnloadDll.lpBaseOfDll);
    if (ev->dwDebugEventCode == OUTPUT_DEBUG_STRING_EVENT)
        line_add(&line, ",\"debugStringAddress\":\"%016llx\",\"debugStringUnicode\":%u,\"debugStringLength\":%u",
            (unsigned long long)(uintptr_t)ev->u.DebugString.lpDebugStringData,
            (unsigned)ev->u.DebugString.fUnicode, (unsigned)ev->u.DebugString.nDebugStringLength);
    if (ev->dwDebugEventCode == RIP_EVENT)
        line_add(&line, ",\"ripError\":%lu,\"ripType\":%lu", (unsigned long)ev->u.RipInfo.dwError,
            (unsigned long)ev->u.RipInfo.dwType);
    if (ev->dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT)
        line_add(&line, ",\"exitCode\":%lu", (unsigned long)ev->u.ExitProcess.dwExitCode);
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    if (!owned) { run->cleanupInconsistent = 1; return fail_run(run, "unknownPidOrThread"); }
    if (run->eventLifecycleError) return fail_run(run, "threadLifecycle");
    if (file && file != INVALID_HANDLE_VALUE && !closeOk) return fail_run(run, "eventFileClose");
    if (file && file != INVALID_HANDLE_VALUE && (!infoOk || !path[0] || !moduleBytes))
        return fail_run(run, "eventFileIdentity");
    if (duplicateNtdll) return fail_run(run, "ambiguousSystemBreakpointModule");
    if (ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT &&
        (!fileHashOk || memcmp(fileHash, run->bootstrap.childExeSHA256, 32) != 0 ||
        moduleBytes != run->bootstrap.imageBytes)) return fail_run(run, "createdImageMismatch");
    if (ev->dwDebugEventCode == EXCEPTION_DEBUG_EVENT && ev->u.Exception.ExceptionRecord.NumberParameters > EXCEPTION_MAXIMUM_PARAMETERS)
        return fail_run(run, "exceptionParameters");
    return 1;
}

static int wait_event(C48Run *run)
{
    C48Line line;
    BOOL ok;
    DWORD error, slice;
    uint64_t elapsed;
    if (run->eventCount >= C48F_MAX_EVENTS) return fail_run(run, "eventBudget");
    elapsed = GetTickCount64() - run->log.started;
    if (elapsed >= C48F_WALL_MS) return fail_run(run, "wallBudget");
    slice = (DWORD)(C48F_WALL_MS - elapsed);
    if (slice > C48F_WAIT_MS) slice = C48F_WAIT_MS;
    memset(&run->event, 0, sizeof(run->event));
    ++run->waitCount;
    ok = WaitForDebugEvent(&run->event, slice);
    error = ok ? 0 : GetLastError();
    run->log.stopSeq = 0;
    if (ok) {
        ++run->eventCount;
        run->outstanding = 1;
        run->log.pid = run->event.dwProcessId;
        run->log.tid = run->event.dwThreadId;
        run->log.eventType = run->event.dwDebugEventCode;
        run->log.stopSeq = run->log.sequence + 2u;
        observe_thread_origin(run);
    }
    line_begin(&run->log, &line, "wait");
    line_add(&line, ",\"apiOrdinal\":%u,\"BOOL\":%s,\"lastErrorIfFalse\":", run->waitCount, ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
    line_add(&line, ",\"timeoutMs\":%lu,\"stopEventSeq\":", (unsigned long)slice);
    if (ok) line_add(&line, "%llu", (unsigned long long)(run->log.sequence + 1u));
    else line_add(&line, "null");
    if (!line_finish(&run->log, &line)) {
        fail_run(run, "writeEvents");
        if (ok) { run->log.stopSeq = run->log.sequence + 1u; (void)log_event(run); }
        return 0;
    }
    if (!ok) {
        if (error == ERROR_SEM_TIMEOUT) return 2;
        return fail_run(run, "WaitForDebugEvent");
    }
    run->log.stopSeq = run->log.sequence + 1u;
    return log_event(run);
}

static int continue_event(C48Run *run, DWORD status, int cleanup)
{
    C48Line line;
    BOOL ok;
    DWORD error;
    if (!cleanup && !within_wall(run)) return 0;
    ++run->continueCount;
    ok = ContinueDebugEvent(run->event.dwProcessId, run->event.dwThreadId, status);
    error = ok ? 0 : GetLastError();
    line_begin(&run->log, &line, "continue");
    line_add(&line, ",\"apiOrdinal\":%u,\"stopEventSeq\":%llu,\"continueStatus\":%lu,"
        "\"BOOL\":%s,\"lastErrorIfFalse\":", run->continueCount, (unsigned long long)run->log.stopSeq,
        (unsigned long)status, ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
    line_add(&line, ",\"cleanupOnly\":%s", cleanup ? "true" : "false");
    if (cleanup) ++run->cleanupContinues;
    if (ok) {
        C48Thread *thread = find_thread(run, run->event.dwThreadId);
        DWORD code = run->event.dwDebugEventCode;
        run->outstanding = 0;
        if ((code == CREATE_PROCESS_DEBUG_EVENT || code == CREATE_THREAD_DEBUG_EVENT) &&
            !run->eventLifecycleError && thread && thread->state == 1) thread->state = 2;
        else if (code == EXIT_THREAD_DEBUG_EVENT && thread && thread->state == 2) thread->state = 3;
        else if (code == EXIT_PROCESS_DEBUG_EVENT) {
            uint32_t i;
            run->exitContinued = 1;
            for (i = 0; i < run->threadCount; ++i) run->threads[i].state = 3;
        }
    }
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    if (!ok) return fail_run(run, "ContinueDebugEvent");
    return 1;
}


static int log_api(C48Run *run, const char *name, BOOL ok, DWORD error, const char *origin)
{
    C48Line line;
    line_begin(&run->log, &line, "cleanup");
    line_add(&line, ",\"api\":\"%s\",\"handleOrigin\":\"%s\",\"BOOL\":%s,\"lastErrorIfFalse\":",
        name, origin, ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
    if (strcmp(origin, "CreateProcess.PI.hProcess") == 0)
        line_add(&line, ",\"handleRaw\":\"%016llx\"", (unsigned long long)(uintptr_t)run->pi.hProcess);
    else if (strcmp(origin, "CreateProcess.PI.hThread") == 0)
        line_add(&line, ",\"handleRaw\":\"%016llx\"", (unsigned long long)(uintptr_t)run->pi.hThread);
    if (strcmp(name, "TerminateProcess") == 0)
        line_add(&line, ",\"requestedExitCode\":%u", C48F_ABORT_EXIT);
    if (strcmp(name, "GetExitCodeProcess") == 0) {
        line_add(&line, ",\"exitCode\":");
        if (ok) line_add(&line, "%lu", (unsigned long)run->childExit); else line_add(&line, "null");
    }
    return line_finish(&run->log, &line) ? 1 : fail_run(run, "writeEvents");
}

static int cleanup_run(C48Run *run, int traceDone)
{
    BOOL ok;
    DWORD error, waitResult, timeout;
    uint64_t elapsed;
    int next;
    if (!traceDone) {
        run->log.phase = "TERMINATING";
        ok = TerminateProcess(run->pi.hProcess, C48F_ABORT_EXIT);
        error = ok ? 0 : GetLastError();
        run->terminateOk = ok != FALSE;
        (void)log_api(run, "TerminateProcess", ok, error, "CreateProcess.PI.hProcess");
        if (run->terminateOk) {
            int original = run->outstanding;
            while (!run->exitContinued) {
                DWORD code;
                if (!run->outstanding) {
                    next = wait_event(run);
                    original = 0;
                    if (next == 2) continue;
                    if (!next) {
                        run->cleanupInconsistent = 1;
                        if (!run->outstanding) break;
                    }
                }
                if (run->event.dwProcessId != run->pi.dwProcessId) {
                    run->cleanupInconsistent = 1;
                    break;
                }
                code = run->event.dwDebugEventCode;
                if (code == CREATE_THREAD_DEBUG_EVENT ||
                    (original && code == CREATE_PROCESS_DEBUG_EVENT)) {
                    if (run->eventLifecycleError) run->cleanupInconsistent = 1;
                } else if (code == EXIT_THREAD_DEBUG_EVENT || code == EXIT_PROCESS_DEBUG_EVENT ||
                    code == LOAD_DLL_DEBUG_EVENT || code == UNLOAD_DLL_DEBUG_EVENT ||
                    code == OUTPUT_DEBUG_STRING_EVENT) {
                    if (!known_live_thread(run)) run->cleanupInconsistent = 1;
                } else if (!(original && code == EXCEPTION_DEBUG_EVENT && run->failure &&
                    run->firstFailureStop == run->log.stopSeq)) {
                    run->cleanupInconsistent = 1;
                }
                if (!continue_event(run, DBG_CONTINUE, 1) && run->outstanding) break;
                original = 0;
            }
        }
    }

    if (run->exitContinued) {
        elapsed = GetTickCount64() - run->log.started;
        timeout = elapsed >= C48F_WALL_MS ? 0u : (DWORD)(C48F_WALL_MS - elapsed);
        waitResult = WaitForSingleObject(run->pi.hProcess, timeout);
        error = waitResult == WAIT_FAILED ? GetLastError() : 0;
        {
            C48Line line;
            line_begin(&run->log, &line, "cleanup");
            line_add(&line, ",\"api\":\"WaitForSingleObject\",\"handleOrigin\":\"CreateProcess.PI.hProcess\","
                "\"handleRaw\":\"%016llx\",\"timeoutMs\":%lu,\"result\":%lu,\"lastErrorIfFailed\":",
                (unsigned long long)(uintptr_t)run->pi.hProcess, (unsigned long)timeout, (unsigned long)waitResult);
            if (waitResult == WAIT_FAILED) line_add(&line, "%lu", (unsigned long)error);
            else line_add(&line, "null");
            (void)line_finish(&run->log, &line);
        }
        run->processSignaled = waitResult == WAIT_OBJECT_0;
        if (run->processSignaled) {
            ok = GetExitCodeProcess(run->pi.hProcess, &run->childExit);
            error = ok ? 0 : GetLastError();
            run->getExitOk = ok != FALSE;
            (void)log_api(run, "GetExitCodeProcess", ok, error, "CreateProcess.PI.hProcess");
        }
    }
    ok = CloseHandle(run->pi.hThread);
    error = ok ? 0 : GetLastError();
    run->threadCloseOk = ok != FALSE;
    (void)log_api(run, "CloseHandle", ok, error, "CreateProcess.PI.hThread");
    ok = CloseHandle(run->pi.hProcess);
    error = ok ? 0 : GetLastError();
    run->processCloseOk = ok != FALSE;
    (void)log_api(run, "CloseHandle", ok, error, "CreateProcess.PI.hProcess");
    return run->exitContinued && run->processSignaled && run->getExitOk &&
        run->eventExitCode == run->childExit && !run->cleanupInconsistent &&
        run->threadCloseOk && run->processCloseOk && !run->log.failed;
}


static int c48f_page_guard(void *opaque)
{
    C48Run *run = (C48Run *)opaque;
    if (!within_wall(run)) return 0;
    if (run->pageCount >= C48F_MAX_PAGE_QUERIES) return fail_run(run, "pageQueryBudget");
    ++run->pageCount;
    return 1;
}
static int c48f_page_sink(void *opaque, const C48FPageRecord *r)
{
    C48Run *run = (C48Run *)opaque;
    C48Line line;
    line_begin(&run->log, &line, "pageQuery");
    line_add(&line, ",\"apiOrdinal\":%u,\"stopEventSeq\":%llu,\"purpose\":\"%s\","
        "\"address\":\"%016llx\",\"allowedRange\":{\"base\":\"%016llx\",\"bytes\":%llu},"
        "\"returnedBytes\":%llu,\"lastErrorIfZero\":", run->pageCount,
        (unsigned long long)run->log.stopSeq, r->purpose, (unsigned long long)r->address,
        (unsigned long long)r->allowedBase, (unsigned long long)r->allowedBytes,
        (unsigned long long)r->returnedBytes);
    if (r->returnedBytes) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)r->errorIfZero);
    line_add(&line, ",\"rawMemoryBasicInformation\":");
    if (r->returnedBytes == sizeof(r->mbi)) {
        line_add(&line, "{\"BaseAddress\":\"%016llx\",\"AllocationBase\":\"%016llx\","
            "\"AllocationProtect\":%lu,\"RegionSize\":\"%016llx\",\"State\":%lu,\"Protect\":%lu,\"Type\":%lu}",
            (unsigned long long)(uintptr_t)r->mbi.BaseAddress, (unsigned long long)(uintptr_t)r->mbi.AllocationBase,
            (unsigned long)r->mbi.AllocationProtect, (unsigned long long)r->mbi.RegionSize,
            (unsigned long)r->mbi.State, (unsigned long)r->mbi.Protect, (unsigned long)r->mbi.Type);
    } else line_add(&line, "null");
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    return 1;
}

static int c48f_read_memory(C48Run *run, const char *purpose, uint64_t address,
    uint32_t width, uint64_t allowedBase, uint32_t allowedBytes, uint8_t *data)
{
    C48Line line;
    SIZE_T actual = 0;
    BOOL ok;
    DWORD error;
    if (!within_wall(run)) return 0;
    if (run->readCount >= C48F_MAX_READS || width > C48F_MAX_SINGLE_READ ||
        width > C48F_MAX_READ_BYTES - run->readBytes) return fail_run(run, "readBudget");
    if (!width || address < allowedBase || address - allowedBase > allowedBytes ||
        width > allowedBytes - (address - allowedBase)) return fail_run(run, "memoryOutsideAllowedSpan");
    ++run->readCount;
    run->readBytes += width;
    ok = ReadProcessMemory(run->pi.hProcess, (LPCVOID)(uintptr_t)address, data, width, &actual);
    error = ok ? 0 : GetLastError();
    line_begin(&run->log, &line, "memory");
    line_add(&line, ",\"apiOrdinal\":%u,\"stopEventSeq\":%llu,\"purpose\":\"%s\","
        "\"address\":\"%016llx\",\"width\":%u,\"allowedRegion\":{\"base\":\"%016llx\","
        "\"bytes\":%u,\"name\":\"%s\"},\"ReadProcessMemoryBOOL\":%s,\"lastErrorIfFalse\":",
        run->readCount, (unsigned long long)run->log.stopSeq, purpose,
        (unsigned long long)address, width, (unsigned long long)allowedBase, allowedBytes,
        purpose, ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
    line_add(&line, ",\"actualBytesRead\":%llu,\"rawHex\":\"", (unsigned long long)actual);
    line_hex(&line, data, actual <= width ? actual : width);
    line_add(&line, "\"");
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    if (!ok || actual != width) return fail_run(run, "ReadProcessMemory");
    return 1;
}

typedef struct C48FTx {
    uint32_t id, preGet, postGet, tfSet, continued, preRead, postRead;
    uint64_t preEvent, postEvent;
    CONTEXT pre, post;
    int helper, mapped, preflightAccepted, retirementKnown, retired, faultObserved, emitted;
    C48FPreflight plan;
    uint8_t code[15], old[32], newBytes[32];
    uint32_t codeBytes;
    int oldValid, newValid;
} C48FTx;

static int c48f_emit_tx(C48Run *run, C48FTx *tx)
{
    C48Line line;
    const char *status = !tx->retirementKnown ? "retirement_unconfirmed" :
        (tx->retired ? "retired" : (tx->faultObserved ? "unretired" : "not_executed"));
    tx->emitted = 1;
    line_begin(&run->log, &line, "transaction");
    line_add(&line, ",\"txId\":%u,\"instructionOrigin\":\"%s\",\"sourceOrdinal\":",
        tx->id, tx->helper ? "original_helper" : "owned_wrapper_CALL");
    if (tx->mapped) line_add(&line, "%u,\"runtimeRva\":\"%08x\",\"originalPC\":\"%016llx\"",
        tx->plan.ordinal, g_c48fInstructions[tx->plan.ordinal].rva,
        (unsigned long long)(UINT64_C(0x140000000) + g_c48fInstructions[tx->plan.ordinal].rva));
    else line_add(&line, "null,\"runtimeRva\":null,\"originalPC\":null");
    if (!tx->helper) line_add(&line, ",\"ownedLabel\":\"C48FullCall\",\"ownedRva\":\"%08x\"", run->bootstrap.callRva);
    else line_add(&line, ",\"ownedLabel\":null,\"ownedRva\":null");
    line_add(&line, ",\"preEventSeq\":%llu,\"preRip\":\"%016llx\",\"instructionBytes\":",
        (unsigned long long)tx->preEvent, (unsigned long long)tx->pre.Rip);
    if (tx->codeBytes) { line_add(&line, "\""); line_hex(&line, tx->code, tx->codeBytes); line_add(&line, "\""); }
    else line_add(&line, "null");
    line_add(&line, ",\"preGetOrdinal\":%u,\"preGPR\":", tx->preGet);
    line_integer(&line, &tx->pre);
    line_add(&line, ",\"preEFlags\":\"%08lx\",\"preflightAccepted\":%s,\"ops\":[",
        (unsigned long)tx->pre.EFlags, tx->preflightAccepted ? "true" : "false");
    if (tx->plan.width) {
        line_add(&line, "{\"direction\":\"%s\",\"EA\":\"%016llx\",\"width\":%u,"
            "\"allowedSpan\":{\"base\":\"%016llx\",\"bytes\":%u,\"name\":\"%s\"},\"preReadOrdinal\":",
            tx->plan.direction == C48F_OPERAND_READ ? "read" : "write", (unsigned long long)tx->plan.address,
            tx->plan.width, (unsigned long long)tx->plan.spanBase, tx->plan.spanBytes,
            tx->helper ? "helperOperand" : "callSlot");
        if (tx->preRead) line_add(&line, "%u", tx->preRead); else line_add(&line, "null");
        line_add(&line, ",\"preHex\":");
        if (tx->oldValid) { line_add(&line, "\""); line_hex(&line, tx->old, tx->plan.width); line_add(&line, "\""); }
        else line_add(&line, "null");
        line_add(&line, ",\"postReadOrdinal\":");
        if (tx->postRead) line_add(&line, "%u", tx->postRead); else line_add(&line, "null");
        line_add(&line, ",\"postHex\":");
        if (tx->newValid) { line_add(&line, "\""); line_hex(&line, tx->newBytes, tx->plan.width); line_add(&line, "\""); }
        else line_add(&line, "null");
        line_add(&line, "}");
    }
    line_add(&line, "],\"expectedNextPCs\":[");
    if (tx->plan.nextPc) line_add(&line, "\"%016llx\"", (unsigned long long)tx->plan.nextPc);
    line_add(&line, "],\"tfSetOrdinal\":");
    if (tx->tfSet) line_add(&line, "%u", tx->tfSet); else line_add(&line, "null");
    line_add(&line, ",\"continueOrdinal\":");
    if (tx->continued) line_add(&line, "%u", tx->continued); else line_add(&line, "null");
    line_add(&line, ",\"postEventSeq\":");
    if (tx->postEvent) {
        line_add(&line, "%llu,\"postGetOrdinal\":%u,\"postRip\":\"%016llx\",\"postGPR\":",
            (unsigned long long)tx->postEvent, tx->postGet, (unsigned long long)tx->post.Rip);
        line_integer(&line, &tx->post);
        line_add(&line, ",\"postEFlags\":\"%08lx\"", (unsigned long)tx->post.EFlags);
    } else line_add(&line, "null,\"postGetOrdinal\":null,\"postRip\":null,\"postGPR\":null,\"postEFlags\":null");
    line_add(&line, ",\"retired\":%s,\"status\":\"%s\"",
        tx->retirementKnown ? (tx->retired ? "true" : "false") : "null", status);
    return line_finish(&run->log, &line) ? 1 : fail_run(run, "writeEvents");
}

static int c48f_prepare(C48Run *run, const C48FCaseConfig *config,
    const CONTEXT *pre, C48FWitnessState *witness, C48FTx *tx)
{
    uint64_t codeBase;
    uint32_t width, previous;
    const char *purpose;
    memset(tx, 0, sizeof(*tx));
    tx->id = run->retired + 1u; tx->preEvent = run->log.stopSeq;
    tx->preGet = run->getCount; tx->pre = *pre; tx->retirementKnown = 1;
    if (run->retired == 0) {
        tx->helper = 0;
        tx->plan.nextPc = run->descriptor.helperEntry;
        tx->plan.direction = C48F_OPERAND_WRITE; tx->plan.width = 8;
        tx->plan.spanBase = tx->plan.address = run->stackC - 8u; tx->plan.spanBytes = 8;
        if (pre->Rip != run->imageBase + run->bootstrap.callRva || pre->Rsp != run->stackC ||
            pre->R11 != run->descriptor.helperEntry) return fail_run(run, "callPreMismatch");
        if (!c48f_read_memory(run, "instructionBytes", pre->Rip, 3,
            run->imageBase + run->bootstrap.wrapperBeginRva, C48F_WRAPPER_BYTES, tx->code)) return 0;
        tx->codeBytes = 3;
        if (memcmp(tx->code, C48F_WRAPPER_CODE + C48F_CALL_OFFSET, 3)) return fail_run(run, "instructionMismatch");
    } else {
        const C48FInstruction *insn;
        uint32_t step = run->retired - 1u;
        tx->helper = 1;
        if (step >= config->helperCount) return fail_run(run, "unexpectedStep");
        tx->plan.ordinal = config->ordinals[step];
        insn = &g_c48fInstructions[tx->plan.ordinal];
        tx->mapped = pre->Rip == run->descriptor.arenaBase + insn->rva;
        width = insn->size;
        codeBase = run->descriptor.arenaBase + C48F_HELPER_RVA;
        if (pre->Rsp != run->stackC - 8u || pre->Rip != run->descriptor.arenaBase + insn->rva)
            return fail_run(run, "helperPcOrRspMismatch");
        if (!c48f_read_memory(run, "instructionBytes", pre->Rip, width,
            codeBase, C48F_SOURCE_BYTES, tx->code)) return 0;
        tx->codeBytes = width;
        if (!c48f_preflight(config, step, &run->descriptor, run->stackC, pre, tx->code, &tx->plan))
            return fail_run(run, "instructionPreflight");
    }
    if (tx->plan.width) {
        purpose = !tx->helper ? "CALLslotOld" :
            (g_c48fInstructions[tx->plan.ordinal].flowKind == C48F_FLOW_RET ? "RETslot" :
             (tx->plan.direction == C48F_OPERAND_READ ? "operandRead" : "writeOld"));
        previous = run->readCount;
        if (!c48f_read_memory(run, purpose, tx->plan.address, tx->plan.width,
            tx->plan.spanBase, tx->plan.spanBytes, tx->old)) {
            if (run->readCount != previous) tx->preRead = run->readCount;
            return 0;
        }
        tx->preRead = run->readCount; tx->oldValid = 1;
        if (tx->helper && !c48f_operand_before(config, &run->descriptor, &tx->plan, witness, tx->old))
            return fail_run(run, "operandOldMismatch");
    }
    tx->preflightAccepted = 1;
    return 1;
}

static int c48f_retire(C48Run *run, const C48FCaseConfig *config,
    C48FWitnessState *witness, C48FTx *tx, const CONTEXT *post)
{
    uint32_t previous;
    tx->postEvent = run->log.stopSeq; tx->postGet = run->getCount; tx->post = *post;
    if (post->Rip != tx->plan.nextPc) return fail_run(run, "postRipMismatch");
    if (tx->plan.direction == C48F_OPERAND_WRITE) {
        previous = run->readCount;
        if (!c48f_read_memory(run, tx->helper ? "writeNew" : "CALLslotNew", tx->plan.address,
            tx->plan.width, tx->plan.spanBase, tx->plan.spanBytes, tx->newBytes)) {
            if (run->readCount != previous) tx->postRead = run->readCount;
            return 0;
        }
        tx->postRead = run->readCount; tx->newValid = 1;
    }
    if (!tx->helper) {
        uint64_t target;
        memcpy(&target, tx->newBytes, 8);
        if (target != run->imageBase + run->bootstrap.afterCallRva || post->Rsp != run->stackC - 8u)
            return fail_run(run, "callPostMismatch");
    } else if (!c48f_post_matches(config, &run->descriptor, run->stackC, &tx->plan, post,
        tx->newBytes, witness)) return fail_run(run, "helperPostMismatch");
    tx->retirementKnown = 1; tx->retired = 1;
    if (!c48f_emit_tx(run, tx)) return 0;
    ++run->retired;
    return 1;
}

static int c48f_arm(C48Run *run, const C48FArguments *args, const C48FCaseConfig *config,
    const CONTEXT *context, const uint8_t fixedSource[904], C48FWitnessState *witness)
{
    uint8_t wrapper[27], source[904], initial[128];
    C48FullDescriptor *d = &run->descriptor;
    if (run->bootstrap.imageBytes > UINT32_MAX) return fail_run(run, "imageBytesMismatch");
    if (!c48f_read_memory(run, "descriptor", run->imageBase + run->bootstrap.descriptorRva,
        sizeof(*d), run->imageBase, (uint32_t)run->bootstrap.imageBytes, (uint8_t *)d)) return 0;
    if (!c48f_descriptor_matches(&run->bootstrap, d, config, args->runId,
        run->pi.dwProcessId, run->pi.dwThreadId, run->imageBase, context))
        return fail_run(run, "descriptorMismatch");
    if (!c48f_query_pages(run->pi.hProcess, d, run->stackC, c48f_page_guard, c48f_page_sink, run))
        return fail_run(run, "pageQueryMismatch");
    if (!c48f_read_memory(run, "wrapper", run->imageBase + run->bootstrap.wrapperBeginRva,
        sizeof(wrapper), run->imageBase + run->bootstrap.wrapperBeginRva, sizeof(wrapper), wrapper) ||
        !c48f_read_memory(run, "source904", d->helperEntry, sizeof(source), d->helperEntry, sizeof(source), source) ||
        !c48f_read_memory(run, "ownedBufferInitial", d->bufferKnownBase, sizeof(initial),
            d->bufferKnownBase, sizeof(initial), initial)) return 0;
    if (memcmp(wrapper, C48F_WRAPPER_CODE, sizeof(wrapper)) || memcmp(source, fixedSource, sizeof(source)) ||
        memcmp(initial, config->initial128, sizeof(initial))) return fail_run(run, "armBytesMismatch");
    memset(witness, 0, sizeof(*witness));
    witness->arm = *context;
    memcpy(witness->observed128, initial, sizeof(initial));
    return 1;
}

static int log_calibration(C48Run *run, const char *name, const CONTEXT *context, uint64_t moduleBase, const char *addressOrigin)
{
    C48Line line;
    uint64_t address = (uint64_t)(uintptr_t)run->event.u.Exception.ExceptionRecord.ExceptionAddress;
    line_begin(&run->log, &line, "calibration");
    line_add(&line, ",\"name\":\"%s\",\"stopEventSeq\":%llu,\"exceptionAddress\":\"%016llx\","
        "\"contextRip\":\"%016llx\",\"moduleBase\":\"%016llx\",\"addressOrigin\":\"%s\",\"exceptionRva\":",
        name, (unsigned long long)run->log.stopSeq, (unsigned long long)address,
        (unsigned long long)context->Rip, (unsigned long long)moduleBase, addressOrigin);
    if (moduleBase && address >= moduleBase)
        line_add(&line, !strcmp(addressOrigin, "sourceArena") ? "\"%08llx\"" : "\"%016llx\"",
            (unsigned long long)(address - moduleBase));
    else line_add(&line, "null");
    return line_finish(&run->log, &line) ? 1 : fail_run(run, "writeEvents");
}

static int c48f_event_loop(C48Run *run, const C48FArguments *args,
    const C48FCaseConfig *config, const uint8_t source[904])
{
    C48FTx tx;
    C48FWitnessState witness;
    CONTEXT context, readback;
    uint8_t final[128];
    int got, waitResult;
    memset(&tx, 0, sizeof(tx)); memset(&witness, 0, sizeof(witness));
    for (;;) {
        DWORD code;
        uint64_t address;
        waitResult = wait_event(run);
        if (waitResult == 2) continue;
        if (!waitResult) break;
        if (run->event.dwProcessId != run->pi.dwProcessId ||
            (strcmp(run->log.phase, "BOOT") && strcmp(run->log.phase, "WAIT_EXIT") &&
             run->event.dwThreadId != run->pi.dwThreadId)) {
            fail_run(run, "unknownPidOrThread"); break;
        }
        code = run->event.dwDebugEventCode;
        if (!strcmp(run->log.phase, "BOOT")) {
            if (code == CREATE_PROCESS_DEBUG_EVENT) {
                if (run->createProcessSeen) { fail_run(run, "duplicateCreateProcess"); break; }
                run->createProcessSeen = 1;
                run->imageBase = (uint64_t)(uintptr_t)run->event.u.CreateProcessInfo.lpBaseOfImage;
                if (!run->imageBase || run->imageBase > UINT64_MAX - run->bootstrap.imageBytes) {
                    fail_run(run, "imageBaseInvalid"); break;
                }
                if (!continue_event(run, DBG_CONTINUE, 0)) break;
                continue;
            }
            if (!run->createProcessSeen) { fail_run(run, "missingCreateProcess"); break; }
            if (code != CREATE_THREAD_DEBUG_EVENT && !known_live_thread(run)) {
                run->cleanupInconsistent = 1; fail_run(run, "unknownPidOrThread"); break;
            }
            if (code == EXIT_THREAD_DEBUG_EVENT && run->event.dwThreadId == run->pi.dwThreadId) {
                fail_run(run, "earlyPrimaryThreadExit"); break;
            }
            if (code == CREATE_THREAD_DEBUG_EVENT || code == EXIT_THREAD_DEBUG_EVENT ||
                code == LOAD_DLL_DEBUG_EVENT || code == UNLOAD_DLL_DEBUG_EVENT ||
                code == OUTPUT_DEBUG_STRING_EVENT) {
                if (code == UNLOAD_DLL_DEBUG_EVENT &&
                    (uint64_t)(uintptr_t)run->event.u.UnloadDll.lpBaseOfDll == run->ntdllBase) {
                    run->ntdllBase = 0;
                    run->ntdllEnd = 0;
                }
                if (!continue_event(run, DBG_CONTINUE, 0)) break;
                continue;
            }
            if (code != EXCEPTION_DEBUG_EVENT || run->event.dwThreadId != run->pi.dwThreadId ||
                !run->event.u.Exception.dwFirstChance ||
                run->event.u.Exception.ExceptionRecord.ExceptionCode != EXCEPTION_BREAKPOINT) {
                fail_run(run, "unexpectedBootEvent"); break;
            }
            address = (uint64_t)(uintptr_t)run->event.u.Exception.ExceptionRecord.ExceptionAddress;
            if (!run->systemBreakpointSeen) {
                if (!run->ntdllBase || address < run->ntdllBase || address >= run->ntdllEnd) {
                    fail_run(run, "systemBreakpointNotNtdll"); break;
                }
                if (!get_context(run, &context) || !log_calibration(run, "systemBreakpoint", &context, run->ntdllBase, "systemModule")) break;
                if (!continue_event(run, DBG_CONTINUE, 0)) break;
                run->systemBreakpointSeen = 1;
                continue;
            }
            if (address != run->imageBase + run->bootstrap.armRva) {
                fail_run(run, "ownedBreakpointMismatch"); break;
            }
            run->log.phase = "ARM_STOP";
            if (!get_context(run, &context) || !log_calibration(run, "ownedInt3", &context, run->imageBase, "childImage")) break;
            if (context.Rip != run->imageBase + run->bootstrap.callRva ||
                (context.EFlags & C48F_TF)) { fail_run(run, "ownedBreakpointMismatch"); break; }
            run->originalTf = 0;
            run->stackC = context.Rsp;
            if (!c48f_arm(run, args, config, &context, source, &witness)) break;
        } else if (!strcmp(run->log.phase, "PENDING")) {
            DWORD exceptionCode;
            run->log.phase = "POST_STOP";
            if (!run->pendingValid || code != EXCEPTION_DEBUG_EVENT) {
                fail_run(run, "unexpectedPendingEvent"); break;
            }
            address = (uint64_t)(uintptr_t)run->event.u.Exception.ExceptionRecord.ExceptionAddress;
            exceptionCode = run->event.u.Exception.ExceptionRecord.ExceptionCode;
            got = get_context(run, &context);
            if (run->lastContextStop == run->log.stopSeq) {
                tx.postEvent = run->log.stopSeq; tx.postGet = run->getCount; tx.post = run->lastContext;
            }
            if (!got) break;
            if (!run->event.u.Exception.dwFirstChance) { fail_run(run, "unexpectedSecondChance"); break; }
            if (exceptionCode != EXCEPTION_SINGLE_STEP) {
                if ((exceptionCode == EXCEPTION_ILLEGAL_INSTRUCTION || exceptionCode == EXCEPTION_ACCESS_VIOLATION) &&
                    address == tx.pre.Rip && context.Rip == tx.pre.Rip) {
                    tx.retirementKnown = 1; tx.faultObserved = 1;
                }
                fail_run(run, "unexpectedException"); break;
            }
            if (address != context.Rip || context.Rip != tx.plan.nextPc || !log_calibration(run, "singleStep", &context,
                    address >= run->descriptor.helperEntry && address < run->descriptor.helperEntry + C48F_SOURCE_BYTES ?
                        run->descriptor.arenaBase :
                        (address >= run->imageBase && address < run->imageBase + run->bootstrap.imageBytes ? run->imageBase : 0),
                    address >= run->descriptor.helperEntry && address < run->descriptor.helperEntry + C48F_SOURCE_BYTES ?
                        "sourceArena" :
                        (address >= run->imageBase && address < run->imageBase + run->bootstrap.imageBytes ? "childImage" : "unknown")) ||
                !c48f_retire(run, config, &witness, &tx, &context)) {
                if (!run->failure) fail_run(run, "stepAddressMismatch");
                break;
            }
            run->pendingValid = 0; run->pendingId = 0;
            run->helperControlReads = witness.controlsCompleted;
            run->helperControlReadBytes = witness.controlBytes;
            run->helperStoreEvents = witness.storesCompleted;
            if (run->retired == config->totalRetired) {
                run->returned = 1; run->returnRax = context.Rax; run->returnRsp = context.Rsp;
            }
            if (run->retired == config->totalRetired) {
                run->log.phase = "TRACE_DONE";
                if (!c48f_read_memory(run, "ownedBufferFinal", run->descriptor.bufferKnownBase,
                    sizeof(final), run->descriptor.bufferKnownBase, sizeof(final), final) ||
                    memcmp(final, config->final128, sizeof(final)) ||
                    memcmp(final, witness.observed128, sizeof(final)) ||
                    witness.storesCompleted != config->storeCount ||
                    witness.controlsCompleted != config->controlCount ||
                    witness.controlBytes != (config->id == C48F_CASE_SSE_LONG ? 17u : 25u) ||
                    run->pageCount != 13u || run->readCount != config->readCount ||
                    run->readBytes != config->requestedReadBytes) {
                    fail_run(run, "finalEvidenceMismatch"); break;
                }
                if (!set_tf(run, &context, run->originalTf, &readback) ||
                    run->getCount != config->getMax || run->setCount != config->setMax) {
                    if (!run->failure) fail_run(run, "contextBudgetMismatch");
                    break;
                }
                if (!continue_event(run, DBG_CONTINUE, 0)) break;
                run->log.phase = "WAIT_EXIT";
                continue;
            }
        } else if (!strcmp(run->log.phase, "WAIT_EXIT")) {
            if (code != CREATE_THREAD_DEBUG_EVENT && !known_live_thread(run)) {
                run->cleanupInconsistent = 1; fail_run(run, "unknownPidOrThread"); break;
            }
            if (code != CREATE_THREAD_DEBUG_EVENT && code != EXIT_THREAD_DEBUG_EVENT &&
                code != EXIT_PROCESS_DEBUG_EVENT && code != LOAD_DLL_DEBUG_EVENT && code != UNLOAD_DLL_DEBUG_EVENT &&
                code != OUTPUT_DEBUG_STRING_EVENT) {
                fail_run(run, "unexpectedExitEvent"); break;
            }
            if (!continue_event(run, DBG_CONTINUE, 0)) break;
            if (code == EXIT_PROCESS_DEBUG_EVENT) { run->log.phase = "EXITED"; return 1; }
            continue;
        } else { fail_run(run, "invalidState"); break; }
        run->pendingValid = 1; run->pendingId = run->retired + 1u;
        if (!c48f_prepare(run, config, &context, &witness, &tx)) break;
        {
            uint32_t before = run->setCount;
            int ok = set_tf(run, &context, 1, &readback);
            if (run->setCount != before) tx.tfSet = run->setCount;
            if (!ok) break;
        }
        {
            uint32_t before = run->continueCount;
            int ok = continue_event(run, DBG_CONTINUE, 0);
            if (run->continueCount != before) { tx.continued = run->continueCount; tx.retirementKnown = 0; }
            if (!ok) break;
        }
        run->log.phase = "PENDING";
    }
    run->pendingRetired = tx.retired; run->pendingRetirementKnown = tx.retirementKnown;
    if (run->pendingValid && tx.id && !tx.emitted) (void)c48f_emit_tx(run, &tx);
    return 0;
}

static void c48f_line_proof(C48Line *line, const C48Run *run)
{
    line_add(line, "\"childExeSHA256\":\""); line_hex(line, run->sourceChildSHA, 32);
    line_add(line, "\",\"parentExeSHA256\":\""); line_hex(line, run->sourceParentSHA, 32);
    line_add(line, "\",\"bootstrapSHA256\":\""); line_hex(line, run->sourceBootstrapSHA, 32);
    line_add(line, "\",\"sharedHeaderSHA256\":\""); line_hex(line, run->sourceHeaderSHA, 32);
    line_add(line, "\",\"source904SHA256\":\""); line_hex(line, run->sourceHelperSHA, 32);
    line_add(line, "\",\"catalogSHA256\":\"%s\",\"casesSHA256\":\"%s\","
        "\"interfaceContractSHA256\":\"%s\",\"imageBytes\":\"%016llx\","
        "\"descriptorRva\":%u,\"wrapperBeginRva\":%u,\"armRva\":%u,\"callRva\":%u,"
        "\"afterCallRva\":%u,\"wrapperEndRva\":%u,\"exitLifecyclePolicy\":\"%s\","
        "\"exitLifecycleContractSHA256\":\"%s\",\"eflagsReadbackPolicy\":\"%s\"",
        C48F_CATALOG_SHA256_HEX, C48F_CASES_SHA256_HEX, C48F_INTERFACE_SHA256_HEX,
        (unsigned long long)run->sourceImageBytes, run->bootstrap.descriptorRva,
        run->bootstrap.wrapperBeginRva, run->bootstrap.armRva, run->bootstrap.callRva,
        run->bootstrap.afterCallRva, run->bootstrap.wrapperEndRva, C48F_EXIT_POLICY,
        C48F_EXIT_CONTRACT_SHA256_HEX, C48F_EFLAGS_POLICY);
}

static int c48f_summary(HANDLE file, C48Run *run, const C48FArguments *args,
    int traceDone, int cleanupComplete)
{
    C48Line line;
    DWORD written;
    uint32_t n = run->helperStoreEvents;
    uint32_t sum = n * (args->caseId == C48F_CASE_SSE_LONG ? 16u : 32u);
    uint32_t uni = args->caseId == C48F_CASE_SSE_LONG ? sum : (n == 0 ? 0u : (n == 1 ? 32u : 48u));
    int normal = traceDone && cleanupComplete && run->childExit == 0 && !run->failure;
    char id[33]; size_t i;
    memset(&line, 0, sizeof(line));
    for (i = 0; i < 32; ++i) id[i] = (char)args->runIdText[i]; id[32] = 0;
    line_add(&line, "{\"schema\":\"c48-full-helper-parent-result-v1\",\"caseId\":%u,\"runId\":\"%s\","
        "\"pid\":", args->caseId, id);
    if (run->childCreated) line_add(&line, "%lu", (unsigned long)run->pi.dwProcessId); else line_add(&line, "null");
    line_add(&line, ",\"tid\":");
    if (run->childCreated) line_add(&line, "%lu", (unsigned long)run->pi.dwThreadId); else line_add(&line, "null");
    line_add(&line, ",\"sourceProof\":");
    if (run->epochValidated) { line_add(&line, "{"); c48f_line_proof(&line, run); line_add(&line, "}"); }
    else line_add(&line, "null");
    line_add(&line, ",\"normalTracePassed\":%s,\"firstFailureKind\":", normal ? "true" : "false");
    if (run->failure) line_add(&line, "\"%s\"", run->failure); else line_add(&line, "null");
    line_add(&line, ",\"stopEventSeq\":");
    if (run->firstFailureStop) line_add(&line, "%llu", (unsigned long long)run->firstFailureStop); else line_add(&line, "null");
    line_add(&line, ",\"rawCode\":");
    if (run->firstHadException) line_add(&line, "%lu", (unsigned long)run->firstRawCode); else line_add(&line, "null");
    line_add(&line, ",\"exceptionAddress\":");
    if (run->firstHadException) line_add(&line, "\"%016llx\"", (unsigned long long)run->firstExceptionAddress); else line_add(&line, "null");
    line_add(&line, ",\"contextRip\":");
    if (run->firstHadContext) line_add(&line, "\"%016llx\"", (unsigned long long)run->firstContextRip); else line_add(&line, "null");
    line_add(&line, ",\"TerminateProcessBOOL\":");
    if (traceDone || !run->childCreated) line_add(&line, "null"); else line_add(&line, "%s", run->terminateOk ? "true" : "false");
    line_add(&line, ",\"terminationRequestedExit\":");
    if (traceDone || !run->childCreated) line_add(&line, "null"); else line_add(&line, "%u", C48F_ABORT_EXIT);
    line_add(&line, ",\"cleanupContinues\":%u", run->cleanupContinues);
    line_add(&line, ",\"pendingTxId\":");
    if (run->pendingValid) line_add(&line, "%u", run->pendingId); else line_add(&line, "null");
    line_add(&line, ",\"pendingRetired\":%s,\"lastCompletedTx\":%u,\"helperRetired\":%u,"
        "\"wrapperRetired\":%u,\"helperControlReads\":%u,\"helperControlReadBytes\":%u,"
        "\"helperStoreEvents\":%u,\"helperStoreSumBytes\":%u,\"helperStoreUnionBytes\":%u,"
        "\"helperStoreIntersectionBytes\":%u,\"returnRAX\":",
        run->pendingValid && run->pendingRetirementKnown ? (run->pendingRetired ? "true" : "false") : "null",
        run->retired, run->retired ? run->retired - 1u : 0u, run->retired ? 1u : 0u,
        run->helperControlReads, run->helperControlReadBytes, n, sum, uni, sum - uni);
    if (run->returned) line_add(&line, "\"%016llx\"", (unsigned long long)run->returnRax); else line_add(&line, "null");
    line_add(&line, ",\"returnRSP\":");
    if (run->returned) line_add(&line, "\"%016llx\"", (unsigned long long)run->returnRsp); else line_add(&line, "null");
    line_add(&line, ",\"exitLifecyclePolicy\":\"%s\",\"exitLifecycleContractSHA256\":\"%s\","
        "\"exitEventSeq\":", C48F_EXIT_POLICY, C48F_EXIT_CONTRACT_SHA256_HEX);
    if (run->exitSeen) line_add(&line, "%llu", (unsigned long long)run->exitEventSeq); else line_add(&line, "null");
    line_add(&line, ",\"exitEventTID\":");
    if (run->exitSeen) line_add(&line, "%lu", (unsigned long)run->exitEventTID); else line_add(&line, "null");
    line_add(&line, ",\"EXIT_PROCESSobserved\":%s,\"EXIT_PROCESScontinued\":%s,\"ownedProcessSignaled\":%s,"
        "\"GetExitCodeProcessBOOL\":", run->exitSeen ? "true" : "false", run->exitContinued ? "true" : "false",
        run->processSignaled ? "true" : "false");
    if (run->processSignaled) line_add(&line, "%s", run->getExitOk ? "true" : "false"); else line_add(&line, "null");
    line_add(&line, ",\"childExitCode\":");
    if (run->getExitOk) line_add(&line, "%lu", (unsigned long)run->childExit); else line_add(&line, "null");
    line_add(&line, ",\"PIThreadCloseBOOL\":");
    if (run->childCreated) line_add(&line, "%s", run->threadCloseOk ? "true" : "false"); else line_add(&line, "null");
    line_add(&line, ",\"PIProcessCloseBOOL\":");
    if (run->childCreated) line_add(&line, "%s", run->processCloseOk ? "true" : "false"); else line_add(&line, "null");
    line_add(&line, ",\"threadLifecycleConsistent\":%s,\"cleanupComplete\":%s,\"budgets\":{"
        "\"events\":%u,\"waits\":%u,\"gets\":%u,\"sets\":%u,\"reads\":%u,\"requestedReadBytes\":%u,"
        "\"continues\":%u,\"pageQueries\":%u,\"retired\":%u,\"helperRetired\":%u}}\n",
        run->cleanupInconsistent ? "false" : "true", cleanupComplete ? "true" : "false", run->eventCount,
        run->waitCount, run->getCount, run->setCount, run->readCount, run->readBytes, run->continueCount,
        run->pageCount, run->retired, run->retired ? run->retired - 1u : 0u);
    return !line.failed && WriteFile(file, line.text, (DWORD)line.length, &written, NULL) &&
        written == (DWORD)line.length && FlushFileBuffers(file);
}

static int c48f_pe_image_bytes(const wchar_t *path, uint64_t *imageBytes)
{
    HANDLE file = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL,
        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    IMAGE_DOS_HEADER dos;
    IMAGE_NT_HEADERS64 nt;
    LARGE_INTEGER size;
    int ok = 0;
    if (file == INVALID_HANDLE_VALUE) return 0;
    if (!GetFileSizeEx(file, &size) || !read_exact_at(file, 0, &dos, (DWORD)sizeof(dos)) ||
        dos.e_magic != IMAGE_DOS_SIGNATURE || dos.e_lfanew < 0 ||
        (uint64_t)dos.e_lfanew + sizeof(nt) > (uint64_t)size.QuadPart ||
        !read_exact_at(file, (uint64_t)dos.e_lfanew, &nt, (DWORD)sizeof(nt)) ||
        nt.Signature != IMAGE_NT_SIGNATURE || nt.FileHeader.Machine != IMAGE_FILE_MACHINE_AMD64 ||
        nt.FileHeader.SizeOfOptionalHeader < sizeof(nt.OptionalHeader) ||
        nt.OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR64_MAGIC || nt.OptionalHeader.SizeOfImage == 0) goto done;
    *imageBytes = nt.OptionalHeader.SizeOfImage;
    ok = 1;
done:
    if (!CloseHandle(file)) ok = 0;
    return ok;
}

static HANDLE create_output(const wchar_t *directory, const wchar_t *name, int inheritable)
{
    wchar_t path[32768];
    SECURITY_ATTRIBUTES security;
    if (_snwprintf_s(path, sizeof(path) / sizeof(path[0]), _TRUNCATE, L"%ls\\%ls", directory, name) < 0) {
        SetLastError(ERROR_BUFFER_OVERFLOW);
        return INVALID_HANDLE_VALUE;
    }
    memset(&security, 0, sizeof(security));
    security.nLength = (DWORD)sizeof(security);
    security.bInheritHandle = inheritable ? TRUE : FALSE;
    return CreateFileW(path, GENERIC_WRITE, FILE_SHARE_READ, &security, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, NULL);
}

static int c48f_hash_file(const wchar_t *path, uint8_t digest[32])
{
    HANDLE file = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL,
        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    int ok;
    if (file == INVALID_HANDLE_VALUE) return 0;
    ok = c48f_sha256_handle(file, digest);
    if (!CloseHandle(file)) ok = 0;
    return ok;
}

static int c48f_run(const C48FArguments *args)
{
    C48Run run;
    const C48FCaseConfig *config = &g_c48fCases[args->caseId == C48F_CASE_SSE_LONG ? 0 : 1];
    STARTUPINFOW startup;
    SECURITY_ATTRIBUTES security;
    HANDLE resultFile = INVALID_HANDLE_VALUE, nullFile = INVALID_HANDLE_VALUE;
    HANDLE childStdout = INVALID_HANDLE_VALUE, childStderr = INVALID_HANDLE_VALUE;
    wchar_t parentPath[32768], childResult[32768], command[32768];
    uint8_t source[904];
    BOOL created, ok;
    DWORD error, attributes, parentLength;
    int traceDone = 0, cleanupComplete = 0, result = 2;
    memset(&run, 0, sizeof(run)); memset(&startup, 0, sizeof(startup)); memset(&security, 0, sizeof(security));
    run.caseId = args->caseId; run.log.file = INVALID_HANDLE_VALUE; run.log.phase = "BOOT";
    run.log.started = GetTickCount64();
    if (!CreateDirectoryW(args->output, NULL)) return 2;
    run.log.file = create_output(args->output, L"events.jsonl", 0);
    if (run.log.file == INVALID_HANDLE_VALUE) return 2;
    resultFile = create_output(args->output, L"parent-result.json", 0);
    if (resultFile == INVALID_HANDLE_VALUE) goto done;
    parentLength = GetModuleFileNameW(NULL, parentPath, (DWORD)(sizeof(parentPath)/sizeof(parentPath[0])));
    if (!parentLength || parentLength >= sizeof(parentPath)/sizeof(parentPath[0]) ||
        !c48f_read_fixed_file(args->bootstrap, &run.bootstrap, sizeof(run.bootstrap), run.sourceBootstrapSHA) ||
        !c48f_bootstrap_matches(&run.bootstrap) ||
        !c48f_read_fixed_file(args->helper, source, sizeof(source), run.sourceHelperSHA) ||
        memcmp(run.sourceHelperSHA, run.bootstrap.sourceSHA256, 32) ||
        !c48f_hash_file(args->child, run.sourceChildSHA) ||
        memcmp(run.sourceChildSHA, run.bootstrap.childExeSHA256, 32) ||
        !c48f_hash_file(parentPath, run.sourceParentSHA) ||
        !c48f_pe_image_bytes(args->child, &run.sourceImageBytes) ||
        run.sourceImageBytes != run.bootstrap.imageBytes) {
        fail_run(&run, "epochMismatch");
        (void)c48f_summary(resultFile, &run, args, 0, 0); goto done;
    }
    memcpy(run.sourceHeaderSHA, run.bootstrap.sharedHeaderSHA256, 32);
    run.epochValidated = 1;
    {
        C48Line line;
        line_begin(&run.log, &line, "epoch");
        line_add(&line, ",\"schema\":\"c48-full-helper-epoch-v1\",");
        c48f_line_proof(&line, &run);
        if (!line_finish(&run.log, &line)) fail_run(&run, "writeEvents");
    }
    if (!within_wall(&run) || run.failure) { (void)c48f_summary(resultFile, &run, args, 0, 0); goto done; }
    if (_snwprintf_s(childResult, sizeof(childResult)/sizeof(childResult[0]), _TRUNCATE,
            L"%ls\\child-result.json", args->output) < 0 ||
        _snwprintf_s(command, sizeof(command)/sizeof(command[0]), _TRUNCATE,
            L"\"%ls\" --helper \"%ls\" --case %u --run-id %ls --output \"%ls\"",
            args->child, args->helper, args->caseId, args->runIdText, childResult) < 0) {
        fail_run(&run, "commandOrPathTooLong"); (void)c48f_summary(resultFile, &run, args, 0, 0); goto done;
    }
    security.nLength = sizeof(security); security.bInheritHandle = TRUE;
    nullFile = CreateFileW(L"NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
        &security, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (nullFile == INVALID_HANDLE_VALUE) {
        fail_run(&run, "openNull"); (void)c48f_summary(resultFile, &run, args, 0, 0); goto done;
    }
    childStdout = create_output(args->output, L"child.stdout", 1);
    childStderr = create_output(args->output, L"child.stderr", 1);
    if (childStdout == INVALID_HANDLE_VALUE || childStderr == INVALID_HANDLE_VALUE) {
        fail_run(&run, "openChildOutput"); (void)c48f_summary(resultFile, &run, args, 0, 0); goto done;
    }
    startup.cb = sizeof(startup); startup.dwFlags = STARTF_USESHOWWINDOW | STARTF_USESTDHANDLES;
    startup.wShowWindow = SW_HIDE; startup.hStdInput = nullFile;
    startup.hStdOutput = childStdout; startup.hStdError = childStderr;
    if (!within_wall(&run)) { (void)c48f_summary(resultFile, &run, args, 0, 0); goto done; }
    created = CreateProcessW(args->child, command, NULL, NULL, TRUE,
        DEBUG_ONLY_THIS_PROCESS | CREATE_NO_WINDOW, NULL, NULL, &startup, &run.pi);
    error = created ? 0 : GetLastError(); run.childCreated = created != FALSE;
    if (!log_api(&run, "CreateProcessW", created, error, "newOwnedChild")) fail_run(&run, "writeEvents");
    if (created) {
        C48Line line;
        FILETIME creation = {0}, exitTime, kernelTime, userTime;
        BOOL timesOk = FALSE; DWORD timesError = 0; int timesAttempted = 0;
        if (!run.failure && within_wall(&run)) {
            timesAttempted = 1;
            timesOk = GetProcessTimes(run.pi.hProcess, &creation, &exitTime, &kernelTime, &userTime);
            if (!timesOk) { timesError = GetLastError(); fail_run(&run, "childIdentityGetProcessTimes"); }
        }
        line_begin(&run.log, &line, "ownedChild");
        line_add(&line, ",\"pid\":%lu,\"tid\":%lu,\"processHandleOrigin\":\"CreateProcess.PI.hProcess\","
            "\"threadHandleOrigin\":\"CreateProcess.PI.hThread\",\"processHandleRaw\":\"%016llx\","
            "\"threadHandleRaw\":\"%016llx\",\"GetProcessTimesBOOL\":%s,\"lastErrorIfFalse\":",
            (unsigned long)run.pi.dwProcessId, (unsigned long)run.pi.dwThreadId,
            (unsigned long long)(uintptr_t)run.pi.hProcess, (unsigned long long)(uintptr_t)run.pi.hThread,
            timesAttempted ? (timesOk ? "true" : "false") : "null");
        if (timesAttempted && !timesOk) line_add(&line, "%lu", (unsigned long)timesError); else line_add(&line, "null");
        line_add(&line, ",\"creationFileTime\":");
        if (timesOk) line_add(&line, "\"%016llx\"",
            (unsigned long long)(((uint64_t)creation.dwHighDateTime << 32) | creation.dwLowDateTime));
        else line_add(&line, "null");
        if (!line_finish(&run.log, &line)) fail_run(&run, "writeEvents");
    }
    ok = CloseHandle(nullFile); error = ok ? 0 : GetLastError(); nullFile = INVALID_HANDLE_VALUE;
    if (!log_api(&run, "CloseHandle", ok, error, "NULInput") || !ok) fail_run(&run, "nullClose");
    ok = CloseHandle(childStdout); error = ok ? 0 : GetLastError(); childStdout = INVALID_HANDLE_VALUE;
    if (!log_api(&run, "CloseHandle", ok, error, "stdout") || !ok) fail_run(&run, "stdoutClose");
    ok = CloseHandle(childStderr); error = ok ? 0 : GetLastError(); childStderr = INVALID_HANDLE_VALUE;
    if (!log_api(&run, "CloseHandle", ok, error, "stderr") || !ok) fail_run(&run, "stderrClose");
    if (!created) { fail_run(&run, "CreateProcessW"); (void)c48f_summary(resultFile, &run, args, 0, 0); goto done; }
    run.killAttempted = 1;
    ok = DebugSetProcessKillOnExit(TRUE); error = ok ? 0 : GetLastError(); run.killAccepted = ok != FALSE;
    if (!log_api(&run, "DebugSetProcessKillOnExit", ok, error, "debuggerThread") || !ok)
        fail_run(&run, "DebugSetProcessKillOnExit");
    if (!run.failure) traceDone = c48f_event_loop(&run, args, config, source);
    cleanupComplete = cleanup_run(&run, traceDone);
    attributes = GetFileAttributesW(childResult);
    ok = attributes != INVALID_FILE_ATTRIBUTES && !(attributes & FILE_ATTRIBUTE_DIRECTORY);
    error = attributes == INVALID_FILE_ATTRIBUTES ? GetLastError() : 0;
    (void)log_api(&run, "GetFileAttributesW", attributes != INVALID_FILE_ATTRIBUTES, error, "childResult");
    if (run.log.failed) cleanupComplete = 0;
    if (traceDone && (!cleanupComplete || run.childExit != 0 || !ok)) fail_run(&run, "normalExitOrChildResult");
    if (!c48f_summary(resultFile, &run, args, traceDone, cleanupComplete)) goto done;
    if (traceDone && cleanupComplete && !run.failure && run.childExit == 0) result = 0;
done:
    if (nullFile != INVALID_HANDLE_VALUE) (void)CloseHandle(nullFile);
    if (childStdout != INVALID_HANDLE_VALUE) (void)CloseHandle(childStdout);
    if (childStderr != INVALID_HANDLE_VALUE) (void)CloseHandle(childStderr);
    if (resultFile != INVALID_HANDLE_VALUE && !CloseHandle(resultFile)) result = 2;
    if (run.log.file != INVALID_HANDLE_VALUE) {
        if (!FlushFileBuffers(run.log.file)) result = 2;
        if (!CloseHandle(run.log.file)) result = 2;
    }
    return result;
}

int wmain(int argc, wchar_t **argv)
{
    C48FArguments args;
    if (!c48f_arguments(argc, argv, &args)) return 2;
    return c48f_run(&args);
}
