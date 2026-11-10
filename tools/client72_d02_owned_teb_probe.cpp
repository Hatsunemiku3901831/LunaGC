// F-03d3f4d2: owned x64 threads only; this program accepts no target or address.
// The two declaration shapes below are adapted from winsiderss/phnt, commit
// 53fbbdc5b5d2b08761db1c7b26bfa8c820924356 (ntpsapi.h / ntpebteb.h).
// MIT License
//
// Copyright (c) 2022 Winsider Seminars & Solutions, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

#include <windows.h>
#include <winternl.h>
#include <intrin.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#if !defined(_M_AMD64) || defined(_M_ARM64EC)
#error This independent probe is for native AMD64, not ARM64EC or WOW64.
#endif

// Default compiler alignment; no packing directives or invented reserved slots.
struct ProbeTBI {
    NTSTATUS ExitStatus;
    PTEB TebBaseAddress;
    CLIENT_ID ClientId;
    KAFFINITY AffinityMask;
    KPRIORITY Priority;
    KPRIORITY BasePriority;
};

struct ProbeTebPrefix {
    NT_TIB NtTib;
    PVOID EnvironmentPointer;
    CLIENT_ID ClientId;
    PVOID ActiveRpcHandle;
    PVOID ThreadLocalStoragePointer;
};

using QueryThread = decltype(&::NtQueryInformationThread);
constexpr DWORD kWaitMs = 5000;

struct ApiResult {
    bool attempted = false;
    ULONG_PTR result = 0;
    DWORD lastError = 0;
};

struct ThreadTimes {
    ApiResult api;
    FILETIME creation{}, exit{}, kernel{}, user{};
};

struct BasicQuery {
    bool attempted = false;
    NTSTATUS status = 0;
    DWORD lastErrorObserved = 0; // NTSTATUS is authoritative; this is not an NT error translation.
    ULONG returnLength = 0;
    ProbeTBI value{};
};

struct SelfObservation {
    bool attempted = false;
    DWORD pid = 0, tid = 0;
    HANDLE pseudoHandle = nullptr;
    PTEB sdkTeb = nullptr;
    PNT_TIB tibSelf = nullptr;
    ULONG_PTR gs58 = 0, sdkTeb58 = 0;
    ThreadTimes times;
    BasicQuery query;
};

struct WorkerContext {
    QueryThread query = nullptr;
    HANDLE ready = nullptr, release = nullptr;
    SelfObservation self;
    ApiResult readySignal, releaseWait;
    const char* failure = "none";
};

struct Report {
    const char* failure = "none";
    const char* stage = "start";
    bool relationsPassed = false;
    bool workerJoined = false;
    WORD nativeArchitecture = 0;
    ApiResult module, symbol, createReady, createRelease, createWorker;
    ApiResult readyWait, workerAliveWait, workerTid, workerPid;
    ApiResult releaseSignal, join, workerExit, closeWorker, closeReady, closeRelease;
    DWORD createdTid = 0;
    DWORD exitCode = 0;
    SelfObservation mainSelf;
    SelfObservation workerReadyCopy;
    ThreadTimes workerHandleTimes;
    BasicQuery mainWorkerQuery;
    WorkerContext worker;
};

bool LayoutMatches();
bool QuerySucceeded(const BasicQuery& query);
bool SelfMatches(const SelfObservation& self);
bool RelationsMatch(const Report& report);
void ObserveSelf(SelfObservation& self, QueryThread query);
void ReadTimes(HANDLE thread, ThreadTimes& times);
void QueryBasic(HANDLE thread, QueryThread query, BasicQuery& output);
DWORD WINAPI WorkerMain(void* context);
ApiResult WaitOwned(HANDLE handle, DWORD milliseconds);
ApiResult SignalOwned(HANDLE handle);
ApiResult CloseOwned(HANDLE handle);
void CleanupOwned(Report& report, HANDLE worker);
void Phase(Report& report, const char* stage);
void PrintReport(const Report& report, bool passed);

