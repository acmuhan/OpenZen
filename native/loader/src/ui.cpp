#include "loader.h"

#include <commctrl.h>
#include <commdlg.h>

#include <string>
#include <vector>

#pragma comment(lib, "comctl32.lib")

namespace loader {

namespace {

constexpr wchar_t WND_CLASS[] = L"OpenZenLoaderWnd";

enum {
    ID_LIST = 1001,
    ID_REFRESH,
    ID_INJECT,
    ID_BROWSE,
    ID_DLLPATH,
    ID_LOG,
};

HWND g_list = nullptr;
HWND g_refresh = nullptr;
HWND g_inject = nullptr;
HWND g_browse = nullptr;
HWND g_dll_edit = nullptr;
HWND g_log = nullptr;
std::vector<JavaProcess> g_processes;

void append_log(const std::wstring& line) {
    int len = GetWindowTextLengthW(g_log);
    SendMessageW(g_log, EM_SETSEL, len, len);
    std::wstring text = line + L"\r\n";
    SendMessageW(g_log, EM_REPLACESEL, FALSE, (LPARAM)text.c_str());
}

std::wstring get_dll_path() {
    int len = GetWindowTextLengthW(g_dll_edit);
    std::wstring s(len, L'\0');
    if (len > 0) GetWindowTextW(g_dll_edit, s.data(), len + 1);
    return s;
}

void refresh_list() {
    ListView_DeleteAllItems(g_list);
    g_processes = list_java_processes();
    for (size_t i = 0; i < g_processes.size(); ++i) {
        const auto& jp = g_processes[i];

        LVITEMW item{};
        item.mask = LVIF_TEXT | LVIF_PARAM;
        item.iItem = (int)i;
        item.iSubItem = 0;
        wchar_t pid_text[32];
        std::swprintf(pid_text, 32, L"%lu", jp.pid);
        item.pszText = pid_text;
        item.lParam = (LPARAM)i;
        int row = ListView_InsertItem(g_list, &item);

        ListView_SetItemText(g_list, row, 1,
            const_cast<LPWSTR>(jp.image_name.c_str()));
        ListView_SetItemText(g_list, row, 2,
            const_cast<LPWSTR>(jp.window_title.c_str()));
        ListView_SetItemText(g_list, row, 3,
            const_cast<LPWSTR>(jp.command_line.c_str()));
    }
    wchar_t msg[64];
    std::swprintf(msg, 64, L"Refreshed: %zu Java process(es)", g_processes.size());
    append_log(msg);
}

void do_inject() {
    int sel = ListView_GetNextItem(g_list, -1, LVNI_SELECTED);
    if (sel < 0) {
        append_log(L"No process selected. Pick a row and try again.");
        return;
    }
    LVITEMW item{};
    item.mask = LVIF_PARAM;
    item.iItem = sel;
    ListView_GetItem(g_list, &item);
    size_t idx = (size_t)item.lParam;
    if (idx >= g_processes.size()) {
        append_log(L"Stale selection. Refresh and retry.");
        return;
    }
    const auto& jp = g_processes[idx];

    std::wstring dll = get_dll_path();
    wchar_t msg[512];
    std::swprintf(msg, 512, L"Injecting %ls into pid %lu (%ls)",
                  dll.c_str(), jp.pid, jp.image_name.c_str());
    append_log(msg);

    std::wstring err = inject(jp.pid, dll);
    if (err.empty()) {
        append_log(L"Injection ok. Watch %TEMP%\\openzen.log for Java side.");
    } else {
        append_log(L"Injection failed: " + err);
    }
}

void do_browse() {
    wchar_t buf[MAX_PATH] = {0};
    std::wstring current = get_dll_path();
    if (!current.empty() && current.size() < MAX_PATH) {
        wcscpy_s(buf, MAX_PATH, current.c_str());
    }

    OPENFILENAMEW ofn{};
    ofn.lStructSize = sizeof ofn;
    ofn.hwndOwner = GetParent(g_browse);
    ofn.lpstrFilter = L"DLL Files\0*.dll\0All Files\0*.*\0";
    ofn.lpstrFile = buf;
    ofn.nMaxFile = MAX_PATH;
    ofn.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST;

    if (GetOpenFileNameW(&ofn)) {
        SetWindowTextW(g_dll_edit, buf);
    }
}

void layout(HWND hwnd) {
    RECT rc; GetClientRect(hwnd, &rc);
    int W = rc.right - rc.left;
    int H = rc.bottom - rc.top;

    int pad = 8;
    int btn_h = 24;
    int row_h = 26;

    // Top row: DLL path edit + Browse
    MoveWindow(g_dll_edit, pad, pad, W - pad*3 - 80, btn_h, TRUE);
    MoveWindow(g_browse, W - pad - 80, pad, 80, btn_h, TRUE);

    // Button row
    int by = pad + row_h;
    MoveWindow(g_refresh, pad, by, 100, btn_h, TRUE);
    MoveWindow(g_inject, pad + 110, by, 100, btn_h, TRUE);

    // List view
    int list_y = by + row_h;
    int list_h = H * 60 / 100 - list_y;
    if (list_h < 100) list_h = 100;
    MoveWindow(g_list, pad, list_y, W - pad*2, list_h, TRUE);

    // Log area
    int log_y = list_y + list_h + pad;
    int log_h = H - log_y - pad;
    if (log_h < 60) log_h = 60;
    MoveWindow(g_log, pad, log_y, W - pad*2, log_h, TRUE);
}

LRESULT CALLBACK wnd_proc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        HINSTANCE hi = ((LPCREATESTRUCT)lp)->hInstance;

