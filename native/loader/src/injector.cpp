#include "loader.h"

#include <shlwapi.h>

#include <sstream>

namespace loader {

namespace {
    std::wstring format_error(const wchar_t* where, DWORD err) {
        wchar_t msg[512] = {0};
        FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                       nullptr, err, 0, msg, 512, nullptr);
        std::wstringstream ss;
        ss << where << L" failed (code " << err << L"): " << msg;
        return ss.str();
    }
}

std::wstring inject(DWORD pid, const std::wstring& dll_path) {
    if (!PathFileExistsW(dll_path.c_str())) {
        return L"DLL not found: " + dll_path;
    }

    HANDLE process = OpenProcess(
        PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION |
        PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ,
        FALSE, pid);
    if (!process) return format_error(L"OpenProcess", GetLastError());

    SIZE_T payload_bytes = (dll_path.size() + 1) * sizeof(wchar_t);
    LPVOID remote = VirtualAllocEx(process, nullptr, payload_bytes,
                                    MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remote) {
        DWORD err = GetLastError();
        CloseHandle(process);
        return format_error(L"VirtualAllocEx", err);
    }

    SIZE_T written = 0;
    if (!WriteProcessMemory(process, remote, dll_path.c_str(), payload_bytes, &written)
        || written != payload_bytes) {
        DWORD err = GetLastError();
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return format_error(L"WriteProcessMemory", err);
    }

    HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");
    if (!kernel32) {
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return L"kernel32.dll handle missing in loader process";
    }
    LPTHREAD_START_ROUTINE loadLib =
        (LPTHREAD_START_ROUTINE)GetProcAddress(kernel32, "LoadLibraryW");
    if (!loadLib) {
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return L"LoadLibraryW not exported by kernel32";
    }

    HANDLE remote_thread = CreateRemoteThread(process, nullptr, 0, loadLib,
                                                remote, 0, nullptr);
    if (!remote_thread) {
        DWORD err = GetLastError();
        VirtualFreeEx(process, remote, 0, MEM_RELEASE);
        CloseHandle(process);
        return format_error(L"CreateRemoteThread", err);
    }

    WaitForSingleObject(remote_thread, 10000);

    DWORD exit_code = 0;
    GetExitCodeThread(remote_thread, &exit_code);

    CloseHandle(remote_thread);
    VirtualFreeEx(process, remote, 0, MEM_RELEASE);
    CloseHandle(process);

    if (exit_code == 0) {
        return L"LoadLibraryW remote returned NULL (DLL failed to load - "
               L"check %TEMP%\\openzen.log)";
    }
    return L"";
}

} // namespace loader
