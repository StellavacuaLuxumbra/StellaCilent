// Stella Client - GUI Control Panel (no console).
// Monitors IPC, shows logs, start/stop injection.
// Lua brain: C++ computes, Java executes.

#include "protocol.h"
#include "ipc/ring_buffer.hpp"
#include "ipc/shared_memory.hpp"
#include "brain/snapshot.hpp"
#include "brain/lua_host.hpp"
#include "instruction.hpp"

#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <commctrl.h>
#include <tlhelp32.h>
#include <tchar.h>

#pragma comment(lib, "comctl32.lib")
#pragma comment(linker,"\"/manifestdependency:type='win32' name='Microsoft.Windows.Common-Controls' version='6.0.0.0' processorArchitecture='*' publicKeyToken='6595b64144ccf1df' language='*'\"")

namespace {

// ---- Window ----
constexpr const wchar_t* kWndClass = L"StellaClientPanel";
constexpr int kWndW = 640;
constexpr int kWndH = 480;

// ---- Controls ----
HWND hLog = nullptr;
HWND hBtnStart = nullptr;
HWND hBtnStop = nullptr;
HWND hStatus = nullptr;
HWND hProcCombo = nullptr;
HFONT hFont = nullptr;

// ---- IPC state ----
std::atomic<bool> g_running{true};
std::atomic<bool> g_injected{false};
stella::SharedFile g_shm;
stella::Ring g_tx{};
stella::Ring g_rx{};
HANDLE hReader = nullptr;
HANDLE hTickThread = nullptr;

// ---- Lua brain ----
stella::LuaHost g_lua;
stella::WorldSnapshot g_lastSnap;

// ---- log ----
std::vector<std::string> g_logLines;
constexpr int kMaxLogLines = 500;

void AppendLog(const char* msg) {
    g_logLines.push_back(msg);
    if ((int)g_logLines.size() > kMaxLogLines) {
        g_logLines.erase(g_logLines.begin());
    }
    // update edit
    if (hLog) {
        std::string all;
        for (auto& l : g_logLines) { all += l; all += "\r\n"; }
        SetWindowTextA(hLog, all.c_str());
        // scroll to bottom
        SendMessageA(hLog, EM_SETSEL, (WPARAM)all.size(), (LPARAM)all.size());
        SendMessageA(hLog, EM_SCROLLCARET, 0, 0);
    }
}

void SetStatus(const char* text) {
    if (hStatus) SetWindowTextA(hStatus, text);
}

// ---- helpers ----
std::wstring ExeDir() {
    wchar_t buf[MAX_PATH]{};
    DWORD n = ::GetModuleFileNameW(nullptr, buf, MAX_PATH);
    std::wstring path(buf, n);
    auto pos = path.find_last_of(L'\\');
    return (pos != std::wstring::npos) ? path.substr(0, pos + 1) : L"";
}

std::wstring IpcFilePath() {
    wchar_t temp[MAX_PATH]{};
    DWORD n = ::GetTempPathW(MAX_PATH, temp);
    return std::wstring(temp, n) + L"stella_ipc.bin";
}

uint32_t ReadU32(const uint8_t* p, int off) {
    return static_cast<uint32_t>(p[off]) | (static_cast<uint32_t>(p[off+1]) << 8) |
           (static_cast<uint32_t>(p[off+2]) << 16) | (static_cast<uint32_t>(p[off+3]) << 24);
}
int32_t ReadI32(const uint8_t* p, int off) { return static_cast<int32_t>(ReadU32(p, off)); }
float ReadF32(const uint8_t* p, int off) {
    uint32_t bits = ReadU32(p, off); float v; std::memcpy(&v, &bits, 4); return v;
}

void WriteU32(uint8_t* p, int off, uint32_t v) {
    p[off]=v; p[off+1]=v>>8; p[off+2]=v>>16; p[off+3]=v>>24;
}
void WriteI32(uint8_t* p, int off, int32_t v) { WriteU32(p, off, static_cast<uint32_t>(v)); }
void WriteF32(uint8_t* p, int off, float v) { uint32_t bits; std::memcpy(&bits, &v, 4); WriteU32(p, off, bits); }

// ---- process enumeration ----
struct ProcInfo { DWORD pid; std::wstring name; };
std::vector<ProcInfo> g_processes;

void RefreshProcessList() {
    g_processes.clear();
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return;

    PROCESSENTRY32W pe{};
    pe.dwSize = sizeof(pe);
    if (Process32FirstW(snap, &pe)) {
        do {
            std::wstring name(pe.szExeFile);
            std::transform(name.begin(), name.end(), name.begin(), ::towlower);
            if (name.find(L"javaw.exe") != std::wstring::npos ||
                name.find(L"java.exe") != std::wstring::npos) {
                g_processes.push_back({pe.th32ProcessID, std::wstring(pe.szExeFile)});
            }
        } while (Process32NextW(snap, &pe));
    }
    CloseHandle(snap);

    // update combo
    SendMessageW(hProcCombo, CB_RESETCONTENT, 0, 0);
    for (size_t i = 0; i < g_processes.size(); i++) {
        wchar_t buf[256];
        wsprintfW(buf, L"PID %lu - %s", g_processes[i].pid, g_processes[i].name.c_str());
        SendMessageW(hProcCombo, CB_ADDSTRING, 0, (LPARAM)buf);
    }
    if (!g_processes.empty()) {
        SendMessageW(hProcCombo, CB_SETCURSEL, 0, 0);
    }
}

// ---- reader thread ----
DWORD WINAPI ReaderThread(LPVOID) {
    std::vector<uint8_t> payload;
    while (g_running.load() && g_injected.load()) {
        uint32_t opcode = 0;
        bool didWork = false;
        while (g_rx.read(opcode, payload)) {
            didWork = true;
            char buf[256];
            switch (opcode) {
                case stella::kEvConnected:
                    AppendLog("[IPC] Connected to Java driver");
                    SetStatus("Connected");
                    break;
                case stella::kEvPong:
                    snprintf(buf, sizeof(buf), "[IPC] Pong: %.*s", (int)payload.size(), payload.data());
                    AppendLog(buf);
                    break;
                case stella::kEvTick:
                    break; // suppress tick spam
                case stella::kEvWorldSnapshot: {
                    // Deserialize world snapshot from Java
                    g_lastSnap = stella::SnapshotDeserializer::deserialize(
                        payload.data(), (uint32_t)payload.size());
                    if (g_lastSnap.valid) {
                        // Run Lua brain: feed snapshot, get instruction sequence
                        auto instructions = g_lua.tick(g_lastSnap);
                        if (!instructions.empty()) {
                            g_tx.write(stella::kCmdInstructionSeq,
                                       instructions.data(), (uint32_t)instructions.size());
                        }
                    }
                    break;
                }
                case stella::kEvPlayerPos:
                    if (payload.size() >= 33) {
                        double px = 0, py = 0, pz = 0;
                        uint64_t lo = ReadU32(payload.data(), 0);
                        uint64_t hi = ReadU32(payload.data(), 4);
                        px = 0; std::memcpy(&px, &lo, 4); // simplified
                        snprintf(buf, sizeof(buf), "[POS] player pos received (%d bytes)", (int)payload.size());
                        AppendLog(buf);
                    }
                    break;
                case stella::kEvEntityList: {
                    int count = payload.empty() ? 0 : payload[0];
                    snprintf(buf, sizeof(buf), "[ENTITIES] %d entities nearby", count);
                    AppendLog(buf);
                    break;
                }
                case stella::kEvAttack:
                    if (payload.size() >= 4) {
                        snprintf(buf, sizeof(buf), "[COMBAT] Attack entity %d", ReadI32(payload.data(), 0));
                        AppendLog(buf);
                    }
                    break;
                case stella::kEvModuleToggle:
                    if (payload.size() >= 2) {
                        int id = payload[0];
                        bool on = payload[1] != 0;
                        // Sync Lua module state by module ID
                        if (id == 32) { // CrystalAura
                            g_lua.setModuleEnabled("CrystalAura", on);
                            AppendLog(on ? "[LUA] CrystalAura ENABLED" : "[LUA] CrystalAura DISABLED");
                        } else if (id == 33) { // SelfTrap
                            g_lua.setModuleEnabled("SelfTrap", on);
                            AppendLog(on ? "[LUA] SelfTrap ENABLED" : "[LUA] SelfTrap DISABLED");
                        } else if (id == 34) { // Surround
                            g_lua.setModuleEnabled("Surround", on);
                            AppendLog(on ? "[LUA] Surround ENABLED" : "[LUA] Surround DISABLED");
                        } else if (id == 1) { // KillAura
                            g_lua.setModuleEnabled("KillAura", on);
                            AppendLog(on ? "[LUA] KillAura ENABLED" : "[LUA] KillAura DISABLED");
                        } else if (id == 25) { // HoleESP
                            g_lua.setModuleEnabled("HoleESP", on);
                            AppendLog(on ? "[LUA] HoleESP ENABLED" : "[LUA] HoleESP DISABLED");
                        } else if (id == 3) { // Speed
                            g_lua.setModuleEnabled("Speed", on);
                            AppendLog(on ? "[LUA] Speed ENABLED" : "[LUA] Speed DISABLED");
                        } else if (id == 4) { // Fly
                            g_lua.setModuleEnabled("Fly", on);
                            AppendLog(on ? "[LUA] Fly ENABLED" : "[LUA] Fly DISABLED");
                        } else {
                            snprintf(buf, sizeof(buf), "[MODULE] id=%d %s", id, on ? "ON" : "OFF");
                            AppendLog(buf);
                        }
                    }
                    break;
                default:
                    snprintf(buf, sizeof(buf), "[EVENT] opcode=%u bytes=%d", opcode, (int)payload.size());
                    AppendLog(buf);
                    break;
            }
        }
        if (!didWork) SwitchToThread();
    }
    return 0;
}

// ---- injection ----
bool DoInject() {
    if (g_injected.load()) {
        AppendLog("[!] Already injected");
        return true;
    }

    AppendLog("[*] Opening shared memory...");
    const std::wstring path = IpcFilePath();

    // Retry up to 10 times, 500ms apart — wait for Java to create the file
    bool opened = false;
    for (int attempt = 1; attempt <= 10; attempt++) {
        if (g_shm.open(path, stella::kFileSize)) {
            opened = true;
            break;
        }
        char rbuf[128];
        snprintf(rbuf, sizeof(rbuf), "[*] Attempt %d/10 — waiting for IPC file...", attempt);
        AppendLog(rbuf);
        Sleep(500);
    }
    if (!opened) {
        AppendLog("[ERROR] Cannot open IPC file after 10 retries.");
        AppendLog("[ERROR] Make sure Minecraft is running with Stella mod first.");
        SetStatus("IPC failed");
        return false;
    }

    uint8_t* base = g_shm.data();
    const uint32_t magic = ReadU32(base, stella::kHMagic);
    const uint32_t version = ReadU32(base, stella::kHVersion);
    const uint32_t dataCap = ReadU32(base, stella::kHDataCap);
    if (magic != stella::kMagic || version != stella::kVersion || dataCap != stella::kDataCap) {
        AppendLog("[ERROR] Protocol mismatch - restart Minecraft with mod");
        SetStatus("Protocol mismatch");
        return false;
    }

    g_tx = stella::Ring::at(base + stella::kOffNativeToJava, stella::kDataCap);
    g_rx = stella::Ring::at(base + stella::kOffJavaToNative, stella::kDataCap);

    // reset
    std::memset(base + stella::kOffNativeToJava, 0, stella::kRegionSize);
    uint32_t dop; std::vector<uint8_t> dbuf;
    while (g_rx.read(dop, dbuf)) {}

    g_injected.store(true);
    SetStatus("Injecting...");

    // handshake
    uint8_t empty[1] = {0};
    g_tx.write(stella::kOpConnect, std::string(""));

    // init Lua brain
    AppendLog("[*] Initializing Lua brain...");
    char lbuf[256];
    std::wstring exeDir = ExeDir();
    std::string scriptDir(exeDir.begin(), exeDir.end());
    scriptDir += "scripts\\";

    // Load CrystalAura
    if (g_lua.loadScript("CrystalAura", scriptDir + "crystal_aura.lua")) {
        g_lua.setModuleEnabled("CrystalAura", true);
        AppendLog("[OK] Loaded CrystalAura.lua");
    } else {
        snprintf(lbuf, sizeof(lbuf), "[WARN] Failed to load CrystalAura: %s", g_lua.lastError().c_str());
        AppendLog(lbuf);
    }

    // Load Surround
    if (g_lua.loadScript("Surround", scriptDir + "surround.lua")) {
        g_lua.setModuleEnabled("Surround", true);
        AppendLog("[OK] Loaded Surround.lua");
    } else {
        snprintf(lbuf, sizeof(lbuf), "[WARN] Failed to load Surround: %s", g_lua.lastError().c_str());
        AppendLog(lbuf);
    }

    // Load SelfTrap
    if (g_lua.loadScript("SelfTrap", scriptDir + "selftrap.lua")) {
        g_lua.setModuleEnabled("SelfTrap", true);
        AppendLog("[OK] Loaded SelfTrap.lua");
    } else {
        snprintf(lbuf, sizeof(lbuf), "[WARN] Failed to load SelfTrap: %s", g_lua.lastError().c_str());
        AppendLog(lbuf);
    }

    // Load KillAura
    if (g_lua.loadScript("KillAura", scriptDir + "kill_aura.lua")) {
        g_lua.setModuleEnabled("KillAura", true);
        AppendLog("[OK] Loaded KillAura.lua");
    } else {
        snprintf(lbuf, sizeof(lbuf), "[WARN] Failed to load KillAura: %s", g_lua.lastError().c_str());
        AppendLog(lbuf);
    }

    // Load HoleESP
    if (g_lua.loadScript("HoleESP", scriptDir + "hole_esp.lua")) {
        g_lua.setModuleEnabled("HoleESP", true);
        AppendLog("[OK] Loaded HoleESP.lua");
    } else {
        snprintf(lbuf, sizeof(lbuf), "[WARN] Failed to load HoleESP: %s", g_lua.lastError().c_str());
        AppendLog(lbuf);
    }

    // Load Speed
    if (g_lua.loadScript("Speed", scriptDir + "speed.lua")) {
        g_lua.setModuleEnabled("Speed", true);
        AppendLog("[OK] Loaded Speed.lua");
    } else {
        snprintf(lbuf, sizeof(lbuf), "[WARN] Failed to load Speed: %s", g_lua.lastError().c_str());
        AppendLog(lbuf);
    }

    // Load Fly
    if (g_lua.loadScript("Fly", scriptDir + "fly.lua")) {
        g_lua.setModuleEnabled("Fly", true);
        AppendLog("[OK] Loaded Fly.lua");
    } else {
        snprintf(lbuf, sizeof(lbuf), "[WARN] Failed to load Fly: %s", g_lua.lastError().c_str());
        AppendLog(lbuf);
    }

    // start reader
    hReader = CreateThread(nullptr, 0, ReaderThread, nullptr, 0, nullptr);

    AppendLog("[OK] Injection successful");
    SetStatus("Injected - Connected");

    EnableWindow(hBtnStart, FALSE);
    EnableWindow(hBtnStop, TRUE);

    return true;
}

void DoStop() {
    if (!g_injected.load()) {
        AppendLog("[!] Not injected");
        return;
    }

    AppendLog("[*] Stopping injection...");
    g_injected.store(false);

    if (hReader) {
        WaitForSingleObject(hReader, 2000);
        CloseHandle(hReader);
        hReader = nullptr;
    }

    g_shm.close();
    SetStatus("Stopped");
    AppendLog("[OK] Injection stopped");

    EnableWindow(hBtnStart, TRUE);
    EnableWindow(hBtnStop, FALSE);
}

// ---- WNDPROC ----
LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_CREATE: {
        // font
        hFont = CreateFontW(-14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
            DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
            CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Consolas");

        // status label
        hStatus = CreateWindowW(L"STATIC", L"Idle", WS_CHILD | WS_VISIBLE | SS_LEFT,
            10, 10, 600, 20, hWnd, nullptr, nullptr, nullptr);
        SendMessageW(hStatus, WM_SETFONT, (WPARAM)hFont, TRUE);

        // process combo
        hProcCombo = CreateWindowW(L"COMBOBOX", nullptr,
            WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL,
            10, 35, 400, 200, hWnd, nullptr, nullptr, nullptr);
        SendMessageW(hProcCombo, WM_SETFONT, (WPARAM)hFont, TRUE);

        // refresh button
        HWND hBtnRefresh = CreateWindowW(L"BUTTON", L"Refresh",
            WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            420, 35, 80, 25, hWnd, (HMENU)3, nullptr, nullptr);
        SendMessageW(hBtnRefresh, WM_SETFONT, (WPARAM)hFont, TRUE);

        // start button
        hBtnStart = CreateWindowW(L"BUTTON", L"Start Injection",
            WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            10, 65, 130, 30, hWnd, (HMENU)1, nullptr, nullptr);
        SendMessageW(hBtnStart, WM_SETFONT, (WPARAM)hFont, TRUE);

        // stop button
        hBtnStop = CreateWindowW(L"BUTTON", L"Stop Injection",
            WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON | WS_DISABLED,
            150, 65, 130, 30, hWnd, (HMENU)2, nullptr, nullptr);
        SendMessageW(hBtnStop, WM_SETFONT, (WPARAM)hFont, TRUE);

        // log area
        hLog = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", nullptr,
            WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_LEFT | ES_MULTILINE | ES_AUTOVSCROLL | ES_READONLY,
            10, 105, 600, 340, hWnd, nullptr, nullptr, nullptr);
        SendMessageW(hLog, WM_SETFONT, (WPARAM)hFont, TRUE);
        SendMessageW(hLog, EM_SETLIMITTEXT, 1024 * 1024, 0); // 1MB buffer

        // initial process list
        RefreshProcessList();

        AppendLog("=== Stella Client Control Panel ===");
        AppendLog("1. Start Minecraft with Stella mod");
        AppendLog("2. Click 'Refresh' to detect Java processes");
        AppendLog("3. Select your Minecraft process");
        AppendLog("4. Click 'Start Injection'");
        break;
    }

