/* Bounded owned-child witness; build and execution require root review. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <bcrypt.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>
#include "shared.h"

#ifndef C48_SHARED_HEADER_SHA256_HEX
#error C48_SHARED_HEADER_SHA256_HEX_must_come_from_build_epoch
#endif

typedef struct C48Arguments {
    const wchar_t *childPath;
    const wchar_t *bootstrapPath;
    const wchar_t *outputPath;
    const wchar_t *runIdText;
    uint8_t runId[16];
    uint32_t caseId;
} C48Arguments;

static int parse_arguments(int argc, wchar_t **argv, C48Arguments *args);
static int read_bootstrap(const wchar_t *path, C48Bootstrap *bootstrap);
static int sha256_file(const wchar_t *path, uint8_t digest[32]);
static int validate_epoch(const C48Arguments *args, const C48Bootstrap *bootstrap, uint8_t childHash[32], uint8_t headerHash[32], uint64_t *imageBytes);
static int pe_image_bytes(const wchar_t *path, uint64_t *imageBytes);
static int run_witness(const C48Arguments *args);

int wmain(int argc, wchar_t **argv)
{
    C48Arguments args;
    if (!parse_arguments(argc, argv, &args)) return 2;
    return run_witness(&args);
}

static int is_absolute(const wchar_t *path)
{
    return (path[0] != 0 && path[1] == L':' && (path[2] == L'\\' || path[2] == L'/')) ||
        (path[0] == L'\\' && path[1] == L'\\' && path[2] != 0);
}

static int hex_value(wchar_t ch)
{
    if (ch >= L'0' && ch <= L'9') return (int)(ch - L'0');
    if (ch >= L'a' && ch <= L'f') return (int)(ch - L'a') + 10;
    return -1;
}

static int parse_arguments(int argc, wchar_t **argv, C48Arguments *args)
{
    size_t i;
    memset(args, 0, sizeof(*args));
    if (argc != 11 || wcscmp(argv[1], L"--child-exe") ||
        wcscmp(argv[3], L"--bootstrap") || wcscmp(argv[5], L"--case") ||
        wcscmp(argv[7], L"--run-id") || wcscmp(argv[9], L"--output") ||
        !is_absolute(argv[2]) || !is_absolute(argv[4]) || !is_absolute(argv[10]) ||
        wcschr(argv[2], L'"') || wcschr(argv[4], L'"') || wcschr(argv[10], L'"') ||
        wcslen(argv[6]) != 1 || argv[6][0] < L'1' || argv[6][0] > L'3' ||
        wcslen(argv[8]) != 32) return 0;
    for (i = 0; i < 16; ++i) {
        int high = hex_value(argv[8][i * 2]);
        int low = hex_value(argv[8][i * 2 + 1]);
        if (high < 0 || low < 0) return 0;
        args->runId[i] = (uint8_t)((high << 4) | low);
    }
    args->childPath = argv[2];
    args->bootstrapPath = argv[4];
    args->caseId = (uint32_t)(argv[6][0] - L'0');
    args->runIdText = argv[8];
    args->outputPath = argv[10];
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

static int read_bootstrap(const wchar_t *path, C48Bootstrap *bootstrap)
{
    HANDLE file = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL,
        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    LARGE_INTEGER size;
    int ok;
    if (file == INVALID_HANDLE_VALUE) return 0;
    ok = GetFileSizeEx(file, &size) && size.QuadPart == (LONGLONG)sizeof(*bootstrap) &&
        read_exact_at(file, 0, bootstrap, (DWORD)sizeof(*bootstrap));
    if (!CloseHandle(file)) ok = 0;
    return ok;
}

static int sha256_handle(HANDLE file, uint8_t digest[32])
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

static int sha256_file(const wchar_t *path, uint8_t digest[32])
{
    HANDLE file = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL,
        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    int ok;
    if (file == INVALID_HANDLE_VALUE) return 0;
    ok = sha256_handle(file, digest);
    if (!CloseHandle(file)) ok = 0;
    return ok;
}

static int pe_image_bytes(const wchar_t *path, uint64_t *imageBytes)
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

static int range_in_image(uint64_t imageBytes, uint32_t rva, uint32_t width)
{
    return rva != 0 && (uint64_t)rva + width <= imageBytes;
}

static int validate_epoch(const C48Arguments *args, const C48Bootstrap *b, uint8_t childHash[32], uint8_t headerHash[32], uint64_t *imageBytes)
{
    static const char headerText[] = C48_SHARED_HEADER_SHA256_HEX;
    size_t i;
    _Static_assert(sizeof(headerText) == 65, "shared header digest length");
    for (i = 0; i < 32; ++i) {
        int high = hex_value((wchar_t)headerText[2 * i]);
        int low = hex_value((wchar_t)headerText[2 * i + 1]);
        if (high < 0 || low < 0) return 0;
        headerHash[i] = (uint8_t)((high << 4) | low);
    }
    if (memcmp(b->magic, C48_BOOTSTRAP_MAGIC, sizeof(b->magic)) ||
        b->version != C48_VERSION || b->byteSize != sizeof(*b) ||
        b->allowedCaseMask != C48_CASE_MASK ||
        memcmp(headerHash, b->sharedHeaderSHA256, 32) ||
        !sha256_file(args->childPath, childHash) || memcmp(childHash, b->childExeSHA256, 32) ||
        !pe_image_bytes(args->childPath, imageBytes) || *imageBytes != b->imageBytes ||
        !range_in_image(*imageBytes, b->descriptorRva, (uint32_t)sizeof(C48Descriptor)) ||
        !range_in_image(*imageBytes, b->wrapperBeginRva, C48_WRAPPER_BYTES) ||
        !range_in_image(*imageBytes, b->returnLeafRva, (uint32_t)sizeof(C48_RETURN_CODE)) ||
        !range_in_image(*imageBytes, b->ymmLeafRva, (uint32_t)sizeof(C48_YMM_CODE)) ||
        !range_in_image(*imageBytes, b->faultLeafRva, (uint32_t)sizeof(C48_FAULT_CODE)) ||
        b->armRva != b->wrapperBeginRva + C48_ARM_OFFSET ||
        b->callRva != b->wrapperBeginRva + C48_CALL_OFFSET ||
        b->afterCallRva != b->wrapperBeginRva + C48_AFTER_CALL_OFFSET ||
        b->wrapperEndRva != b->wrapperBeginRva + C48_WRAPPER_BYTES) return 0;
    return 1;
}

/* All debug control stays on the thread that creates the owned child. */
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

typedef struct C48Run {
    C48Log log;
    PROCESS_INFORMATION pi;
    C48Bootstrap bootstrap;
    C48Descriptor descriptor;
    uint8_t sourceChildSHA[32];
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
    uint32_t continueCount;
    uint32_t retired;
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
    return GetTickCount64() - run->log.started < C48_WALL_MILLISECONDS ? 1 : fail_run(run, "wallBudget");
}

