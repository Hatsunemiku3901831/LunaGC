#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <intrin.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>
#include <io.h>
#include <fcntl.h>

extern void Probe(const unsigned char *, unsigned char *, unsigned char *);
extern uint64_t ReadXcr0(void);
extern void Witness(void);
extern unsigned char ProbeEntry, ProbeSite, ProbeReturn, ProbeEnd, Xcr0Entry, Xcr0Site, Xcr0Return, Xcr0End;
extern unsigned char WitnessEntry, WitnessSite, WitnessEnd;

typedef struct { unsigned char pre[32], data[32], post[32]; } Block;
typedef struct {
    DWORD code; uintptr_t address, rip, rsp, rax, rcx, rdx, r8, r9, r10, r11, image;
    int seen, phase;
} Fault;
static Fault fault;
static int save_fault(EXCEPTION_POINTERS *p, int phase) {
    CONTEXT *c = p->ContextRecord;
    fault.code = p->ExceptionRecord->ExceptionCode;
    fault.address = (uintptr_t)p->ExceptionRecord->ExceptionAddress;
    fault.rip = (uintptr_t)c->Rip; fault.rsp = (uintptr_t)c->Rsp;
    fault.rax = (uintptr_t)c->Rax; fault.rcx = (uintptr_t)c->Rcx;
    fault.rdx = (uintptr_t)c->Rdx; fault.r8 = (uintptr_t)c->R8; fault.r9 = (uintptr_t)c->R9;
    fault.r10 = (uintptr_t)c->R10; fault.r11 = (uintptr_t)c->R11;
    fault.image = (uintptr_t)GetModuleHandleW(NULL); fault.phase = phase; fault.seen = 1;
    return EXCEPTION_EXECUTE_HANDLER;
}
static void hex(FILE *f, const void *p, size_t n) {
    const unsigned char *b = (const unsigned char *)p; size_t i;
    for (i = 0; i < n; ++i) fprintf(f, "%02x", b[i]);
}
static void regs(FILE *f, const int r[4]) {
    fprintf(f, "[%u,%u,%u,%u]", (unsigned)r[0], (unsigned)r[1], (unsigned)r[2], (unsigned)r[3]);
}
int wmain(int argc, wchar_t **argv) {
    static const unsigned char probe_expected[23] = {0xc5,0xfe,0x6f,1,0xc5,0xfe,0x7f,2,0xc4,0xe3,0x7d,0x18,0xc0,1,0xc4,0xc1,0x7e,0x7f,0,0xc5,0xf8,0x77,0xc3};
    static const unsigned char xcr_expected[13] = {0x31,0xc9,0x0f,1,0xd0,0x48,0xc1,0xe2,0x20,0x48,9,0xd0,0xc3};
    static const unsigned char witness_expected[3] = {0x0f,0x0b,0xc3};
    __declspec(align(32)) Block blocks[3], initial[3];
    unsigned char expected[32], code_before[23], xcr_before[13], wit_before[3];
    int cp0[4] = {0}, cp1[4] = {0}, cp_count = 0, x_count = 0, probe_count = 0, witness_count = 0;
    int normal = 0, post_reached = 0, exit_code = 10, mode, i, fd;
    uint64_t xcr = 0; uintptr_t base = (uintptr_t)GetModuleHandleW(NULL);
    const IMAGE_DOS_HEADER *dos = (const IMAGE_DOS_HEADER *)base;
    const IMAGE_NT_HEADERS64 *nt = (const IMAGE_NT_HEADERS64 *)(base + (uintptr_t)(unsigned long)dos->e_lfanew);
    HANDLE h; FILE *f;
    if (argc != 3) return 10;
    mode = wcscmp(argv[1], L"capability") == 0 ? 1 : (wcscmp(argv[1], L"ud2") == 0 ? 2 : 0);
    if (!mode) return 10;
    h = CreateFileW(argv[2], GENERIC_WRITE, 0, NULL, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return 10;
    fd = _open_osfhandle((intptr_t)h, _O_WRONLY | _O_BINARY);
    if (fd == -1) { CloseHandle(h); return 10; }
    f = _fdopen(fd, "wb"); if (!f) { _close(fd); return 10; }
    for (i = 0; i < 3; ++i) {
        memset(blocks[i].pre, 0x41 + i, 32); memset(blocks[i].post, 0x51 + i, 32);
        memset(blocks[i].data, 0x61 + i, 32);
    }
    for (i = 0; i < 32; ++i) { blocks[0].data[i] = (unsigned char)(i + 1); expected[i] = (unsigned char)((i % 16) + 1); }
    memcpy(initial, blocks, sizeof(blocks));
    memcpy(code_before, &ProbeEntry, 23); memcpy(xcr_before, &Xcr0Entry, 13); memcpy(wit_before, &WitnessEntry, 3);
    if (memcmp(code_before, probe_expected, 23) || memcmp(xcr_before, xcr_expected, 13) || memcmp(wit_before, witness_expected, 3) ||
        (uintptr_t)&ProbeSite != (uintptr_t)&ProbeEntry + 8 || (uintptr_t)&ProbeReturn != (uintptr_t)&ProbeEntry + 22 || (uintptr_t)&ProbeEnd != (uintptr_t)&ProbeEntry + 23 ||
        (uintptr_t)&Xcr0Site != (uintptr_t)&Xcr0Entry + 2 || (uintptr_t)&Xcr0Return != (uintptr_t)&Xcr0Entry + 12 || (uintptr_t)&Xcr0End != (uintptr_t)&Xcr0Entry + 13 ||
        (uintptr_t)&WitnessSite != (uintptr_t)&WitnessEntry || (uintptr_t)&WitnessEnd != (uintptr_t)&WitnessEntry + 3) goto report;
    if (mode == 2) {
        __try { ++witness_count; Witness(); normal = 1; }
        __except(save_fault(GetExceptionInformation(), 3)) { }
        exit_code = fault.seen ? 30 : 31; goto report;
    }
    __cpuidex(cp0, 0, 0); ++cp_count;
    if ((unsigned)cp0[0] < 1) { exit_code = 20; goto report; }
    __cpuidex(cp1, 1, 0); ++cp_count;
    if (((unsigned)cp1[2] & 0x1c000000u) != 0x1c000000u) { exit_code = 20; goto report; }
    __try { ++x_count; xcr = ReadXcr0(); }
    __except(save_fault(GetExceptionInformation(), 1)) { }
    if (fault.seen) { exit_code = 30; goto report; }
    if ((xcr & 6) != 6) { exit_code = 20; goto report; }
    __try { ++probe_count; Probe(blocks[0].data, blocks[1].data, blocks[2].data); normal = 1; }
    __except(save_fault(GetExceptionInformation(), 2)) { }
    post_reached = normal || (fault.seen && fault.phase == 2 && fault.rip >= (uintptr_t)&ProbeEntry + 19 && fault.rip <= (uintptr_t)&ProbeEntry + 22);
    if (fault.seen) { exit_code = 30; goto report; }
    exit_code = 0;
    if (!normal || !post_reached || memcmp(blocks[0].data, initial[0].data, 32) ||
        memcmp(blocks[1].data, initial[0].data, 32) || memcmp(blocks[2].data, expected, 32)) exit_code = 31;
    for (i = 0; i < 3; ++i) if (memcmp(blocks[i].pre, initial[i].pre, 32) || memcmp(blocks[i].post, initial[i].post, 32)) exit_code = 31;
report:
    if (memcmp(code_before, &ProbeEntry, 23) || memcmp(xcr_before, &Xcr0Entry, 13) || memcmp(wit_before, &WitnessEntry, 3)) exit_code = 10;
    fprintf(f, "{\"mode\":\"%s\",\"exit\":%d,\"imageBase\":\"%llx\",\"probeRva\":%llu,\"xcrRva\":%llu,\"witnessRva\":%llu,", mode == 1 ? "capability" : "ud2", exit_code, (unsigned long long)base, (unsigned long long)((uintptr_t)&ProbeEntry-base), (unsigned long long)((uintptr_t)&Xcr0Entry-base), (unsigned long long)((uintptr_t)&WitnessEntry-base));
    fprintf(f, "\"cpuidCount\":%d,\"cpuid0\":", cp_count); regs(f, cp0); fprintf(f, ",\"cpuid1\":"); regs(f, cp1);
    fprintf(f, ",\"xgetbvCount\":%d,\"xcr0\":\"%016llx\",\"probeCount\":%d,\"witnessCount\":%d,\"normalReturnObserved\":%s,\"postSnapshotReached\":%s,", x_count, (unsigned long long)xcr, probe_count, witness_count, normal ? "true" : "false", post_reached ? "true" : "false");
    fprintf(f, "\"fault\":{\"seen\":%s,\"phase\":%d,\"code\":%lu,\"address\":\"%llx\",\"rip\":\"%llx\",\"rsp\":\"%llx\",\"rax\":\"%llx\",\"rcx\":\"%llx\",\"rdx\":\"%llx\",\"r8\":\"%llx\",\"r9\":\"%llx\",\"r10\":\"%llx\",\"r11\":\"%llx\",\"imageBase\":\"%llx\",\"rva\":", fault.seen ? "true" : "false", fault.phase, (unsigned long)fault.code, (unsigned long long)fault.address, (unsigned long long)fault.rip, (unsigned long long)fault.rsp, (unsigned long long)fault.rax, (unsigned long long)fault.rcx, (unsigned long long)fault.rdx, (unsigned long long)fault.r8, (unsigned long long)fault.r9, (unsigned long long)fault.r10, (unsigned long long)fault.r11, (unsigned long long)fault.image);
    if (fault.seen && fault.address >= base && fault.address - base < nt->OptionalHeader.SizeOfImage) fprintf(f, "%llu", (unsigned long long)(fault.address-base)); else fprintf(f, "null");
    fprintf(f, "},");
    fprintf(f, "\"blocksBefore\":\""); hex(f, initial, sizeof(initial)); fprintf(f, "\",\"blocksAfter\":\""); hex(f, blocks, sizeof(blocks));
    fprintf(f, "\",\"probeBytesBefore\":\""); hex(f, code_before, 23); fprintf(f, "\",\"probeBytesAfter\":\""); hex(f, &ProbeEntry, 23);
    fprintf(f, "\",\"xcrBytesBefore\":\""); hex(f, xcr_before, 13); fprintf(f, "\",\"xcrBytesAfter\":\""); hex(f, &Xcr0Entry, 13);
    fprintf(f, "\",\"witnessBytesBefore\":\""); hex(f, wit_before, 3); fprintf(f, "\",\"witnessBytesAfter\":\""); hex(f, &WitnessEntry, 3); fprintf(f, "\"}\n");
    if (fclose(f) != 0) return 10;
    return exit_code;
}