    case WM_COMMAND:
        switch (LOWORD(wParam)) {
        case 1: // Start
            DoInject();
            break;
        case 2: // Stop
            DoStop();
            break;
        case 3: // Refresh
            RefreshProcessList();
            AppendLog("[*] Process list refreshed");
            break;
        }
        break;

    case WM_SIZE: {
        // resize log to fill window
        if (hLog) {
            RECT rc;
            GetClientRect(hWnd, &rc);
            MoveWindow(hLog, 10, 105, rc.right - 20, rc.bottom - 115, TRUE);
        }
        break;
    }

    case WM_CLOSE:
        DoStop();
        DestroyWindow(hWnd);
        break;

    case WM_DESTROY:
        if (hFont) DeleteObject(hFont);
        PostQuitMessage(0);
        break;

    default:
        return DefWindowProcW(hWnd, msg, wParam, lParam);
    }
    return 0;
}

} // namespace

int WINAPI WinMain(HINSTANCE hInst, HINSTANCE, LPSTR, int nCmdShow) {
    INITCOMMONCONTROLSEX icc = {sizeof(icc), 0};
    InitCommonControlsEx(&icc);

    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    wc.lpszClassName = kWndClass;
    RegisterClassExW(&wc);

    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    int x = (screenW - kWndW) / 2;
    int y = (screenH - kWndH) / 2;

    HWND hWnd = CreateWindowExW(0, kWndClass, L"Stella Client - Control Panel",
        WS_OVERLAPPEDWINDOW & ~(WS_THICKFRAME | WS_MAXIMIZEBOX),
        x, y, kWndW, kWndH,
        nullptr, nullptr, hInst, nullptr);

    ShowWindow(hWnd, nCmdShow);
    UpdateWindow(hWnd);

    MSG msg{};
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    g_running.store(false);
    DoStop();
    return (int)msg.wParam;
}