int main(int argc, char**) {
    Report report;
    HANDLE worker = nullptr;
    Phase(report, "layout");
    do {
        if (argc != 1) { report.failure = "arguments-not-supported"; break; }
        SYSTEM_INFO system{};
        GetNativeSystemInfo(&system);
        report.nativeArchitecture = system.wProcessorArchitecture;
        if (report.nativeArchitecture != PROCESSOR_ARCHITECTURE_AMD64 || !LayoutMatches()) {
            report.failure = "native-amd64-or-candidate-layout-mismatch"; break;
        }

        Phase(report, "resolve-sdk-prototype");
        SetLastError(ERROR_SUCCESS);
        HMODULE module = GetModuleHandleW(L"ntdll.dll");
        report.module = {true, reinterpret_cast<ULONG_PTR>(module), GetLastError()};
        if (!module) { report.failure = "GetModuleHandleW-failed"; break; }
        SetLastError(ERROR_SUCCESS);
        FARPROC symbol = GetProcAddress(module, "NtQueryInformationThread");
        report.symbol = {true, reinterpret_cast<ULONG_PTR>(symbol), GetLastError()};
        if (!symbol) { report.failure = "GetProcAddress-failed"; break; }
        const auto query = reinterpret_cast<QueryThread>(symbol);
        report.worker.query = query;

        Phase(report, "main-self-query");
        ObserveSelf(report.mainSelf, query);
        if (!SelfMatches(report.mainSelf)) { report.failure = "main-self-observation-failed"; break; }

        Phase(report, "owned-worker-create");
        SetLastError(ERROR_SUCCESS);
        report.worker.ready = CreateEventW(nullptr, TRUE, FALSE, nullptr);
        report.createReady = {true, reinterpret_cast<ULONG_PTR>(report.worker.ready), GetLastError()};
        if (!report.worker.ready) { report.failure = "CreateEventW-ready-failed"; break; }
        SetLastError(ERROR_SUCCESS);
        report.worker.release = CreateEventW(nullptr, TRUE, FALSE, nullptr);
        report.createRelease = {true, reinterpret_cast<ULONG_PTR>(report.worker.release), GetLastError()};
        if (!report.worker.release) { report.failure = "CreateEventW-release-failed"; break; }
        SetLastError(ERROR_SUCCESS);
        worker = CreateThread(nullptr, 0, WorkerMain, &report.worker, 0, &report.createdTid);
        report.createWorker = {true, reinterpret_cast<ULONG_PTR>(worker), GetLastError()};
        if (!worker) { report.failure = "CreateThread-failed"; break; }

        Phase(report, "owned-worker-ready");
        report.readyWait = WaitOwned(report.worker.ready, kWaitMs);
        if (report.readyWait.result != WAIT_OBJECT_0) { report.failure = "worker-ready-wait-failed"; break; }
        // Self is complete before SetEvent. Post-signal worker fields are read only after join.
        report.workerReadyCopy = report.worker.self;
        if (!SelfMatches(report.workerReadyCopy)) {
            report.failure = "worker-self-observation-failed"; break;
        }
        report.workerAliveWait = WaitOwned(worker, 0);
        if (report.workerAliveWait.result != WAIT_TIMEOUT) { report.failure = "worker-not-alive"; break; }

        Phase(report, "original-worker-handle-query");
        SetLastError(ERROR_SUCCESS);
        const DWORD handleTid = GetThreadId(worker);
        report.workerTid = {true, handleTid, GetLastError()};
        if (!handleTid) { report.failure = "GetThreadId-failed"; break; }
        SetLastError(ERROR_SUCCESS);
        const DWORD handlePid = GetProcessIdOfThread(worker);
        report.workerPid = {true, handlePid, GetLastError()};
        if (!handlePid) { report.failure = "GetProcessIdOfThread-failed"; break; }
        ReadTimes(worker, report.workerHandleTimes);
        if (!report.workerHandleTimes.api.result) { report.failure = "worker-handle-GetThreadTimes-failed"; break; }
        QueryBasic(worker, query, report.mainWorkerQuery);
        if (!QuerySucceeded(report.mainWorkerQuery)) { report.failure = "worker-handle-class0-failed"; break; }
        report.relationsPassed = RelationsMatch(report);
        if (!report.relationsPassed) { report.failure = "independent-identity-relations-failed"; break; }
    } while (false);

    Phase(report, "cleanup");
    CleanupOwned(report, worker);
    const bool passed = strcmp(report.failure, "none") == 0;
    Phase(report, "done");
    PrintReport(report, passed);
    fflush(stdout);
    return passed ? 0 : 1;
}

