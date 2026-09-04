package com.example.service

enum class DisplayServerType(val label: String) {
    WAYLAND("Wayland"),
    X11("X11"),
    AUTO_DETECT("Auto-Detect")
}

enum class DesktopEnvType(val label: String) {
    KDE_PLASMA("KDE Plasma 6"),
    GNOME("GNOME"),
    WLROOTS("wlroots (Sway/Hyprland)"),
    XFCE("XFCE / Lightweight"),
    AUTO_DETECT("Auto-Detect")
}

data class EnvironmentDetectionResult(
    val displayServer: DisplayServerType,
    val desktopEnv: DesktopEnvType,
    val remoteDesktopPackage: String,
    val remoteProtocol: String,
    val defaultPort: Int,
    val companionPackages: List<String>,
    val activationCommands: String
)

object HostProvisioningScript {

    /**
     * Resolves the required packages and service commands based on detected or target environment.
     */
    fun resolveEnvironmentStack(
        displayServer: DisplayServerType,
        desktopEnv: DesktopEnvType
    ): EnvironmentDetectionResult {
        val effectiveDisplay = if (displayServer == DisplayServerType.AUTO_DETECT) DisplayServerType.WAYLAND else displayServer
        val effectiveEnv = if (desktopEnv == DesktopEnvType.AUTO_DETECT) DesktopEnvType.KDE_PLASMA else desktopEnv

        return when (effectiveDisplay) {
            DisplayServerType.WAYLAND -> {
                when (effectiveEnv) {
                    DesktopEnvType.KDE_PLASMA -> EnvironmentDetectionResult(
                        displayServer = DisplayServerType.WAYLAND,
                        desktopEnv = DesktopEnvType.KDE_PLASMA,
                        remoteDesktopPackage = "krdp",
                        remoteProtocol = "RDP (Native Wayland / KPipeWire)",
                        defaultPort = 3389,
                        companionPackages = listOf("krdp", "krfb", "pipewire-pulse", "wl-clipboard", "cockpit", "cockpit-bridge", "openssh-server", "curl"),
                        activationCommands = "systemctl --user enable --now krdp.service 2>/dev/null || true"
                    )
                    DesktopEnvType.GNOME -> EnvironmentDetectionResult(
                        displayServer = DisplayServerType.WAYLAND,
                        desktopEnv = DesktopEnvType.GNOME,
                        remoteDesktopPackage = "gnome-remote-desktop",
                        remoteProtocol = "RDP (GNOME Mutter Portal)",
                        defaultPort = 3389,
                        companionPackages = listOf("gnome-remote-desktop", "pipewire-pulse", "wl-clipboard", "cockpit", "cockpit-bridge", "openssh-server", "curl"),
                        activationCommands = "grdctl rdp enable && systemctl --user enable --now gnome-remote-desktop.service"
                    )
                    DesktopEnvType.WLROOTS -> EnvironmentDetectionResult(
                        displayServer = DisplayServerType.WAYLAND,
                        desktopEnv = DesktopEnvType.WLROOTS,
                        remoteDesktopPackage = "wayvnc",
                        remoteProtocol = "VNC (zwlr_screencopy_v1)",
                        defaultPort = 5900,
                        companionPackages = listOf("wayvnc", "grim", "slurp", "pipewire-pulse", "wl-clipboard", "cockpit", "cockpit-bridge", "openssh-server", "curl"),
                        activationCommands = "wayvnc 0.0.0.0 5900 &"
                    )
                    else -> EnvironmentDetectionResult(
                        displayServer = DisplayServerType.WAYLAND,
                        desktopEnv = DesktopEnvType.KDE_PLASMA,
                        remoteDesktopPackage = "krdp",
                        remoteProtocol = "RDP (Wayland Portal)",
                        defaultPort = 3389,
                        companionPackages = listOf("krdp", "pipewire-pulse", "wl-clipboard", "cockpit", "openssh-server"),
                        activationCommands = "systemctl --user enable --now krdp.service 2>/dev/null || true"
                    )
                }
            }
            DisplayServerType.X11 -> {
                EnvironmentDetectionResult(
                    displayServer = DisplayServerType.X11,
                    desktopEnv = effectiveEnv,
                    remoteDesktopPackage = "x11vnc",
                    remoteProtocol = "VNC (X11 Framebuffer)",
                    defaultPort = 5900,
                    companionPackages = listOf("x11vnc", "xclip", "xdotool", "pipewire-pulse", "cockpit", "cockpit-bridge", "openssh-server", "curl"),
                    activationCommands = "x11vnc -display :0 -forever -shared -rfbport 5900 -bg"
                )
            }
            else -> resolveEnvironmentStack(DisplayServerType.WAYLAND, DesktopEnvType.KDE_PLASMA)
        }
    }