        g_dll_edit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
            WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL,
            0, 0, 0, 0, hwnd, (HMENU)ID_DLLPATH, hi, nullptr);
        // Extract the embedded OpenZen.dll to %TEMP%\OpenZenLoader and pre-fill
        // it as the default DLL path. Users can still override via Browse.
        std::wstring embedded = extract_embedded_dll();
        if (!embedded.empty()) {
            SetWindowTextW(g_dll_edit, embedded.c_str());
        }

        g_browse = CreateWindowW(L"BUTTON", L"Browse...",
            WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            0, 0, 0, 0, hwnd, (HMENU)ID_BROWSE, hi, nullptr);

        g_refresh = CreateWindowW(L"BUTTON", L"Refresh",
            WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            0, 0, 0, 0, hwnd, (HMENU)ID_REFRESH, hi, nullptr);
        g_inject = CreateWindowW(L"BUTTON", L"Inject",
            WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            0, 0, 0, 0, hwnd, (HMENU)ID_INJECT, hi, nullptr);

        g_list = CreateWindowExW(WS_EX_CLIENTEDGE, WC_LISTVIEWW, L"",
            WS_CHILD | WS_VISIBLE | LVS_REPORT | LVS_SINGLESEL,
            0, 0, 0, 0, hwnd, (HMENU)ID_LIST, hi, nullptr);
        ListView_SetExtendedListViewStyle(g_list,
            LVS_EX_FULLROWSELECT | LVS_EX_GRIDLINES);

        LVCOLUMNW col{};
        col.mask = LVCF_TEXT | LVCF_WIDTH;
        col.cx = 70;  col.pszText = const_cast<LPWSTR>(L"PID");
        ListView_InsertColumn(g_list, 0, &col);
        col.cx = 100; col.pszText = const_cast<LPWSTR>(L"Image");
        ListView_InsertColumn(g_list, 1, &col);
        col.cx = 320; col.pszText = const_cast<LPWSTR>(L"Window Title");
        ListView_InsertColumn(g_list, 2, &col);
        col.cx = 300; col.pszText = const_cast<LPWSTR>(L"Path");
        ListView_InsertColumn(g_list, 3, &col);

        g_log = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
            WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_MULTILINE
            | ES_AUTOVSCROLL | ES_READONLY,
            0, 0, 0, 0, hwnd, (HMENU)ID_LOG, hi, nullptr);

        layout(hwnd);
        refresh_list();
        append_log(L"OpenZen Loader ready. Select a javaw.exe and press Inject.");
        return 0;
    }
    case WM_SIZE: layout(hwnd); return 0;
    case WM_COMMAND:
        switch (LOWORD(wp)) {
        case ID_REFRESH: refresh_list(); return 0;
        case ID_INJECT:  do_inject(); return 0;
        case ID_BROWSE:  do_browse(); return 0;
        }
        break;
    case WM_DESTROY: PostQuitMessage(0); return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

} // namespace

int run_ui(HINSTANCE hInstance) {
    INITCOMMONCONTROLSEX icc{};
    icc.dwSize = sizeof icc;
    icc.dwICC = ICC_LISTVIEW_CLASSES | ICC_STANDARD_CLASSES;
    InitCommonControlsEx(&icc);

    WNDCLASSEXW wc{};
    wc.cbSize = sizeof wc;
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = wnd_proc;
    wc.hInstance = hInstance;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    wc.lpszClassName = WND_CLASS;
    RegisterClassExW(&wc);

    HWND hwnd = CreateWindowExW(0, WND_CLASS, L"OpenZen Loader",
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT, 820, 560,
        nullptr, nullptr, hInstance, nullptr);
    if (!hwnd) return 1;

    ShowWindow(hwnd, SW_SHOW);
    UpdateWindow(hwnd);

    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    return (int)msg.wParam;
}

} // namespace loader