bool LayoutMatches() {
    return sizeof(void*) == 8 && sizeof(KAFFINITY) == 8 && sizeof(LONG) == 4 &&
        sizeof(NTSTATUS) == 4 && sizeof(KPRIORITY) == 4 && sizeof(CLIENT_ID) == 0x10 &&
        alignof(void*) == 8 && alignof(KAFFINITY) == 8 && alignof(LONG) == 4 &&
        alignof(NTSTATUS) == 4 && alignof(KPRIORITY) == 4 && alignof(CLIENT_ID) == 8 &&
        offsetof(CLIENT_ID, UniqueProcess) == 0 && offsetof(CLIENT_ID, UniqueThread) == 8 &&
        sizeof(NT_TIB) == 0x38 && alignof(NT_TIB) == 8 && offsetof(NT_TIB, Self) == 0x30 &&
        sizeof(ProbeTBI) == 0x30 && alignof(ProbeTBI) == 8 &&
        offsetof(ProbeTBI, ExitStatus) == 0 && offsetof(ProbeTBI, TebBaseAddress) == 8 &&
        offsetof(ProbeTBI, ClientId) == 0x10 && offsetof(ProbeTBI, AffinityMask) == 0x20 &&
        offsetof(ProbeTBI, Priority) == 0x28 && offsetof(ProbeTBI, BasePriority) == 0x2c &&
        sizeof(ProbeTebPrefix) == 0x60 && alignof(ProbeTebPrefix) == 8 &&
        offsetof(ProbeTebPrefix, NtTib) == 0 && offsetof(ProbeTebPrefix, EnvironmentPointer) == 0x38 &&
        offsetof(ProbeTebPrefix, ClientId) == 0x40 && offsetof(ProbeTebPrefix, ActiveRpcHandle) == 0x50 &&
        offsetof(ProbeTebPrefix, ThreadLocalStoragePointer) == 0x58;
}

void QueryBasic(HANDLE thread, QueryThread query, BasicQuery& output) {
    output.attempted = true;
    memset(&output.value, 0xcc, sizeof(output.value));
    SetLastError(ERROR_SUCCESS);
    output.status = query(thread, static_cast<THREADINFOCLASS>(0), &output.value,
        static_cast<ULONG>(sizeof(output.value)), &output.returnLength);
    output.lastErrorObserved = GetLastError();
}

void ReadTimes(HANDLE thread, ThreadTimes& times) {
    SetLastError(ERROR_SUCCESS);
    const BOOL result = GetThreadTimes(thread, &times.creation, &times.exit, &times.kernel, &times.user);
    times.api = {true, static_cast<ULONG_PTR>(result), GetLastError()};
}

void ObserveSelf(SelfObservation& self, QueryThread query) {
    self.attempted = true;
    self.pid = GetCurrentProcessId();
    self.tid = GetCurrentThreadId();
    self.pseudoHandle = GetCurrentThread();
    self.sdkTeb = NtCurrentTeb();
    // These reads originate ONLY from the SDK's current-thread TEB, never ProbeTBI.
    self.tibSelf = reinterpret_cast<volatile const NT_TIB*>(self.sdkTeb)->Self;
    self.gs58 = static_cast<ULONG_PTR>(__readgsqword(0x58));
    self.sdkTeb58 = *reinterpret_cast<volatile const ULONG_PTR*>(
        reinterpret_cast<const unsigned char*>(self.sdkTeb) +
        offsetof(ProbeTebPrefix, ThreadLocalStoragePointer));
    ReadTimes(self.pseudoHandle, self.times);
    if (self.times.api.result) QueryBasic(self.pseudoHandle, query, self.query);
}

bool QuerySucceeded(const BasicQuery& query) {
    return query.attempted && query.status == 0 && query.returnLength == 0x30;
}

bool SelfMatches(const SelfObservation& self) {
    return self.attempted && self.times.api.result && QuerySucceeded(self.query) &&
        self.pid != 0 && self.tid != 0 && self.sdkTeb != nullptr &&
        reinterpret_cast<void*>(self.tibSelf) == reinterpret_cast<void*>(self.sdkTeb) &&
        self.gs58 == self.sdkTeb58 && self.query.value.TebBaseAddress == self.sdkTeb &&
        reinterpret_cast<ULONG_PTR>(self.query.value.ClientId.UniqueProcess) == self.pid &&
        reinterpret_cast<ULONG_PTR>(self.query.value.ClientId.UniqueThread) == self.tid;
}