    /**
     * Generates a fully autonomous, production-grade bash provisioning script
     * that detects X11 vs Wayland and KDE vs GNOME and installs dependencies.
     */
    fun generateStandaloneBashScript(
        targetUser: String = "hostmanager",
        customSshPort: Int = 22,
        injectPublicKey: String = ""
    ): String {
        return """
#!/usr/bin/env bash
# ==============================================================================
# Host Remote Desktop & Environment Auto-Provisioning Script
# Target: Auto-detects Display Server (Wayland vs X11) & Desktop Env (KDE vs GNOME)
# Automatically configures matched Remote Desktop (krdp / gnome-remote-desktop / x11vnc)
# ==============================================================================

set -euo pipefail

# ANSI Color Palette
CYAN='\033[1;36m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
RED='\033[1;31m'
NC='\033[0m'

log_info() { echo -e "${'$'}{CYAN}[INFO]${'$'}{NC} $*"; }
log_ok()   { echo -e "${'$'}{GREEN}[OK]${'$'}{NC} $*"; }
log_warn() { echo -e "${'$'}{YELLOW}[WARN]${'$'}{NC} $*"; }
log_err()  { echo -e "${'$'}{RED}[ERROR]${'$'}{NC} $*"; }

echo -e "${'$'}{CYAN}================================================================${'$'}{NC}"
echo -e "${'$'}{CYAN}   HOST MANAGER: REMOTE WORKSTATION PROVISIONING ENGINE         ${'$'}{NC}"
echo -e "${'$'}{CYAN}================================================================${'$'}{NC}"

# 1. ROOT CHECK & PERMISSIONS
if [ "${'$'}(id -u)" -ne 0 ]; then
    log_err "This script requires root privileges to install packages and configure sudoers."
    log_err "Please run via: sudo bash ${'$'}0"
    exit 1
fi

# 2. DETECT PACKAGE MANAGER
PKG_MANAGER=""
if command -v apt-get >/dev/null 2>&1; then
    PKG_MANAGER="apt"
elif command -v dnf >/dev/null 2>&1; then
    PKG_MANAGER="dnf"
elif command -v pacman >/dev/null 2>&1; then
    PKG_MANAGER="pacman"
else
    log_err "Unsupported package manager. Please install dependencies manually."
    exit 1
fi
log_ok "Package manager detected: ${'$'}{PKG_MANAGER}"

# 3. CONFIGURE DEDICATED HOSTMANAGER USER & SUDOERS
TARGET_USER="$targetUser"
if ! id "${'$'}{TARGET_USER}" >/dev/null 2>&1; then
    log_info "Creating dedicated management user '${'$'}{TARGET_USER}'..."
    useradd -m -s /bin/bash "${'$'}{TARGET_USER}"
    usermod -aG sudo,adm,audio,video "${'$'}{TARGET_USER}" 2>/dev/null || usermod -aG wheel "${'$'}{TARGET_USER}" 2>/dev/null || true
    log_ok "User '${'$'}{TARGET_USER}' created."
else
    log_ok "User '${'$'}{TARGET_USER}' already exists."
fi

# Passwordless Sudoers Drop-in
echo "${'$'}{TARGET_USER} ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/${'$'}{TARGET_USER}"
chmod 0440 "/etc/sudoers.d/${'$'}{TARGET_USER}"
log_ok "Configured passwordless sudo rules in /etc/sudoers.d/${'$'}{TARGET_USER}"

# 4. INJECT AUTHORIZED_KEYS (IF PROVIDED)
USER_HOME="${'$'}(eval echo ~${'$'}{TARGET_USER})"
mkdir -p "${'$'}{USER_HOME}/.ssh"
chmod 0700 "${'$'}{USER_HOME}/.ssh"

${if (injectPublicKey.isNotBlank()) """
if ! grep -q "$injectPublicKey" "${'$'}{USER_HOME}/.ssh/authorized_keys" 2>/dev/null; then
    echo "$injectPublicKey" >> "${'$'}{USER_HOME}/.ssh/authorized_keys"
    log_ok "Injected Android client public key into ${'$'}{USER_HOME}/.ssh/authorized_keys"
fi
""" else """
# Public key injection placeholder
touch "${'$'}{USER_HOME}/.ssh/authorized_keys"
"""}
chmod 0600 "${'$'}{USER_HOME}/.ssh/authorized_keys"
chown -R "${'$'}{TARGET_USER}:${'$'}{TARGET_USER}" "${'$'}{USER_HOME}/.ssh"

# 5. DISPLAY SERVER DETECTION (WAYLAND VS X11)
log_info "Detecting display server protocol..."
DISPLAY_SERVER="unknown"

# Probe session type via loginctl or session env
SESSION_TYPE=""
if [ -n "${'$'}{XDG_SESSION_TYPE:-}" ]; then
    SESSION_TYPE="${'$'}{XDG_SESSION_TYPE}"
elif command -v loginctl >/dev/null 2>&1; then
    ACTIVE_SESSION="${'$'}(loginctl list-sessions --no-legend 2>/dev/null | awk '/seat0/ {print ${'$'}1}' | head -n 1 || true)"
    if [ -n "${'$'}{ACTIVE_SESSION}" ]; then
        SESSION_TYPE="${'$'}(loginctl show-session "${'$'}{ACTIVE_SESSION}" -p Type --value 2>/dev/null || true)"
    fi
fi

# Deep inspection of running compositor daemons
if [ "${'$'}{SESSION_TYPE}" = "wayland" ] || \
   pgrep -x "kwin_wayland" >/dev/null 2>&1 || \
   pgrep -f "gnome-shell --wayland" >/dev/null 2>&1 || \
   pgrep -x "sway" >/dev/null 2>&1 || \
   pgrep -x "Hyprland" >/dev/null 2>&1 || \
   [ -n "${'$'}{WAYLAND_DISPLAY:-}" ] || \
   find /run/user/ -name "wayland-*" 2>/dev/null | grep -q "wayland"; then
    DISPLAY_SERVER="Wayland"
elif [ "${'$'}{SESSION_TYPE}" = "x11" ] || \
     pgrep -x "Xorg" >/dev/null 2>&1 || \
     pgrep -x "X" >/dev/null 2>&1 || \
     [ -n "${'$'}{DISPLAY:-}" ]; then
    DISPLAY_SERVER="X11"
else
    # Modern Ubuntu / Kubuntu 24.04+ defaults to Wayland
    log_warn "No active display session currently attached. Assuming modern Wayland environment."
    DISPLAY_SERVER="Wayland"
fi
log_ok "Display Server Identified: ${'$'}{CYAN}${'$'}{DISPLAY_SERVER}${'$'}{NC}"

# 6. DESKTOP ENVIRONMENT DETECTION (KDE VS GNOME VS WLROOTS)
log_info "Detecting desktop environment..."
DESKTOP_ENV="unknown"
RAW_DESKTOP="${'$'}{XDG_CURRENT_DESKTOP:-${'$'}{DESKTOP_SESSION:-}}"

if echo "${'$'}{RAW_DESKTOP}" | grep -iq "kde" || \
   pgrep -x "kwin_wayland" >/dev/null 2>&1 || \
   pgrep -x "plasmashell" >/dev/null 2>&1 || \
   pgrep -x "kwin_x11" >/dev/null 2>&1 || \
   command -v plasmashell >/dev/null 2>&1; then
    DESKTOP_ENV="KDE Plasma"
elif echo "${'$'}{RAW_DESKTOP}" | grep -iq "gnome" || \
     pgrep -x "gnome-shell" >/dev/null 2>&1 || \
     command -v gnome-shell >/dev/null 2>&1; then
    DESKTOP_ENV="GNOME"
elif echo "${'$'}{RAW_DESKTOP}" | grep -iqE "sway|hyprland|wlroots" || \
     pgrep -x "sway" >/dev/null 2>&1 || \
     pgrep -x "Hyprland" >/dev/null 2>&1; then
    DESKTOP_ENV="wlroots"
elif echo "${'$'}{RAW_DESKTOP}" | grep -iqE "xfce|xubuntu" || \
     pgrep -x "xfwm4" >/dev/null 2>&1; then
    DESKTOP_ENV="XFCE"
else
    DESKTOP_ENV="Generic"
fi
log_ok "Desktop Environment Identified: ${'$'}{CYAN}${'$'}{DESKTOP_ENV}${'$'}{NC}"

# 7. DEPENDENCY MATRIX SELECTION
COMMON_PKGS="openssh-server cockpit cockpit-bridge pipewire-pulse curl socat"
SELECTED_REMOTE_PKG=""
REMOTE_PORT=""

if [ "${'$'}{DISPLAY_SERVER}" = "Wayland" ]; then
    COMMON_PKGS="${'$'}{COMMON_PKGS} wl-clipboard"
    case "${'$'}{DESKTOP_ENV}" in
        "KDE Plasma")
            log_info "Selecting KDE Plasma 6 native Wayland stack (krdp + KPipeWire portal)..."
            SELECTED_REMOTE_PKG="krdp krfb"
            REMOTE_PORT="3389 (RDP)"
            ;;
        "GNOME")
            log_info "Selecting GNOME native Wayland stack (gnome-remote-desktop via RDP)..."
            SELECTED_REMOTE_PKG="gnome-remote-desktop"
            REMOTE_PORT="3389 (RDP)"
            ;;
        "wlroots")
            log_info "Selecting wlroots screencopy stack (wayvnc)..."
            SELECTED_REMOTE_PKG="wayvnc grim slurp"
            REMOTE_PORT="5900 (VNC)"
            ;;
        *)
            log_info "Selecting universal Wayland RDP server (krdp)..."
            SELECTED_REMOTE_PKG="krdp"
            REMOTE_PORT="3389 (RDP)"
            ;;
    esac
else
    # X11 Display Server
    log_info "Selecting X11 frame buffer stack (x11vnc + xclip + xdotool)..."
    COMMON_PKGS="${'$'}{COMMON_PKGS} xclip xdotool"
    SELECTED_REMOTE_PKG="x11vnc"
    REMOTE_PORT="5900 (VNC)"
fi

ALL_PACKAGES="${'$'}{COMMON_PKGS} ${'$'}{SELECTED_REMOTE_PKG}"
log_info "Installing targeted packages: ${'$'}{ALL_PACKAGES}"

# 8. EXECUTE INSTALLATION
case "${'$'}{PKG_MANAGER}" in
    "apt")
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -qq
        apt-get install -y --no-install-recommends ${'$'}{ALL_PACKAGES}
        ;;
    "dnf")
        dnf install -y ${'$'}{ALL_PACKAGES}
        ;;
    "pacman")
        pacman -Sy --noconfirm --needed ${'$'}{ALL_PACKAGES}
        ;;
esac
log_ok "Packages installed successfully."

# 9. CONFIGURE SERVICES & SOCKETS
log_info "Configuring daemons and audio TCP relay..."
systemctl enable --now ssh || systemctl enable --now sshd || true
systemctl enable --now cockpit.socket || true

# Audio TCP Module for remote relay (PipeWire / Pulse)
if command -v pactl >/dev/null 2>&1; then
    pactl load-module module-native-protocol-tcp port=4713 auth-anonymous=1 2>/dev/null || true
fi

# Enable desktop daemon if applicable
if [ "${'$'}{SELECTED_REMOTE_PKG}" = "krdp" ] || echo "${'$'}{SELECTED_REMOTE_PKG}" | grep -q "krdp"; then
    log_ok "KDE Remote Desktop (krdp) daemon is ready. Port: 3389"
    systemctl --user -M "${'$'}{TARGET_USER}@" enable --now krdp.service 2>/dev/null || true
elif [ "${'$'}{SELECTED_REMOTE_PKG}" = "gnome-remote-desktop" ]; then
    log_ok "Enabling gnome-remote-desktop on RDP port 3389..."
    su - "${'$'}{TARGET_USER}" -c "grdctl rdp enable" 2>/dev/null || true
    systemctl --user -M "${'$'}{TARGET_USER}@" enable --now gnome-remote-desktop.service 2>/dev/null || true
fi

echo ""
echo -e "${'$'}{GREEN}================================================================${'$'}{NC}"
echo -e "${'$'}{GREEN} [SUCCESS] HOST PROVISIONING & ENVIRONMENT SETUP COMPLETE!      ${'$'}{NC}"
echo -e "${'$'}{GREEN}================================================================${'$'}{NC}"
echo -e "  • User Account:     ${'$'}{CYAN}${'$'}{TARGET_USER}${'$'}{NC} (NOPASSWD sudo)"
echo -e "  • Display Server:   ${'$'}{CYAN}${'$'}{DISPLAY_SERVER}${'$'}{NC}"
echo -e "  • Desktop Env:      ${'$'}{CYAN}${'$'}{DESKTOP_ENV}${'$'}{NC}"
echo -e "  • Remote Desktop:   ${'$'}{CYAN}${'$'}{SELECTED_REMOTE_PKG}${'$'}{NC} on ${'$'}{GREEN}${'$'}{REMOTE_PORT}${'$'}{NC}"
echo -e "  • Web Management:   ${'$'}{CYAN}Cockpit${'$'}{NC} on ${'$'}{GREEN}https://<host>:9090${'$'}{NC}"
echo -e "  • Audio Stream:     ${'$'}{CYAN}PipeWire Pulse TCP${'$'}{NC} on ${'$'}{GREEN}port 4713${'$'}{NC}"
echo -e "${'$'}{GREEN}================================================================${'$'}{NC}"
""".trimIndent()
    }
}
