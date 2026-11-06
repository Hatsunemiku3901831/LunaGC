// Synthetic Windows debug target. No game/library loading or network access.
#include <windows.h>
#include <shellapi.h>
#include <cstdint>
#include <cstdio>

struct FixtureState {
    std::uint32_t magic;
    std::uint32_t phase;
    std::uint32_t avCaught;
    std::uint32_t cppCaught;
    std::uint32_t markerCalls;
    std::uint32_t ticks;
};

extern "C" __declspec(dllexport) volatile FixtureState g_fixture_state = {0x72324442, 0, 0, 0, 0, 0};
static HANDLE gate;
static HANDLE report;
static void write_progress(std::uint32_t phase);
static bool wait_gate(std::uint32_t phase);
extern "C" __declspec(dllexport) __declspec(noinline) void fixture_av();
extern "C" __declspec(dllexport) __declspec(noinline) void fixture_cpp();
extern "C" __declspec(dllexport) __declspec(noinline) void channel_marker(std::uint32_t value);

int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    int argc = 0;
    auto argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (!argv || argc != 4) return 80;
    gate = OpenEventW(SYNCHRONIZE | EVENT_MODIFY_STATE, FALSE, argv[1]);
    report = CreateFileW(argv[2], GENERIC_WRITE, FILE_SHARE_READ, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (!gate || report == INVALID_HANDLE_VALUE) return 81;
    write_progress(10);
    if (lstrcmpW(argv[3], L"no-exceptions") != 0) {
        fixture_av();
        write_progress(11);
        fixture_cpp();
        write_progress(12);
    }
    if (!wait_gate(20)) return 82;
    channel_marker(1);
    if (!wait_gate(30)) return 83;
    channel_marker(2);
    write_progress(40);
    CloseHandle(report);
    CloseHandle(gate);
    LocalFree(argv);
    return 0;
}

static void write_progress(std::uint32_t phase) {
    g_fixture_state.phase = phase;
    char line[256];
    auto length = sprintf_s(line, "{\"phase\":%u,\"avCaught\":%u,\"cppCaught\":%u,\"markerCalls\":%u,\"ticks\":%u}\n",
        phase, g_fixture_state.avCaught, g_fixture_state.cppCaught, g_fixture_state.markerCalls, g_fixture_state.ticks);
    DWORD written = 0;
    WriteFile(report, line, static_cast<DWORD>(length), &written, nullptr);
    FlushFileBuffers(report);
}

static bool wait_gate(std::uint32_t phase) {
    write_progress(phase);
    for (unsigned i = 0; i < 4000; ++i) {
        ++g_fixture_state.ticks;
        auto status = WaitForSingleObject(gate, 10);
        if (status == WAIT_OBJECT_0) {
            ResetEvent(gate);
            return true;
        }
        if (status != WAIT_TIMEOUT) return false;
    }
    return false;
}

extern "C" __declspec(dllexport) __declspec(noinline) void fixture_av() {
    __try {
        *reinterpret_cast<volatile std::uint32_t*>(1) = 0x72;
    } __except (GetExceptionCode() == EXCEPTION_ACCESS_VIOLATION ? EXCEPTION_EXECUTE_HANDLER : EXCEPTION_CONTINUE_SEARCH) {
        ++g_fixture_state.avCaught;
    }
}

extern "C" __declspec(dllexport) __declspec(noinline) void fixture_cpp() {
    try {
        throw 72;
    } catch (int value) {
        if (value == 72) ++g_fixture_state.cppCaught;
    }
}

extern "C" __declspec(dllexport) __declspec(noinline) void channel_marker(std::uint32_t value) {
    g_fixture_state.markerCalls += value;
}