bool RelationsMatch(const Report& report) {
    const auto& main = report.mainSelf;
    const auto& worker = report.worker.self;
    const auto& queried = report.mainWorkerQuery.value;
    return SelfMatches(main) && SelfMatches(worker) && QuerySucceeded(report.mainWorkerQuery) &&
        main.pid == worker.pid && main.tid != worker.tid && main.sdkTeb != worker.sdkTeb &&
        worker.tid == report.createdTid && worker.tid == report.workerTid.result &&
        worker.pid == report.workerPid.result && queried.TebBaseAddress == worker.sdkTeb &&
        reinterpret_cast<ULONG_PTR>(queried.ClientId.UniqueProcess) == worker.pid &&
        reinterpret_cast<ULONG_PTR>(queried.ClientId.UniqueThread) == worker.tid &&
        report.workerHandleTimes.creation.dwLowDateTime == worker.times.creation.dwLowDateTime &&
        report.workerHandleTimes.creation.dwHighDateTime == worker.times.creation.dwHighDateTime;
}

DWORD WINAPI WorkerMain(void* context) {
    auto& worker = *static_cast<WorkerContext*>(context);
    ObserveSelf(worker.self, worker.query);
    if (!SelfMatches(worker.self)) worker.failure = "worker-self-observation-failed";
    worker.readySignal = SignalOwned(worker.ready);
    if (!worker.readySignal.result) worker.failure = "worker-ready-SetEvent-failed";
    worker.releaseWait = WaitOwned(worker.release, kWaitMs);
    if (worker.releaseWait.result != WAIT_OBJECT_0) worker.failure = "worker-release-wait-failed";
    return strcmp(worker.failure, "none") == 0 ? 0 : 1;
}

ApiResult WaitOwned(HANDLE handle, DWORD milliseconds) {
    SetLastError(ERROR_SUCCESS);
    const DWORD result = WaitForSingleObject(handle, milliseconds);
    return {true, result, GetLastError()};
}

ApiResult SignalOwned(HANDLE handle) {
    SetLastError(ERROR_SUCCESS);
    const BOOL result = SetEvent(handle);
    return {true, static_cast<ULONG_PTR>(result), GetLastError()};
}

ApiResult CloseOwned(HANDLE handle) {
    SetLastError(ERROR_SUCCESS);
    const BOOL result = CloseHandle(handle);
    return {true, static_cast<ULONG_PTR>(result), GetLastError()};
}

void CleanupOwned(Report& report, HANDLE worker) {
    bool joined = worker == nullptr;
    if (worker) {
        report.releaseSignal = SignalOwned(report.worker.release);
        report.join = WaitOwned(worker, kWaitMs);
        joined = report.join.result == WAIT_OBJECT_0;
        report.workerJoined = joined;
        if (joined) {
            SetLastError(ERROR_SUCCESS);
            const BOOL result = GetExitCodeThread(worker, &report.exitCode);
            report.workerExit = {true, static_cast<ULONG_PTR>(result), GetLastError()};
        }
        report.closeWorker = CloseOwned(worker);
        if ((!joined || !report.releaseSignal.result || !report.workerExit.result ||
             report.exitCode != 0 || !report.closeWorker.result) && strcmp(report.failure, "none") == 0)
            report.failure = "owned-worker-cleanup-failed";
    }
    // An event still waited on by an unjoined worker is left to process exit.
    if (joined && report.worker.ready) report.closeReady = CloseOwned(report.worker.ready);
    if (joined && report.worker.release) report.closeRelease = CloseOwned(report.worker.release);
    if (((report.closeReady.attempted && !report.closeReady.result) ||
         (report.closeRelease.attempted && !report.closeRelease.result)) && strcmp(report.failure, "none") == 0)
        report.failure = "owned-event-cleanup-failed";
}

void Phase(Report& report, const char* stage) {
    report.stage = stage;
    printf("{\"record\":\"phase\",\"stage\":\"%s\"}\n", stage);
    fflush(stdout);
}

const char* JsonBool(bool value) { return value ? "true" : "false"; }
unsigned long long Bits(ULONG_PTR value) { return static_cast<unsigned long long>(value); }
unsigned long long FileTimeBits(const FILETIME& time) {
    return (static_cast<unsigned long long>(time.dwHighDateTime) << 32) | time.dwLowDateTime;
}

void PrintApi(const ApiResult& api) {
    printf("{\"attempted\":%s,\"result\":\"0x%016llx\",\"lastError\":%lu}",
        JsonBool(api.attempted), Bits(api.result), api.lastError);
}