static int get_context(C48Run *run, CONTEXT *context)
{
    C48Line line;
    BOOL ok;
    DWORD error;
    if (!within_wall(run)) return 0;
    if (run->getCount >= C48_MAX_GET_CONTEXT) return fail_run(run, "getBudget");
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
    if (run->setCount >= C48_MAX_SET_CONTEXT) return fail_run(run, "setBudget");
    ++run->setCount;
    setOrdinal = run->setCount;
    memset(&submitted, 0, sizeof(submitted));
    submitted.ContextFlags = CONTEXT_CONTROL;
    submitted.SegCs = before->SegCs;
    submitted.SegSs = before->SegSs;
    submitted.Rsp = before->Rsp;
    submitted.Rip = before->Rip;
    submitted.EFlags = (before->EFlags & ~C48_TF) | (tf ? C48_TF : 0u);
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

static int read_memory(C48Run *run, const char *purpose, uint64_t address, uint32_t width,
    uint64_t allowedBegin, uint64_t allowedEnd, uint8_t *data)
{
    C48Line line;
    SIZE_T actual = 0;
    BOOL ok;
    DWORD error;
    uint32_t maxReads = run->caseId == C48_CASE_YMM ? 64u : 16u;
    uint32_t maxBytes = run->caseId == C48_CASE_YMM ? 4096u : 512u;
    if (!within_wall(run)) return 0;
    if (run->readCount >= maxReads || width > maxBytes - run->readBytes)
        return fail_run(run, "readBudget");
    if (address < allowedBegin || address > allowedEnd || width > allowedEnd - address)
        return fail_run(run, "memoryOutsideAllowedSpan");
    ++run->readCount;
    run->readBytes += width;
    ok = ReadProcessMemory(run->pi.hProcess, (LPCVOID)(uintptr_t)address, data, width, &actual);
    error = ok ? 0 : GetLastError();
    line_begin(&run->log, &line, "memory");
    line_add(&line, ",\"apiOrdinal\":%u,\"stopEventSeq\":%llu,\"purpose\":\"%s\","
        "\"address\":\"%016llx\",\"width\":%u,\"allowedRegion\":{\"begin\":\"%016llx\","
        "\"endExclusive\":\"%016llx\"},\"ReadProcessMemoryBOOL\":%s,\"lastErrorIfFalse\":",
        run->readCount, (unsigned long long)run->log.stopSeq, purpose,
        (unsigned long long)address, width, (unsigned long long)allowedBegin,
        (unsigned long long)allowedEnd, ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
    line_add(&line, ",\"actualBytesRead\":%llu,\"rawHex\":\"", (unsigned long long)actual);
    line_hex(&line, data, actual <= width ? actual : width);
    line_add(&line, "\"");
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    if (!ok || actual != width) return fail_run(run, "ReadProcessMemory");
    return 1;
}

typedef struct C48Transaction {
    uint32_t id;
    uint64_t preEvent;
    CONTEXT pre;
    uint8_t instruction[10];
    uint32_t instructionBytes;
    uint64_t nextPc;
    const char *origin;
    const char *direction;
    uint64_t ea;
    uint64_t allowedBegin;
    uint64_t allowedEnd;
    uint32_t width;
    uint32_t preRead;
    uint32_t postRead;
    int preReadValid;
    int postReadValid;
    uint8_t oldBytes[32];
    uint8_t newBytes[32];
    uint32_t tfSet;
    uint32_t continued;
    uint64_t postEvent;
    CONTEXT post;
    int retired;
    int retirementKnown;
    int faultObserved;
    int recordAttempted;
} C48Transaction;

static int emit_transaction(C48Run *run, C48Transaction *tx)
{
    C48Line line;
    const char *status = !tx->retirementKnown ? "retirement_unconfirmed" :
        (tx->retired ? "retired" : (tx->faultObserved ? "unretired" : "not_executed"));
    tx->recordAttempted = 1;
    line_begin(&run->log, &line, "transaction");
    line_add(&line, ",\"txId\":%u,\"instructionOrigin\":\"%s\",\"preEventSeq\":%llu,"
        "\"preRip\":\"%016llx\",\"instructionBytes\":", tx->id, tx->origin,
        (unsigned long long)tx->preEvent, (unsigned long long)tx->pre.Rip);
    if (tx->instructionBytes) {
        line_add(&line, "\""); line_hex(&line, tx->instruction, tx->instructionBytes); line_add(&line, "\"");
    } else line_add(&line, "null");
    line_add(&line, ",\"preGPR\":");
    line_integer(&line, &tx->pre);
    line_add(&line, ",\"preEFlags\":\"%08lx\",\"ops\":[", (unsigned long)tx->pre.EFlags);
    if (tx->width != 0) {
        line_add(&line, "{\"direction\":\"%s\",\"EA\":\"%016llx\",\"width\":%u,"
            "\"allowedSpan\":{\"begin\":\"%016llx\",\"endExclusive\":\"%016llx\"},"
            "\"preReadOrdinal\":", tx->direction, (unsigned long long)tx->ea, tx->width,
            (unsigned long long)tx->allowedBegin, (unsigned long long)tx->allowedEnd);
        if (tx->preRead) line_add(&line, "%u", tx->preRead); else line_add(&line, "null");
        line_add(&line, ",\"preHex\":");
        if (tx->preReadValid) { line_add(&line, "\""); line_hex(&line, tx->oldBytes, tx->width); line_add(&line, "\""); }
        else line_add(&line, "null");
        line_add(&line, ",\"postReadOrdinal\":");
        if (tx->postRead) line_add(&line, "%u", tx->postRead); else line_add(&line, "null");
        line_add(&line, ",\"postHex\":");
        if (tx->postReadValid) { line_add(&line, "\""); line_hex(&line, tx->newBytes, tx->width); line_add(&line, "\""); }
        else line_add(&line, "null");
        line_add(&line, "}");
    }
    if (tx->nextPc) line_add(&line, "],\"expectedNextPCs\":[\"%016llx\"],\"tfSetOrdinal\":",
        (unsigned long long)tx->nextPc);
    else line_add(&line, "],\"expectedNextPCs\":[],\"tfSetOrdinal\":");
    if (tx->tfSet) line_add(&line, "%u", tx->tfSet); else line_add(&line, "null");
    line_add(&line, ",\"continueOrdinal\":");
    if (tx->continued) line_add(&line, "%u", tx->continued); else line_add(&line, "null");
    line_add(&line, ",\"postEventSeq\":");
    if (tx->postEvent) {
        line_add(&line, "%llu,\"postRip\":\"%016llx\",\"postGPR\":",
            (unsigned long long)tx->postEvent, (unsigned long long)tx->post.Rip);
        line_integer(&line, &tx->post);
        line_add(&line, ",\"postEFlags\":\"%08lx\"", (unsigned long)tx->post.EFlags);
    } else line_add(&line, "null,\"postRip\":null,\"postGPR\":null,\"postEFlags\":null");
    line_add(&line, ",\"retired\":%s,\"status\":\"%s\"",
        tx->retirementKnown ? (tx->retired ? "true" : "false") : "null", status);
    return line_finish(&run->log, &line) ? 1 : fail_run(run, "writeEvents");
}

static int expected_initial_buffer(const uint8_t *buffer)
{
    static const uint8_t guard[3][2] = {{0xa1,0xa2},{0xb1,0xb2},{0xc1,0xc2}};
    uint32_t block, i;
    for (block = 0; block < 3; ++block) {
        const uint8_t *p = buffer + block * C48_BLOCK_BYTES;
        for (i = 0; i < 32; ++i) {
            uint8_t payload = block == 0 ? (uint8_t)(i + 1u) : (block == 1 ? 0xcc : 0xdd);
            if (p[i] != guard[block][0] || p[32u + i] != payload || p[64u + i] != guard[block][1]) return 0;
        }
    }
    return 1;
}

static int expected_final_buffer(const uint8_t *buffer)
{
    static const uint8_t guard[3][2] = {{0xa1,0xa2},{0xb1,0xb2},{0xc1,0xc2}};
    uint32_t block, i;
    for (block = 0; block < 3; ++block) {
        const uint8_t *p = buffer + block * C48_BLOCK_BYTES;
        for (i = 0; i < 32; ++i) {
            uint8_t payload = (uint8_t)((block == 2 ? i % 16u : i) + 1u);
            if (p[i] != guard[block][0] || p[32u + i] != payload || p[64u + i] != guard[block][1]) return 0;
        }
    }
    return 1;
}

static int validate_descriptor(C48Run *run, const C48Arguments *args, const CONTEXT *context)
{
    const C48Descriptor *d = &run->descriptor;
    uint8_t wrapper[C48_WRAPPER_BYTES], leaf[sizeof(C48_YMM_CODE)], buffer[3u * C48_BLOCK_BYTES];
    uint32_t leafRva = run->caseId == C48_CASE_RETURN ? run->bootstrap.returnLeafRva :
        (run->caseId == C48_CASE_YMM ? run->bootstrap.ymmLeafRva : run->bootstrap.faultLeafRva);
    uint32_t leafBytes = run->caseId == C48_CASE_RETURN ? (uint32_t)sizeof(C48_RETURN_CODE) :
        (run->caseId == C48_CASE_YMM ? (uint32_t)sizeof(C48_YMM_CODE) : (uint32_t)sizeof(C48_FAULT_CODE));
    const uint8_t *expected = run->caseId == C48_CASE_RETURN ? C48_RETURN_CODE :
        (run->caseId == C48_CASE_YMM ? C48_YMM_CODE : C48_FAULT_CODE);
    uint64_t imageEnd = run->imageBase + run->bootstrap.imageBytes;
    uint32_t i;
    static const uint8_t zeroReserved[40] = {0};
    if (!read_memory(run, "descriptor", run->imageBase + run->bootstrap.descriptorRva,
        (uint32_t)sizeof(*d), run->imageBase, imageEnd, (uint8_t *)&run->descriptor)) return 0;
    if (memcmp(d->magic, C48_DESCRIPTOR_MAGIC, 8) || d->version != C48_VERSION || d->byteSize != sizeof(*d) ||
        d->caseId != run->caseId || d->childState != C48_STATE_READY || memcmp(d->runId, args->runId, 16) ||
        d->imageBase != run->imageBase || d->pid != run->pi.dwProcessId || d->tid != run->pi.dwThreadId ||
        d->armRva != run->bootstrap.armRva || d->callRva != run->bootstrap.callRva ||
        d->afterCallRva != run->bootstrap.afterCallRva || d->leafRva != leafRva || d->leafBytes != leafBytes ||
        d->stackLow >= d->stackHigh || context->Rsp < d->stackLow || context->Rsp - d->stackLow < 8u ||
        context->Rsp > d->stackHigh || d->stackHigh - context->Rsp < 32u ||
        (context->Rsp & 15u) || context->R11 != run->imageBase + leafRva || d->continuedToC || d->childResultReady ||
        d->reservedU32 || memcmp(d->reserved, zeroReserved, sizeof(zeroReserved)))
        return fail_run(run, "descriptorMismatch");
    if (!read_memory(run, "sourceCode", run->imageBase + run->bootstrap.wrapperBeginRva,
        C48_WRAPPER_BYTES, run->imageBase, imageEnd, wrapper) ||
        !read_memory(run, "sourceCode", run->imageBase + leafRva, leafBytes, run->imageBase, imageEnd, leaf)) return 0;
    if (memcmp(wrapper, C48_WRAPPER_CODE, sizeof(wrapper)) || memcmp(leaf, expected, leafBytes))
        return fail_run(run, "sourceMismatch");
    if (run->caseId == C48_CASE_YMM) {
        if (d->blockCount != 3 || d->arenaReserved != 65536 || d->arenaBase > UINT64_MAX - 65536u ||
            d->arenaBase != d->bufferDomainBase ||
            d->bufferDomainBytes < sizeof(buffer) || d->bufferDomainBytes > d->arenaReserved ||
            d->hostGateValid != 1 || d->cpuidCount != 2 || d->xgetbvCount != 1 ||
            d->cpuidLeaf0[0] < 1 || (d->cpuidLeaf1[2] & 0x1c000000u) != 0x1c000000u || (d->xcr0 & 6u) != 6u)
            return fail_run(run, "ymmDescriptorMismatch");
        for (i = 0; i < 3; ++i) if (d->blockBase[i] != d->arenaBase + i * C48_BLOCK_BYTES)
            return fail_run(run, "blockLayoutMismatch");
        if (context->Rcx != d->blockBase[0] + 32u || context->Rdx != d->blockBase[1] + 32u ||
            context->R8 != d->blockBase[2] + 32u) return fail_run(run, "argumentMismatch");
        if (!read_memory(run, "ownedBufferInitial", d->arenaBase, (uint32_t)sizeof(buffer),
            d->arenaBase, d->arenaBase + sizeof(buffer), buffer)) return 0;
        if (!expected_initial_buffer(buffer)) return fail_run(run, "initialBufferMismatch");
    } else if (d->blockCount || d->cpuidCount || d->xgetbvCount || d->hostGateValid || d->xcr0 ||
        d->arenaBase || d->arenaReserved || d->bufferDomainBase || d->bufferDomainBytes ||
        d->blockBase[0] || d->blockBase[1] || d->blockBase[2] || context->Rcx || context->Rdx || context->R8 ||
        d->cpuidLeaf0[0] || d->cpuidLeaf0[1] || d->cpuidLeaf0[2] || d->cpuidLeaf0[3] ||
        d->cpuidLeaf1[0] || d->cpuidLeaf1[1] || d->cpuidLeaf1[2] || d->cpuidLeaf1[3]) {
        return fail_run(run, "nonYmmDescriptorMismatch");
    }
    return 1;
}

static int prepare_transaction(C48Run *run, const CONTEXT *pre, C48Transaction *tx)
{
    static const uint8_t ymmOffsets[6] = {0,4,8,14,19,22};
    static const uint8_t ymmWidths[6] = {4,4,6,5,3,1};
    uint32_t step = run->retired;
    uint32_t leafRva = run->descriptor.leafRva;
    uint64_t leaf = run->imageBase + leafRva;
    uint64_t after = run->imageBase + run->bootstrap.afterCallRva;
    const uint8_t *expected;
    uint32_t offset;
    uint8_t bytes[10];
    uint32_t instructionWidth;
    memset(tx, 0, sizeof(*tx));
    tx->retirementKnown = 1;
    tx->id = step + 1u;
    tx->preEvent = run->log.stopSeq;
    tx->pre = *pre;
    if (step == 0) {
        tx->origin = "wrapper";
        instructionWidth = 3;
        tx->nextPc = leaf;
        expected = C48_WRAPPER_CODE + C48_CALL_OFFSET;
        if (pre->Rip != run->imageBase + run->bootstrap.callRva || pre->Rsp != run->stackC || pre->R11 != leaf)
            return fail_run(run, "callPreMismatch");
        tx->direction = "write";
        tx->ea = run->stackC - 8u;
        tx->allowedBegin = tx->ea;
        tx->allowedEnd = run->stackC;
        tx->width = 8;
    } else {
        tx->origin = "leaf";
        if (pre->Rsp != run->stackC - 8u) return fail_run(run, "leafRspMismatch");
        if (run->caseId == C48_CASE_RETURN) {
            if (step > 2) return fail_run(run, "unexpectedStep");
            offset = step == 1 ? 0u : 10u;
            instructionWidth = step == 1 ? 10u : 1u;
            tx->nextPc = step == 1 ? leaf + 10u : after;
            expected = C48_RETURN_CODE + offset;
        } else if (run->caseId == C48_CASE_YMM) {
            if (step > 6) return fail_run(run, "unexpectedStep");
            offset = ymmOffsets[step - 1u];
            instructionWidth = ymmWidths[step - 1u];
            tx->nextPc = step == 6 ? after : leaf + ymmOffsets[step];
            expected = C48_YMM_CODE + offset;
        } else {
            if (step != 1) return fail_run(run, "unexpectedStep");
            offset = 0;
            instructionWidth = 2;
            tx->nextPc = 0; /* UD2 has no accepted retirement successor. */
            expected = C48_FAULT_CODE;
        }
        if (pre->Rip != leaf + offset) return fail_run(run, "leafPcMismatch");
        if ((run->caseId == C48_CASE_RETURN && step == 2) || (run->caseId == C48_CASE_YMM && step == 6)) {
            tx->direction = "read"; tx->ea = run->stackC - 8u; tx->allowedBegin = tx->ea;
            tx->allowedEnd = run->stackC; tx->width = 8;
        } else if (run->caseId == C48_CASE_YMM && (step == 1 || step == 2 || step == 4)) {
            uint32_t block = step == 1 ? 0u : (step == 2 ? 1u : 2u);
            tx->direction = step == 1 ? "read" : "write";
            tx->ea = step == 1 ? pre->Rcx : (step == 2 ? pre->Rdx : pre->R8);
            tx->allowedBegin = run->descriptor.blockBase[block] + C48_PAYLOAD_OFFSET;
            tx->allowedEnd = tx->allowedBegin + C48_PAYLOAD_BYTES;
            tx->width = C48_PAYLOAD_BYTES;
            if (tx->ea != tx->allowedBegin) return fail_run(run, "operandEaMismatch");
        }
    }
    if (!read_memory(run, "sourceCode", pre->Rip, instructionWidth,
        run->imageBase, run->imageBase + run->bootstrap.imageBytes, bytes)) return 0;
    tx->instructionBytes = instructionWidth;
    memcpy(tx->instruction, bytes, tx->instructionBytes);
    if (memcmp(bytes, expected, tx->instructionBytes)) return fail_run(run, "instructionMismatch");
    if (tx->width) {
        const char *purpose = step == 0 ? "CALLslotOld" :
            (tx->width == 8 ? "RETslot" : (tx->direction[0] == 'r' ? "operandRead" : "writeOld"));
        uint32_t beforeRead = run->readCount;
        int readOk = read_memory(run, purpose, tx->ea, tx->width, tx->allowedBegin, tx->allowedEnd, tx->oldBytes);
        if (run->readCount != beforeRead) tx->preRead = run->readCount;
        if (!readOk) return 0;
        tx->preReadValid = 1;
        if (tx->width == 32) {
            uint32_t j;
            for (j = 0; j < 32; ++j) {
                uint8_t expectedByte = step == 1 ? (uint8_t)(j + 1u) : (step == 2 ? 0xcc : 0xdd);
                if (tx->oldBytes[j] != expectedByte) return fail_run(run, "operandOldMismatch");
            }
        }
        if (tx->width == 8 && step != 0) {
            uint64_t value;
            memcpy(&value, tx->oldBytes, sizeof(value));
            if (value != after) return fail_run(run, "returnSlotMismatch");
        }
    }
    return 1;
}

static int retire_transaction(C48Run *run, C48Transaction *tx, const CONTEXT *post)
{
    uint32_t step = run->retired;
    uint64_t value;
    uint32_t i;
    if (post->Rip != tx->nextPc) return fail_run(run, "postPcMismatch");
    if (step == 0) {
        if (post->Rsp != run->stackC - 8u) return fail_run(run, "callPostRspMismatch");
        uint32_t beforeRead = run->readCount;
        int readOk = read_memory(run, "CALLslotNew", tx->ea, 8, tx->allowedBegin, tx->allowedEnd, tx->newBytes);
        if (run->readCount != beforeRead) tx->postRead = run->readCount;
        if (!readOk) return 0;
        tx->postReadValid = 1;
        memcpy(&value, tx->newBytes, sizeof(value));
        if (value != run->imageBase + run->bootstrap.afterCallRva) return fail_run(run, "callReturnMismatch");
    } else {
        int isRet = (run->caseId == C48_CASE_RETURN && step == 2) ||
            (run->caseId == C48_CASE_YMM && step == 6);
        if (post->Rsp != (isRet ? run->stackC : run->stackC - 8u)) return fail_run(run, "postRspMismatch");
        if (run->caseId == C48_CASE_RETURN && step == 1 && post->Rax != UINT64_C(0xf123456789abcdef))
            return fail_run(run, "movRaxMismatch");
        if (run->caseId == C48_CASE_RETURN && isRet && post->Rax != UINT64_C(0xf123456789abcdef))
            return fail_run(run, "returnRaxMismatch");
        if (run->caseId == C48_CASE_YMM && tx->width == 32 && tx->direction[0] == 'w') {
            uint32_t beforeRead = run->readCount;
            int readOk = read_memory(run, "writeNew", tx->ea, 32, tx->allowedBegin, tx->allowedEnd, tx->newBytes);
            if (run->readCount != beforeRead) tx->postRead = run->readCount;
            if (!readOk) return 0;
            tx->postReadValid = 1;
            for (i = 0; i < 32; ++i) {
                uint8_t expected = (uint8_t)((step == 4 ? i % 16u : i) + 1u);
                if (tx->newBytes[i] != expected) return fail_run(run, "vectorStoreMismatch");
            }
        }
    }
    tx->retirementKnown = 1;
    tx->retired = 1;
    ++run->retired;
    return emit_transaction(run, tx);
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
    int ntdll = 0, fileHashOk = 0;
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
    if (file != NULL && file != INVALID_HANDLE_VALUE) {
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
        if (ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT) fileHashOk = sha256_handle(file, fileHash);
        closeOk = CloseHandle(file);
        if (!closeOk) closeError = GetLastError();
        if (ntdll && moduleBytes && moduleBase <= UINT64_MAX - moduleBytes) {
            run->ntdllBase = moduleBase;
            run->ntdllEnd = moduleBase + moduleBytes;
        }
    }
    line_begin(&run->log, &line, "event");
    run->log.stopSeq = run->log.sequence;
    line_add(&line, ",\"rawDebugEventCode\":%lu,\"firstChance\":", (unsigned long)ev->dwDebugEventCode);
    if (ev->dwDebugEventCode == EXCEPTION_DEBUG_EVENT) {
        const EXCEPTION_DEBUG_INFO *x = &ev->u.Exception;
        line_add(&line, "%s,\"exceptionCode\":\"%08lx\",\"exceptionAddress\":\"%016llx\","
            "\"exceptionFlags\":\"%08lx\",\"exceptionParameters\":[", x->dwFirstChance ? "true" : "false",
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
        line_add(&line, ",\"fileHandleValid\":%s,\"filePath\":", file && file != INVALID_HANDLE_VALUE ? "true" : "false");
        if (path[0]) line_wide_json(&line, path); else line_add(&line, "null");
        line_add(&line, ",\"filePathError\":");
        if (pathLength == 0 && file && file != INVALID_HANDLE_VALUE) line_add(&line, "%lu", (unsigned long)pathError);
        else line_add(&line, "null");
        line_add(&line, ",\"filePathRequiredLength\":%lu,\"fileIdentityBOOL\":", (unsigned long)pathLength);
        if (file && file != INVALID_HANDLE_VALUE) line_add(&line, "%s", infoOk ? "true" : "false");
        else line_add(&line, "null");
        line_add(&line, ",\"fileIdentityError\":");
        if (file && file != INVALID_HANDLE_VALUE && !infoOk) line_add(&line, "%lu", (unsigned long)infoError);
        else line_add(&line, "null");
        line_add(&line, ",\"fileIdentity\":");
        if (infoOk) line_add(&line, "{\"volumeSerial\":%lu,\"indexHigh\":%lu,\"indexLow\":%lu}",
            (unsigned long)info.dwVolumeSerialNumber, (unsigned long)info.nFileIndexHigh, (unsigned long)info.nFileIndexLow);
        else line_add(&line, "null");
        line_add(&line, ",\"fileCloseBOOL\":");
        if (file && file != INVALID_HANDLE_VALUE) line_add(&line, "%s", closeOk ? "true" : "false");
        else line_add(&line, "null");
        line_add(&line, ",\"fileCloseError\":");
        if (file && file != INVALID_HANDLE_VALUE && !closeOk) line_add(&line, "%lu", (unsigned long)closeError);
        else line_add(&line, "null");
        if (ev->dwDebugEventCode == CREATE_PROCESS_DEBUG_EVENT) {
            line_add(&line, ",\"debugProcessHandleOrigin\":\"CREATE_PROCESS.borrowed\","
                "\"debugThreadHandleOrigin\":\"CREATE_PROCESS.borrowed\",\"startAddress\":\"%016llx\","
                "\"threadLocalBase\":\"%016llx\"",
                (unsigned long long)(uintptr_t)ev->u.CreateProcessInfo.lpStartAddress,
                (unsigned long long)(uintptr_t)ev->u.CreateProcessInfo.lpThreadLocalBase);
        }
        line_add(&line, ",\"ntdllNameMatch\":%s,\"fileSHA256\":", ntdll ? "true" : "false");
        if (fileHashOk) { line_add(&line, "\""); line_hex(&line, fileHash, 32); line_add(&line, "\""); }
        else line_add(&line, "null");
    }
    if (ev->dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT)
        line_add(&line, ",\"exitCode\":%lu", (unsigned long)ev->u.ExitProcess.dwExitCode);
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    if (file && file != INVALID_HANDLE_VALUE && !closeOk) return fail_run(run, "eventFileClose");
    if (file && file != INVALID_HANDLE_VALUE && (!infoOk || !path[0] || !moduleBytes))
        return fail_run(run, "eventFileIdentity");
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
    if (run->eventCount >= C48_MAX_EVENTS) return fail_run(run, "eventBudget");
    elapsed = GetTickCount64() - run->log.started;
    if (elapsed >= C48_WALL_MILLISECONDS) return fail_run(run, "wallBudget");
    slice = (DWORD)(C48_WALL_MILLISECONDS - elapsed);
    if (slice > C48_WAIT_MILLISECONDS) slice = C48_WAIT_MILLISECONDS;
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
    line_add(&line, ",\"apiOrdinal\":%u,\"stopEventSeq\":%llu,\"continueStatus\":\"%08lx\","
        "\"BOOL\":%s,\"lastErrorIfFalse\":", run->continueCount, (unsigned long long)run->log.stopSeq,
        (unsigned long)status, ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
    line_add(&line, ",\"cleanupOnly\":%s", cleanup ? "true" : "false");
    if (cleanup) ++run->cleanupContinues;
    if (ok) run->outstanding = 0;
    if (!line_finish(&run->log, &line)) return fail_run(run, "writeEvents");
    if (!ok) return fail_run(run, "ContinueDebugEvent");
    return 1;
}

static int log_calibration(C48Run *run, const char *name, const CONTEXT *context, uint64_t moduleBase)
{
    C48Line line;
    uint64_t address = (uint64_t)(uintptr_t)run->event.u.Exception.ExceptionRecord.ExceptionAddress;
    line_begin(&run->log, &line, "calibration");
    line_add(&line, ",\"name\":\"%s\",\"stopEventSeq\":%llu,\"exceptionAddress\":\"%016llx\","
        "\"contextRip\":\"%016llx\",\"moduleBase\":\"%016llx\",\"exceptionRva\":",
        name, (unsigned long long)run->log.stopSeq, (unsigned long long)address,
        (unsigned long long)context->Rip, (unsigned long long)moduleBase);
    if (address >= moduleBase) line_add(&line, "\"%016llx\"", (unsigned long long)(address - moduleBase));
    else line_add(&line, "null");
    return line_finish(&run->log, &line) ? 1 : fail_run(run, "writeEvents");
}

static int event_loop(C48Run *run, const C48Arguments *args)
{
    C48Transaction tx;
    CONTEXT context, readback;
    uint8_t buffer[3u * C48_BLOCK_BYTES];
    int waitResult;
    memset(&tx, 0, sizeof(tx));
    for (;;) {
        DWORD code;
        uint64_t address;
        waitResult = wait_event(run);
        if (waitResult == 2) continue;
        if (!waitResult) break;
        if (run->event.dwProcessId != run->pi.dwProcessId || run->event.dwThreadId != run->pi.dwThreadId) {
            fail_run(run, "unknownPidOrThread"); break;
        }
        code = run->event.dwDebugEventCode;
        if (strcmp(run->log.phase, "BOOT") == 0) {
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
            if (code == LOAD_DLL_DEBUG_EVENT) {
                if (!continue_event(run, DBG_CONTINUE, 0)) break;
                continue;
            }
            if (code != EXCEPTION_DEBUG_EVENT || !run->event.u.Exception.dwFirstChance ||
                run->event.u.Exception.ExceptionRecord.ExceptionCode != EXCEPTION_BREAKPOINT) {
                fail_run(run, "unexpectedBootEvent"); break;
            }
            address = (uint64_t)(uintptr_t)run->event.u.Exception.ExceptionRecord.ExceptionAddress;
            if (!run->systemBreakpointSeen) {
                if (!run->ntdllBase || address < run->ntdllBase || address >= run->ntdllEnd) {
                    fail_run(run, "systemBreakpointNotNtdll"); break;
                }
                if (!get_context(run, &context) || !log_calibration(run, "systemBreakpoint", &context, run->ntdllBase)) break;
                run->systemBreakpointSeen = 1;
                if (!continue_event(run, DBG_CONTINUE, 0)) break;
                continue;
            }
            run->log.phase = "ARM_STOP";
            if (!get_context(run, &context) || !log_calibration(run, "ownedInt3", &context, run->imageBase)) break;
            if (address != run->imageBase + run->bootstrap.armRva ||
                context.Rip != run->imageBase + run->bootstrap.callRva) {
                fail_run(run, "ownedBreakpointMismatch"); break;
            }
            if ((context.EFlags & C48_TF) != 0) { fail_run(run, "originalTfNonzero"); break; }
            run->originalTf = (context.EFlags & C48_TF) != 0;
            run->stackC = context.Rsp;
            if (!validate_descriptor(run, args, &context)) break;
        } else if (strcmp(run->log.phase, "PENDING") == 0) {
            run->log.phase = "POST_STOP";
            if (!run->pendingValid || code != EXCEPTION_DEBUG_EVENT || !run->event.u.Exception.dwFirstChance) {
                fail_run(run, "unexpectedPendingEvent"); break;
            }
            address = (uint64_t)(uintptr_t)run->event.u.Exception.ExceptionRecord.ExceptionAddress;
            if (run->caseId == C48_CASE_FAULT && run->retired == 1) {
                if (run->event.u.Exception.ExceptionRecord.ExceptionCode != EXCEPTION_ILLEGAL_INSTRUCTION ||
                    address != run->imageBase + run->bootstrap.faultLeafRva) {
                    fail_run(run, "faultMismatch"); break;
                }
            } else if (run->event.u.Exception.ExceptionRecord.ExceptionCode != EXCEPTION_SINGLE_STEP) {
                fail_run(run, "unexpectedException"); break;
            }
            {
                int got = get_context(run, &context);
                if (run->lastContextStop == run->log.stopSeq) {
                    tx.postEvent = run->log.stopSeq;
                    tx.post = run->lastContext;
                }
                if (!got) break;
            }
            if (run->caseId == C48_CASE_FAULT && run->retired == 1) {
                if (context.Rip != address || context.Rsp != run->stackC - 8u) {
                    fail_run(run, "faultMismatch"); break;
                }
                tx.retirementKnown = 1;
                tx.faultObserved = 1;
                fail_run(run, "expectedUD2"); break;
            }
            if (!log_calibration(run, "singleStep", &context, run->imageBase) ||
                !retire_transaction(run, &tx, &context)) break;
            run->pendingValid = 0;
            run->pendingId = 0;
            if ((run->caseId == C48_CASE_RETURN && run->retired == 3) ||
                (run->caseId == C48_CASE_YMM && run->retired == 7)) {
                run->log.phase = "TRACE_DONE";
                if (run->caseId == C48_CASE_YMM) {
                    if (!read_memory(run, "ownedBufferFinal", run->descriptor.arenaBase, (uint32_t)sizeof(buffer),
                        run->descriptor.arenaBase, run->descriptor.arenaBase + sizeof(buffer), buffer) ||
                        !expected_final_buffer(buffer)) { fail_run(run, "finalBufferMismatch"); break; }
                }
                if (!set_tf(run, &context, run->originalTf, &readback)) break;
                if (!continue_event(run, DBG_CONTINUE, 0)) break;
                run->log.phase = "WAIT_EXIT";
                continue;
            }
        } else if (strcmp(run->log.phase, "WAIT_EXIT") == 0) {
            if (code == LOAD_DLL_DEBUG_EVENT) {
                if (!continue_event(run, DBG_CONTINUE, 0)) break;
                continue;
            }
            if (code != EXIT_PROCESS_DEBUG_EVENT) { fail_run(run, "unexpectedExitEvent"); break; }
            run->exitSeen = 1;
            run->eventExitCode = run->event.u.ExitProcess.dwExitCode;
            if (!continue_event(run, DBG_CONTINUE, 0)) break;
            run->exitContinued = 1;
            run->log.phase = "EXITED";
            return 1;
        } else { fail_run(run, "invalidState"); break; }
        run->pendingValid = 1;
        run->pendingId = run->retired + 1u;
        memset(&tx, 0, sizeof(tx));
        if (!prepare_transaction(run, &context, &tx)) break;
        {
            uint32_t beforeSet = run->setCount;
            int setOk = set_tf(run, &context, 1, &readback);
            if (run->setCount != beforeSet) tx.tfSet = run->setCount;
            if (!setOk) break;
        }
        {
            uint32_t beforeContinue = run->continueCount;
            int continueOk = continue_event(run, DBG_CONTINUE, 0);
            if (run->continueCount != beforeContinue) {
                tx.continued = run->continueCount;
                tx.retirementKnown = 0;
            }
            if (!continueOk) break;
        }
        run->log.phase = "PENDING";
    }
    run->pendingRetired = tx.retired;
    run->pendingRetirementKnown = tx.retirementKnown;
    if (run->pendingValid && tx.origin != NULL && !tx.recordAttempted) (void)emit_transaction(run, &tx);
    return 0;
}

static int log_api(C48Run *run, const char *name, BOOL ok, DWORD error, const char *origin)
{
    C48Line line;
    line_begin(&run->log, &line, "cleanup");
    line_add(&line, ",\"api\":\"%s\",\"handleOrigin\":\"%s\",\"BOOL\":%s,\"lastErrorIfFalse\":",
        name, origin, ok ? "true" : "false");
    if (ok) line_add(&line, "null"); else line_add(&line, "%lu", (unsigned long)error);
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
        ok = TerminateProcess(run->pi.hProcess, C48_ABORT_EXIT);
        error = ok ? 0 : GetLastError();
        run->terminateOk = ok != FALSE;
        (void)log_api(run, "TerminateProcess", ok, error, "CreateProcess.PI.hProcess");
        if (run->terminateOk) {
            if (run->outstanding) (void)continue_event(run, DBG_CONTINUE, 1);
            while (!run->outstanding && !run->exitContinued) {
                next = wait_event(run);
                if (next == 2) continue;
                if (!next) break;
                if (run->event.dwDebugEventCode == EXIT_PROCESS_DEBUG_EVENT &&
                    run->event.dwProcessId == run->pi.dwProcessId) {
                    run->exitSeen = 1;
                    run->eventExitCode = run->event.u.ExitProcess.dwExitCode;
                    if (run->event.dwThreadId != run->pi.dwThreadId) run->cleanupInconsistent = 1;
                    if (continue_event(run, DBG_CONTINUE, 1)) run->exitContinued = 1;
                    break;
                }
                if (run->event.dwProcessId != run->pi.dwProcessId || run->event.dwThreadId != run->pi.dwThreadId)
                    run->cleanupInconsistent = 1;
                if (!continue_event(run, DBG_CONTINUE, 1)) break;
            }
        }
    }
    if (run->exitContinued) {
        elapsed = GetTickCount64() - run->log.started;
        timeout = elapsed >= C48_WALL_MILLISECONDS ? 0u : (DWORD)(C48_WALL_MILLISECONDS - elapsed);
        waitResult = WaitForSingleObject(run->pi.hProcess, timeout);
        error = waitResult == WAIT_FAILED ? GetLastError() : 0;
        {
            C48Line line;
            line_begin(&run->log, &line, "cleanup");
            line_add(&line, ",\"api\":\"WaitForSingleObject\",\"handleOrigin\":\"CreateProcess.PI.hProcess\","
                "\"timeoutMs\":%lu,\"result\":%lu,\"lastErrorIfFailed\":",
                (unsigned long)timeout, (unsigned long)waitResult);
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

static int write_summary(HANDLE file, C48Run *run, const C48Arguments *args, int traceDone, int cleanupComplete)
{
    C48Line line;
    DWORD written;
    char id[33];
    size_t i;
    int normal = traceDone && cleanupComplete && run->childExit == 0 && run->failure == NULL &&
        (run->caseId == C48_CASE_RETURN || run->caseId == C48_CASE_YMM);
    memset(&line, 0, sizeof(line));
    for (i = 0; i < 32; ++i) id[i] = (char)args->runIdText[i];
    id[32] = 0;
    line_add(&line, "{\"schema\":\"c48-parent-result-v1\",\"caseId\":%u,\"runId\":\"%s\","
        "\"epochValidated\":%s,\"normalTracePassed\":%s,\"firstFailureKind\":",
        run->caseId, id, run->epochValidated ? "true" : "false", normal ? "true" : "false");
    if (run->failure) line_add(&line, "\"%s\"", run->failure); else line_add(&line, "null");
    line_add(&line, ",\"pid\":");
    if (run->childCreated) line_add(&line, "%lu", (unsigned long)run->pi.dwProcessId); else line_add(&line, "null");
    line_add(&line, ",\"tid\":");
    if (run->childCreated) line_add(&line, "%lu", (unsigned long)run->pi.dwThreadId); else line_add(&line, "null");
    line_add(&line, ",\"stopEventSeq\":");
    if (run->firstFailureStop) line_add(&line, "%llu", (unsigned long long)run->firstFailureStop); else line_add(&line, "null");
    line_add(&line, ",\"rawCode\":");
    if (run->firstHadException) line_add(&line, "\"%08lx\"", (unsigned long)run->firstRawCode); else line_add(&line, "null");
    line_add(&line, ",\"exceptionAddress\":");
    if (run->firstHadException) line_add(&line, "\"%016llx\"", (unsigned long long)run->firstExceptionAddress); else line_add(&line, "null");
    line_add(&line, ",\"contextRip\":");
    if (run->firstHadContext) line_add(&line, "\"%016llx\"", (unsigned long long)run->firstContextRip); else line_add(&line, "null");
    line_add(&line, ",\"pendingTxId\":");
    if (run->pendingValid) line_add(&line, "%u", run->pendingId); else line_add(&line, "null");
    line_add(&line, ",\"pendingRetired\":%s,\"lastCompletedTx\":%u,\"TerminateProcessBOOL\":",
        run->pendingValid && run->pendingRetirementKnown ? (run->pendingRetired ? "true" : "false") : "null", run->retired);
    if (traceDone || !run->childCreated) line_add(&line, "null"); else line_add(&line, "%s", run->terminateOk ? "true" : "false");
    line_add(&line, ",\"terminationRequestedExit\":");
    if (traceDone || !run->childCreated) line_add(&line, "null"); else line_add(&line, "%u", C48_ABORT_EXIT);
    line_add(&line, ",\"cleanupContinues\":%u,\"EXIT_PROCESSobserved\":%s,\"EXIT_PROCESScontinued\":%s,"
        "\"ownedProcessSignaled\":%s,\"GetExitCodeProcessBOOL\":", run->cleanupContinues,
        run->exitSeen ? "true" : "false", run->exitContinued ? "true" : "false", run->processSignaled ? "true" : "false");
    if (run->processSignaled) line_add(&line, "%s", run->getExitOk ? "true" : "false"); else line_add(&line, "null");
    line_add(&line, ",\"childExitCode\":");
    if (run->getExitOk) line_add(&line, "%lu", (unsigned long)run->childExit); else line_add(&line, "null");
    line_add(&line, ",\"killOnDebuggerExitAccepted\":");
    if (run->killAttempted) line_add(&line, "%s", run->killAccepted ? "true" : "false");
    else line_add(&line, "null");
    line_add(&line, ",\"cleanupComplete\":%s,\"sourceProof\":", cleanupComplete ? "true" : "false");
    if (run->epochValidated) {
        line_add(&line, "{\"childExeSHA256\":\""); line_hex(&line, run->sourceChildSHA, 32);
        line_add(&line, "\",\"bootstrapSHA256\":\""); line_hex(&line, run->sourceBootstrapSHA, 32);
        line_add(&line, "\",\"sharedHeaderSHA256\":\""); line_hex(&line, run->sourceHeaderSHA, 32);
        line_add(&line, "\",\"imageBytes\":\"%016llx\",\"descriptorRva\":%u,\"wrapperBeginRva\":%u,"
            "\"armRva\":%u,\"callRva\":%u,\"afterCallRva\":%u,\"wrapperEndRva\":%u,"
            "\"returnLeafRva\":%u,\"ymmLeafRva\":%u,\"faultLeafRva\":%u}",
            (unsigned long long)run->sourceImageBytes, run->bootstrap.descriptorRva, run->bootstrap.wrapperBeginRva,
            run->bootstrap.armRva, run->bootstrap.callRva, run->bootstrap.afterCallRva, run->bootstrap.wrapperEndRva,
            run->bootstrap.returnLeafRva, run->bootstrap.ymmLeafRva, run->bootstrap.faultLeafRva);
    } else line_add(&line, "null");
    line_add(&line, ",\"budgets\":{\"events\":%u,\"waits\":%u,\"gets\":%u,\"sets\":%u,\"reads\":%u,"
        "\"readRequestedBytes\":%u,\"continues\":%u,\"retired\":%u},\"PIThreadCloseBOOL\":",
        run->eventCount, run->waitCount, run->getCount, run->setCount, run->readCount,
        run->readBytes, run->continueCount, run->retired);
    if (run->childCreated) line_add(&line, "%s", run->threadCloseOk ? "true" : "false"); else line_add(&line, "null");
    line_add(&line, ",\"PIProcessCloseBOOL\":");
    if (run->childCreated) line_add(&line, "%s}\n", run->processCloseOk ? "true" : "false"); else line_add(&line, "null}\n");
    if (line.failed || !WriteFile(file, line.text, (DWORD)line.length, &written, NULL) || written != (DWORD)line.length) return 0;
    return 1;
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

static int run_witness(const C48Arguments *args)
{
    C48Run run;
    STARTUPINFOW startup;
    HANDLE resultFile = INVALID_HANDLE_VALUE, stdoutFile = INVALID_HANDLE_VALUE;
    HANDLE stderrFile = INVALID_HANDLE_VALUE, inputFile = INVALID_HANDLE_VALUE;
    SECURITY_ATTRIBUTES security;
    wchar_t childResult[32768], command[32768];
    BOOL created, ok;
    DWORD error, attributes;
    uint8_t childHash[32], headerHash[32], bootstrapHash[32];
    uint64_t imageBytes = 0;
    int traceDone = 0, cleanupComplete = 0, result = 2;
    memset(&run, 0, sizeof(run));
    memset(&startup, 0, sizeof(startup));
    memset(&security, 0, sizeof(security));
    run.caseId = args->caseId;
    run.log.file = INVALID_HANDLE_VALUE;
    run.log.phase = "BOOT";
    if (!CreateDirectoryW(args->outputPath, NULL)) return 2;
    run.log.started = GetTickCount64();
    run.log.file = create_output(args->outputPath, L"events.jsonl", 0);
    if (run.log.file == INVALID_HANDLE_VALUE) return 2;
    resultFile = create_output(args->outputPath, L"parent-result.json", 0);
    if (resultFile == INVALID_HANDLE_VALUE) {
        error = GetLastError();
        (void)log_api(&run, "CreateFileW", FALSE, error, "parentResult");
        goto done;
    }
    stdoutFile = create_output(args->outputPath, L"stdout.txt", 1);
    if (stdoutFile == INVALID_HANDLE_VALUE) {
        error = GetLastError(); fail_run(&run, "createStdout");
        (void)log_api(&run, "CreateFileW", FALSE, error, "stdout");
        (void)write_summary(resultFile, &run, args, 0, 0); goto done;
    }
    stderrFile = create_output(args->outputPath, L"stderr.txt", 1);
    if (stderrFile == INVALID_HANDLE_VALUE) {
        error = GetLastError(); fail_run(&run, "createStderr");
        (void)log_api(&run, "CreateFileW", FALSE, error, "stderr");
        (void)write_summary(resultFile, &run, args, 0, 0); goto done;
    }
    security.nLength = (DWORD)sizeof(security);
    security.bInheritHandle = TRUE;
    inputFile = CreateFileW(L"NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, &security,
        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (inputFile == INVALID_HANDLE_VALUE) {
        error = GetLastError(); fail_run(&run, "openNullInput");
        (void)log_api(&run, "CreateFileW", FALSE, error, "NULInput");
        (void)write_summary(resultFile, &run, args, 0, 0); goto done;
    }
    if (_snwprintf_s(childResult, sizeof(childResult) / sizeof(childResult[0]), _TRUNCATE,
            L"%ls\\child-result.json", args->outputPath) < 0 ||
        _snwprintf_s(command, sizeof(command) / sizeof(command[0]), _TRUNCATE,
            L"\"%ls\" --case %u --run-id %ls --output \"%ls\"", args->childPath,
            (unsigned)args->caseId, args->runIdText, childResult) < 0) {
        fail_run(&run, "commandOrPathTooLong");
        (void)write_summary(resultFile, &run, args, 0, 0); goto done;
    }
    ok = read_bootstrap(args->bootstrapPath, &run.bootstrap);
    if (!ok) fail_run(&run, "bootstrapRead");
    else if (!sha256_file(args->bootstrapPath, bootstrapHash)) fail_run(&run, "bootstrapHash");
    else if (!validate_epoch(args, &run.bootstrap, childHash, headerHash, &imageBytes)) fail_run(&run, "epochMismatch");
    if (run.failure) {
        (void)write_summary(resultFile, &run, args, 0, 0);
        goto done;
    }
    run.epochValidated = 1;
    memcpy(run.sourceChildSHA, childHash, 32);
    memcpy(run.sourceBootstrapSHA, bootstrapHash, 32);
    memcpy(run.sourceHeaderSHA, headerHash, 32);
    run.sourceImageBytes = imageBytes;
    {
        C48Line line;
        line_begin(&run.log, &line, "epoch");
        line_add(&line, ",\"childExeSHA256\":\""); line_hex(&line, childHash, 32);
        line_add(&line, "\",\"bootstrapSHA256\":\""); line_hex(&line, bootstrapHash, 32);
        line_add(&line, "\",\"sharedHeaderSHA256\":\""); line_hex(&line, headerHash, 32);
        line_add(&line, "\",\"imageBytes\":\"%016llx\",\"descriptorRva\":%u,\"wrapperBeginRva\":%u,"
            "\"armRva\":%u,\"callRva\":%u,\"afterCallRva\":%u,\"wrapperEndRva\":%u,"
            "\"returnLeafRva\":%u,\"ymmLeafRva\":%u,\"faultLeafRva\":%u",
            (unsigned long long)imageBytes, run.bootstrap.descriptorRva, run.bootstrap.wrapperBeginRva,
            run.bootstrap.armRva, run.bootstrap.callRva, run.bootstrap.afterCallRva, run.bootstrap.wrapperEndRva,
            run.bootstrap.returnLeafRva, run.bootstrap.ymmLeafRva, run.bootstrap.faultLeafRva);
        if (!line_finish(&run.log, &line)) fail_run(&run, "writeEvents");
    }
    if (!run.failure && GetTickCount64() - run.log.started >= C48_WALL_MILLISECONDS)
        fail_run(&run, "wallBudget");
    if (run.failure) { (void)write_summary(resultFile, &run, args, 0, 0); goto done; }
    startup.cb = (DWORD)sizeof(startup);
    startup.dwFlags = STARTF_USESHOWWINDOW | STARTF_USESTDHANDLES;
    startup.wShowWindow = SW_HIDE;
    startup.hStdInput = inputFile;
    startup.hStdOutput = stdoutFile;
    startup.hStdError = stderrFile;
    created = CreateProcessW(args->childPath, command, NULL, NULL, TRUE,
        DEBUG_ONLY_THIS_PROCESS | CREATE_NO_WINDOW, NULL, NULL, &startup, &run.pi);
    error = created ? 0 : GetLastError();
    run.childCreated = created != FALSE;
    if (!log_api(&run, "CreateProcessW", created, error, "newOwnedChild")) {
        if (!created) { (void)write_summary(resultFile, &run, args, 0, 0); goto done; }
        fail_run(&run, "writeEvents");
    }
    if (created) {
        C48Line line;
        FILETIME creation = {0}, exitTime, kernelTime, userTime;
        BOOL timesOk = FALSE;
        int timesAttempted = 0;
        DWORD timesError = 0;
        if (!run.failure && within_wall(&run)) {
            timesAttempted = 1;
            timesOk = GetProcessTimes(run.pi.hProcess, &creation, &exitTime, &kernelTime, &userTime);
            if (!timesOk) { timesError = GetLastError(); fail_run(&run, "childIdentityGetProcessTimes"); }
        }
        line_begin(&run.log, &line, "ownedChild");
        line_add(&line, ",\"pid\":%lu,\"tid\":%lu,\"processHandleOrigin\":\"CreateProcess.PI.hProcess\","
            "\"threadHandleOrigin\":\"CreateProcess.PI.hThread\",\"GetProcessTimesBOOL\":",
            (unsigned long)run.pi.dwProcessId, (unsigned long)run.pi.dwThreadId);
        if (timesAttempted) line_add(&line, "%s", timesOk ? "true" : "false"); else line_add(&line, "null");
        line_add(&line, ",\"lastErrorIfFalse\":");
        if (timesAttempted && !timesOk) line_add(&line, "%lu", (unsigned long)timesError); else line_add(&line, "null");
        line_add(&line, ",\"creationFileTime\":");
        if (timesOk) line_add(&line, "\"%016llx\"",
            (unsigned long long)(((uint64_t)creation.dwHighDateTime << 32) | creation.dwLowDateTime));
        else line_add(&line, "null");
        if (!line_finish(&run.log, &line)) fail_run(&run, "writeEvents");
    }
    ok = CloseHandle(inputFile); error = ok ? 0 : GetLastError(); inputFile = INVALID_HANDLE_VALUE;
    if (!log_api(&run, "CloseHandle", ok, error, "NULInput") || !ok) fail_run(&run, "inputClose");
    ok = CloseHandle(stdoutFile); error = ok ? 0 : GetLastError(); stdoutFile = INVALID_HANDLE_VALUE;
    if (!log_api(&run, "CloseHandle", ok, error, "stdout") || !ok) fail_run(&run, "stdoutClose");
    ok = CloseHandle(stderrFile); error = ok ? 0 : GetLastError(); stderrFile = INVALID_HANDLE_VALUE;
    if (!log_api(&run, "CloseHandle", ok, error, "stderr") || !ok) fail_run(&run, "stderrClose");
    if (!created) {
        fail_run(&run, "CreateProcessW");
        (void)write_summary(resultFile, &run, args, 0, 0);
        goto done;
    }
    run.killAttempted = 1;
    ok = DebugSetProcessKillOnExit(TRUE);
    error = ok ? 0 : GetLastError();
    run.killAccepted = ok != FALSE;
    if (!log_api(&run, "DebugSetProcessKillOnExit", ok, error, "debuggerThread") || !ok)
        fail_run(&run, "DebugSetProcessKillOnExit");
    if (!run.failure) traceDone = event_loop(&run, args);
    cleanupComplete = cleanup_run(&run, traceDone);
    attributes = GetFileAttributesW(childResult);
    error = attributes == INVALID_FILE_ATTRIBUTES ? GetLastError() : 0;
    ok = attributes != INVALID_FILE_ATTRIBUTES && !(attributes & FILE_ATTRIBUTE_DIRECTORY);
    (void)log_api(&run, "GetFileAttributesW", attributes != INVALID_FILE_ATTRIBUTES, error, "childResult");
    if (run.log.failed) cleanupComplete = 0;
    if (traceDone && (!cleanupComplete || run.childExit != 0 || !ok)) fail_run(&run, "normalExitOrChildResult");
    if (!traceDone && run.caseId == C48_CASE_FAULT && ok) fail_run(&run, "unexpectedChildResult");
    if (!write_summary(resultFile, &run, args, traceDone, cleanupComplete)) goto done;
    if (traceDone && cleanupComplete && run.childExit == 0 && run.failure == NULL) result = 0;
    else if (!traceDone && run.caseId == C48_CASE_FAULT && run.failure &&
        strcmp(run.failure, "expectedUD2") == 0 && run.terminateOk && cleanupComplete &&
        run.childExit == C48_ABORT_EXIT && !run.log.failed && !ok && error == ERROR_FILE_NOT_FOUND) result = C48_PARENT_EXPECTED_FAULT_EXIT;
done:
    if (inputFile != INVALID_HANDLE_VALUE) (void)CloseHandle(inputFile);
    if (stdoutFile != INVALID_HANDLE_VALUE) (void)CloseHandle(stdoutFile);
    if (stderrFile != INVALID_HANDLE_VALUE) (void)CloseHandle(stderrFile);
    if (resultFile != INVALID_HANDLE_VALUE && !CloseHandle(resultFile)) result = 2;
    if (run.log.file != INVALID_HANDLE_VALUE && !CloseHandle(run.log.file)) result = 2;
    return result;
}