void PrintTimes(const ThreadTimes& times) {
    printf("{\"api\":"); PrintApi(times.api);
    printf(",\"creation\":\"0x%016llx\",\"exit\":\"0x%016llx\",\"kernel\":\"0x%016llx\",\"user\":\"0x%016llx\"}",
        FileTimeBits(times.creation), FileTimeBits(times.exit), FileTimeBits(times.kernel), FileTimeBits(times.user));
}

void PrintQuery(const BasicQuery& query) {
    printf("{\"attempted\":%s,\"informationClass\":0,\"bufferLength\":%zu,\"returnLength\":%lu,"
        "\"status\":%ld,\"statusHex\":\"0x%08lx\",\"lastErrorObserved\":%lu,\"rawHex\":\"",
        JsonBool(query.attempted), sizeof(ProbeTBI), query.returnLength,
        query.status, static_cast<ULONG>(query.status), query.lastErrorObserved);
    const auto* raw = reinterpret_cast<const unsigned char*>(&query.value);
    for (size_t index = 0; index < sizeof(query.value); ++index) printf("%02x", static_cast<unsigned>(raw[index]));
    printf("\",\"exitStatus\":%ld,\"teb\":\"0x%016llx\",\"clientPid\":\"0x%016llx\","
        "\"clientTid\":\"0x%016llx\",\"affinityMask\":\"0x%016llx\",\"priority\":%ld,\"basePriority\":%ld}",
        query.value.ExitStatus, Bits(reinterpret_cast<ULONG_PTR>(query.value.TebBaseAddress)),
        Bits(reinterpret_cast<ULONG_PTR>(query.value.ClientId.UniqueProcess)),
        Bits(reinterpret_cast<ULONG_PTR>(query.value.ClientId.UniqueThread)),
        Bits(query.value.AffinityMask), query.value.Priority, query.value.BasePriority);
}

void PrintSelf(const SelfObservation& self) {
    printf("{\"attempted\":%s,\"pid\":%lu,\"tid\":%lu,\"pseudoHandle\":\"0x%016llx\","
        "\"sdkNtCurrentTeb\":\"0x%016llx\",\"ntTibSelf\":\"0x%016llx\","
        "\"gs58\":\"0x%016llx\",\"sdkTeb58\":\"0x%016llx\",\"times\":",
        JsonBool(self.attempted), self.pid, self.tid, Bits(reinterpret_cast<ULONG_PTR>(self.pseudoHandle)),
        Bits(reinterpret_cast<ULONG_PTR>(self.sdkTeb)), Bits(reinterpret_cast<ULONG_PTR>(self.tibSelf)),
        Bits(self.gs58), Bits(self.sdkTeb58));
    PrintTimes(self.times); printf(",\"query\":"); PrintQuery(self.query); printf("}");
}

void PrintReport(const Report& report, bool passed) {
    const auto& workerSelf = report.workerJoined ? report.worker.self : report.workerReadyCopy;
    const ApiResult unavailable;
    const unsigned queryCount = static_cast<unsigned>(report.mainSelf.query.attempted) +
        static_cast<unsigned>(workerSelf.query.attempted) + static_cast<unsigned>(report.mainWorkerQuery.attempted);
    printf("{\"record\":\"result\",\"schema\":\"owned-teb-abi-v1\",\"passed\":%s,\"failure\":\"%s\","
        "\"stage\":\"%s\",\"nativeArchitecture\":%u,\"gsBaseRegisterReadDirectly\":false,"
        "\"liveApproved\":false,\"replayApproved\":false,\"capturedWriteApproved\":false,"
        "\"R1Only13_340Approved\":false,\"pool15_82Approved\":false,"
        "\"naturalAvatarAllocatorThreadObserved\":false,\"queryCount\":%u,\"queryCountExact\":%s,\"workerJoined\":%s,\"relationsPassed\":%s,"
        "\"waitLimitMs\":%lu,\"layout\":{",
        JsonBool(passed), report.failure, report.stage, static_cast<unsigned>(report.nativeArchitecture),
        queryCount, JsonBool(!report.createWorker.result || report.workerJoined ||
            (report.readyWait.attempted && report.readyWait.result == WAIT_OBJECT_0)),
        JsonBool(report.workerJoined), JsonBool(report.relationsPassed), kWaitMs);
    printf("\"pointerSize\":%zu,\"kaffinitySize\":%zu,\"longSize\":%zu,\"ntstatusSize\":%zu,"
        "\"kprioritySize\":%zu,\"clientIdSize\":%zu,\"clientIdAlign\":%zu,\"ntTibSize\":%zu,\"ntTibAlign\":%zu,"
        "\"pointerAlign\":%zu,\"kaffinityAlign\":%zu,\"longAlign\":%zu,\"ntstatusAlign\":%zu,\"kpriorityAlign\":%zu,"
        "\"clientIdProcessOffset\":%zu,\"clientIdThreadOffset\":%zu,"
        "\"ntTibSelfOffset\":%zu,\"tbiSize\":%zu,\"tbiAlign\":%zu,\"exitStatusOffset\":%zu,"
        "\"tebOffset\":%zu,\"clientIdOffset\":%zu,\"affinityOffset\":%zu,\"priorityOffset\":%zu,"
        "\"basePriorityOffset\":%zu,\"tebPrefixSize\":%zu,\"tebPrefixAlign\":%zu,\"ntTibOffset\":%zu,"
        "\"environmentPointerOffset\":%zu,\"tebClientIdOffset\":%zu,\"activeRpcHandleOffset\":%zu,\"tlsPointerOffset\":%zu},",
        sizeof(void*), sizeof(KAFFINITY), sizeof(LONG), sizeof(NTSTATUS), sizeof(KPRIORITY),
        sizeof(CLIENT_ID), alignof(CLIENT_ID), sizeof(NT_TIB), alignof(NT_TIB),
        alignof(void*), alignof(KAFFINITY), alignof(LONG), alignof(NTSTATUS), alignof(KPRIORITY),
        offsetof(CLIENT_ID, UniqueProcess), offsetof(CLIENT_ID, UniqueThread), offsetof(NT_TIB, Self),
        sizeof(ProbeTBI), alignof(ProbeTBI), offsetof(ProbeTBI, ExitStatus), offsetof(ProbeTBI, TebBaseAddress),
        offsetof(ProbeTBI, ClientId), offsetof(ProbeTBI, AffinityMask), offsetof(ProbeTBI, Priority),
        offsetof(ProbeTBI, BasePriority), sizeof(ProbeTebPrefix), alignof(ProbeTebPrefix),
        offsetof(ProbeTebPrefix, NtTib), offsetof(ProbeTebPrefix, EnvironmentPointer),
        offsetof(ProbeTebPrefix, ClientId), offsetof(ProbeTebPrefix, ActiveRpcHandle), offsetof(ProbeTebPrefix, ThreadLocalStoragePointer));
    printf("\"mainSelf\":"); PrintSelf(report.mainSelf);
    printf(",\"workerSelf\":"); PrintSelf(workerSelf);
    printf(",\"mainWorkerQuery\":"); PrintQuery(report.mainWorkerQuery);
    printf(",\"workerHandleTimes\":"); PrintTimes(report.workerHandleTimes);
    printf(",\"createdWorkerTid\":%lu,\"workerExitCode\":%lu,\"workerFailure\":\"%s\",\"api\":{",
        report.createdTid, report.exitCode, report.workerJoined ? report.worker.failure :
            (report.createWorker.result ? "unavailable-unjoined" : "none"));
    const ApiResult* values[] = {&report.module, &report.symbol, &report.createReady, &report.createRelease,
        &report.createWorker, &report.readyWait, &report.workerAliveWait, &report.workerTid, &report.workerPid,
        report.workerJoined ? &report.worker.readySignal : &unavailable,
        report.workerJoined ? &report.worker.releaseWait : &unavailable, &report.releaseSignal, &report.join,
        &report.workerExit, &report.closeWorker, &report.closeReady, &report.closeRelease};
    const char* names[] = {"GetModuleHandleW", "GetProcAddress", "CreateEventW-ready", "CreateEventW-release",
        "CreateThread", "readyWait", "workerAliveWait", "GetThreadId", "GetProcessIdOfThread",
        "workerReadySignal", "workerReleaseWait", "releaseSignal", "join", "GetExitCodeThread",
        "CloseHandle-worker", "CloseHandle-ready", "CloseHandle-release"};
    for (size_t index = 0; index < sizeof(values) / sizeof(values[0]); ++index) {
        if (index) printf(",");
        printf("\"%s\":", names[index]); PrintApi(*values[index]);
    }
    printf("}}\n");
}
