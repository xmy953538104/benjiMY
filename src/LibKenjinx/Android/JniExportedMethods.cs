using LibKenjinx.Android;
using LibKenjinx.Jni.Pointers;
using Ryujinx.Audio.Backends.OpenAL;
using Ryujinx.Common;
using Ryujinx.Common.Configuration;
using Ryujinx.Common.Logging;
using Ryujinx.Common.Logging.Targets;
using Ryujinx.HLE;
using Ryujinx.HLE.HOS.SystemState;
using Ryujinx.Input;
using Silk.NET.Core.Loader;
using Silk.NET.Vulkan;
using Silk.NET.Vulkan.Extensions.KHR;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Numerics;
using System.Runtime.InteropServices;

namespace LibKenjinx
{
    public static partial class LibKenjinx
    {
        private static long _surfacePtr;
        private static long _window = 0;

        // Merkt sich die zuletzt gesetzte Renderer-Größe (für den Jiggle)
        private static int _lastRenderWidth = 0;
        private static int _lastRenderHeight = 0;

        // NEW: Rotation-Debounce + Pending-Puffer
        private static int _lastRotationDegrees = -1;
        private static int _pendingRotationDegrees = -1;

        public static VulkanLoader? VulkanLoader { get; private set; }

        [DllImport("libkenjinxjni")]
        internal extern static void setRenderingThread();

        [DllImport("libkenjinxjni")]
        internal extern static void debug_break(int code);

        [DllImport("libkenjinxjni")]
        internal extern static void setCurrentTransform(long native_window, int transform);

        public delegate IntPtr JniCreateSurface(IntPtr native_surface, IntPtr instance);

        [UnmanagedCallersOnly(EntryPoint = "javaInitialize")]
        public unsafe static bool JniInitialize(IntPtr jpathId, IntPtr jniEnv)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            PlatformInfo.IsBionic = true;

            Logger.AddTarget(
                new AsyncLogTargetWrapper(
                    new AndroidLogTarget("RyujinxLog"),
                    1000,
                    AsyncLogTargetOverflowAction.Block
                ));

            var path = Marshal.PtrToStringAnsi(jpathId);

            var init = Initialize(path);

            Interop.Initialize(new JEnvRef(jniEnv));

            Interop.Test();

            return init;
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceReloadFilesystem")]
        public static void JnaReloadFileSystem()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SwitchDevice?.ReloadFileSystem();
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceInitialize")]
        public static bool JnaDeviceInitialize(int memoryManagerMode,
                                                    bool useNce,
                                                    int memoryConfiguration,
                                                    int systemLanguage,
                                                    int regionCode,
                                                    int vSyncMode,
                                                    bool enableDockedMode,
                                                    bool enablePtc,
                                                    bool enableLowPowerPtc,
                                                    bool enableJitCacheEviction,
                                                    bool enableInternetAccess,
                                                    bool enableFsIntegrityChecks,
                                                    int fsGlobalAccessLogMode,
                                                    IntPtr timeZonePtr,
                                                    bool ignoreMissingServices)
        {
            debug_break(4);
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            AudioDriver = new OpenALHardwareDeviceDriver();

            var timezone = Marshal.PtrToStringAnsi(timeZonePtr);
            return InitializeDevice((MemoryManagerMode)memoryManagerMode,
                                    useNce,
                                    (MemoryConfiguration)memoryConfiguration,
                                    (SystemLanguage)systemLanguage,
                                    (RegionCode)regionCode,
                                    (VSyncMode)vSyncMode,
                                    enableDockedMode,
                                    enablePtc,
                                    enableLowPowerPtc,
                                    enableJitCacheEviction,
                                    enableInternetAccess,
                                    enableFsIntegrityChecks,
                                    fsGlobalAccessLogMode,
                                    timezone,
                                    ignoreMissingServices);
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceGetGameFifo")]
        public static double JnaGetGameFifo()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var stats = SwitchDevice?.EmulationContext?.Statistics.GetFifoPercent() ?? 0;

            return stats;
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceGetGameFrameTime")]
        public static double JnaGetGameFrameTime()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var stats = SwitchDevice?.EmulationContext?.Statistics.GetGameFrameTime() ?? 0;

            return stats;
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceGetGameFrameRate")]
        public static double JnaGetGameFrameRate()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var stats = SwitchDevice?.EmulationContext?.Statistics.GetGameFrameRate() ?? 0;

            return stats;
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceLaunchMiiEditor")]
        public static bool JNALaunchMiiEditApplet()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            if (SwitchDevice?.EmulationContext == null)
            {
                return false;
            }

            return LaunchMiiEditApplet();
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceGetDlcContentList")]
        public static IntPtr JniGetDlcContentListNative(IntPtr pathPtr, long titleId)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var list = GetDlcContentList(Marshal.PtrToStringAnsi(pathPtr) ?? "", (ulong)titleId);

            return CreateStringArray(list);
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceGetDlcTitleId")]
        public static long JniGetDlcTitleIdNative(IntPtr pathPtr, IntPtr ncaPath)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            return Marshal.StringToHGlobalAnsi(GetDlcTitleId(Marshal.PtrToStringAnsi(pathPtr) ?? "", Marshal.PtrToStringAnsi(ncaPath) ?? ""));
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceSignalEmulationClose")]
        public static void JniSignalEmulationCloseNative()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SignalEmulationClose();
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceCloseEmulation")]
        public static void JniCloseEmulationNative()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            CloseEmulation();
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceReinitEmulation")]
        public static void JniReinitEmulationNative()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            ReinitEmulation();
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceLoadDescriptor")]
        public static bool JnaLoadApplicationNative(int descriptor, int type, int updateDescriptor)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            if (SwitchDevice?.EmulationContext == null)
            {
                return false;
            }

            var stream = OpenFile(descriptor);
            var update = updateDescriptor == -1 ? null : OpenFile(updateDescriptor);

            return LoadApplication(stream, (FileType)type, update);
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceVerifyFirmware")]
        public static IntPtr JniVerifyFirmware(int descriptor, bool isXci)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");

            var stream = OpenFile(descriptor);

            IntPtr stringHandle = 0;
            string? version = "0.0";

            try
            {
                version = VerifyFirmware(stream, isXci)?.VersionString;
            }
            catch (Exception _)
            {
                Logger.Error?.Print(LogClass.Service, $"Unable to verify firmware. Exception: {_}");
            }

            if (version != null)
            {
                stringHandle = Marshal.StringToHGlobalAnsi(version);
            }

            return stringHandle;
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceInstallFirmware")]
        public static void JniInstallFirmware(int descriptor, bool isXci)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");

            var stream = OpenFile(descriptor);

            InstallFirmware(stream, isXci);
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceGetInstalledFirmwareVersion")]
        public static IntPtr JniGetInstalledFirmwareVersion()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");

            var version = GetInstalledFirmwareVersion() ?? "0.0";
            return Marshal.StringToHGlobalAnsi(version);
        }

        [UnmanagedCallersOnly(EntryPoint = "graphicsInitialize")]
        public static bool JnaGraphicsInitialize(float resScale,
                float maxAnisotropy,
                bool fastGpuTime,
                bool fast2DCopy,
                bool enableMacroJit,
                bool enableMacroHLE,
                bool enableShaderCache,
                bool enableTextureRecompression,
                int backendThreading)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SearchPathContainer.Platform = UnderlyingPlatform.Android;
            return InitializeGraphics(new GraphicsConfiguration()
            {
                ResScale = resScale,
                MaxAnisotropy = maxAnisotropy,
                FastGpuTime = fastGpuTime,
                Fast2DCopy = fast2DCopy,
                EnableMacroJit = enableMacroJit,
                EnableMacroHLE = enableMacroHLE,
                EnableShaderCache = enableShaderCache,
                EnableTextureRecompression = enableTextureRecompression,
                BackendThreading = (BackendThreading)backendThreading
            });
        }

        [UnmanagedCallersOnly(EntryPoint = "graphicsInitializeRenderer")]
        public unsafe static bool JnaGraphicsInitializeRenderer(char** extensionsArray,
                                                                          int extensionsLength,
                                                                          long driverHandle)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            if (Renderer != null)
            {
                return false;
            }

            List<string?> extensions = [];

            for (int i = 0; i < extensionsLength; i++)
            {
                extensions.Add(Marshal.PtrToStringAnsi((IntPtr)extensionsArray[i]));
            }

            if (driverHandle != 0)
            {
                VulkanLoader = new VulkanLoader((IntPtr)driverHandle);
            }

            CreateSurface createSurfaceFunc = instance =>
            {
                _surfacePtr = Interop.GetSurfacePtr();
                _window = Interop.GetWindowsHandle();

                var api = VulkanLoader?.GetApi() ?? Vk.GetApi();
                if (api.TryGetInstanceExtension(new Instance(instance), out KhrAndroidSurface surfaceExtension))
                {
                    var createInfo = new AndroidSurfaceCreateInfoKHR
                    {
                        SType = StructureType.AndroidSurfaceCreateInfoKhr,
                        Window = (nint*)_surfacePtr,
                    };

                    var result = surfaceExtension.CreateAndroidSurface(new Instance(instance), createInfo, null, out var surface);

                    // NEW: Falls schon vor Surface-Erstellung eine Rotation kam → jetzt anwenden
                    if (_window != 0 && _pendingRotationDegrees != -1)
                    {
                        try
                        {
                            int t = _pendingRotationDegrees switch
                            {
                                0   => 0, // IDENTITY
                                90  => 4, // ROTATE_90
                                180 => 3, // ROTATE_180 (H|V mirror)
                                270 => 7, // ROTATE_270 (ROT_90 | H|V)
                                _   => 0,
                            };
                            setCurrentTransform(_window, t);
                            Logger.Trace?.Print(LogClass.Application, $"[JNI] Apply pending SurfaceTransform {_pendingRotationDegrees}° (t={t}, window=0x{_window:x})");
                            _lastRotationDegrees = _pendingRotationDegrees;
                            _pendingRotationDegrees = -1;
                        }
                        catch (Exception ex)
                        {
                            Logger.Warning?.Print(LogClass.Application, $"Apply pending transform failed: {ex}");
                        }
                    }

                    return (nint)surface.Handle;
                }

                return IntPtr.Zero;
            };

            return InitializeGraphicsRenderer(GraphicsBackend.Vulkan, createSurfaceFunc, extensions.ToArray());
        }

        [UnmanagedCallersOnly(EntryPoint = "graphicsRendererSetSize")]
        public static void JnaSetRendererSizeNative(int width, int height)
        {
            Logger.Trace?.Print(LogClass.Application, $"graphicsRendererSetSize -> {width}x{height}");
            _lastRenderWidth  = width;
            _lastRenderHeight = height;
            Renderer?.Window?.SetSize(width, height);
        }

        [UnmanagedCallersOnly(EntryPoint = "graphicsRendererRunLoop")]
        public static void JniRunLoopNative()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SetSwapBuffersCallback(() =>
            {
                if (SwitchDevice?.EmulationContext != null)
                {
                    var time = SwitchDevice.EmulationContext.Statistics.GetGameFrameTime();
                    Interop.FrameEnded(time);
                }
            });
            RunLoop();
        }

        [UnmanagedCallersOnly(EntryPoint = "loggingSetEnabled")]
        public static void JniSetLoggingEnabledNative(int logLevel, bool enabled)
        {
            Logger.SetEnable((LogLevel)logLevel, enabled);
        }

        [UnmanagedCallersOnly(EntryPoint = "loggingEnabledGraphicsLog")]
        public static void JniSetLoggingEnabledGraphicsLog(bool enabled)
        {
            _enableGraphicsLogging = enabled;
        }

        [UnmanagedCallersOnly(EntryPoint = "deviceGetGameInfo")]
        public unsafe static void JniGetGameInfo(int fileDescriptor, IntPtr extension, IntPtr infoPtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            using var stream = OpenFile(fileDescriptor);
            var ext = Marshal.PtrToStringAnsi(extension);
            var info = GetGameInfo(stream, ext?.ToLower() ?? string.Empty) ?? GetDefaultInfo(stream);
            var i = (GameInfoNative*)infoPtr;
            var n = new GameInfoNative(info);
            i->TitleId = n.TitleId;
            i->TitleName = n.TitleName;
            i->Version = n.Version;
            i->FileSize = n.FileSize;
            i->Icon = n.Icon;
            i->Version = n.Version;
            i->Developer = n.Developer;
        }

        [UnmanagedCallersOnly(EntryPoint = "graphicsRendererSetVsync")]
        public static void JnaSetVsyncStateNative(int vSyncMode)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SetVsyncState((VSyncMode)vSyncMode);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputInitialize")]
        public static void JnaInitializeInput(int width, int height)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            InitializeInput(width, height);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputSetClientSize")]
        public static void JnaSetClientSize(int width, int height)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SetClientSize(width, height);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputSetTouchPoint")]
        public static void JnaSetTouchPoint(int x, int y)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SetTouchPoint(x, y);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputReleaseTouchPoint")]
        public static void JnaReleaseTouchPoint()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            ReleaseTouchPoint();
        }

        [UnmanagedCallersOnly(EntryPoint = "inputUpdate")]
        public static void JniUpdateInput()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            UpdateInput();
        }

        [UnmanagedCallersOnly(EntryPoint = "inputSetButtonPressed")]
        public static void JnaSetButtonPressed(int button, int id)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SetButtonPressed((GamepadButtonInputId)button, id);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputSetButtonReleased")]
        public static void JnaSetButtonReleased(int button, int id)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SetButtonReleased((GamepadButtonInputId)button, id);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputSetAccelerometerData")]
        public static void JniSetAccelerometerData(float x, float y, float z, int id)
        {
            var accel = new Vector3(x, y, z);
            SetAccelerometerData(accel, id);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputSetGyroData")]
        public static void JniSetGyroData(float x, float y, float z, int id)
        {
            var gryo = new Vector3(x, y, z);
            SetGryoData(gryo, id);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputSetStickAxis")]
        public static void JnaSetStickAxis(int stick, float x, float y, int id)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            SetStickAxis((StickInputId)stick, new Vector2(float.IsNaN(x) ? 0 : x, float.IsNaN(y) ? 0 : y), id);
        }

        [UnmanagedCallersOnly(EntryPoint = "inputConnectGamepad")]
        public static int JnaConnectGamepad(int index)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            return ConnectGamepad(index);
        }

        [UnmanagedCallersOnly(EntryPoint = "userGetOpenedUser")]
        public static IntPtr JniGetOpenedUser()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userId = GetOpenedUser();
            var ptr = Marshal.StringToHGlobalAnsi(userId);

            return ptr;
        }

        [UnmanagedCallersOnly(EntryPoint = "userGetUserPicture")]
        public static IntPtr JniGetUserPicture(IntPtr userIdPtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userId = Marshal.PtrToStringAnsi(userIdPtr) ?? "";

            return Marshal.StringToHGlobalAnsi(GetUserPicture(userId));
        }

        [UnmanagedCallersOnly(EntryPoint = "userSetUserPicture")]
        public static void JniGetUserPicture(IntPtr userIdPtr, IntPtr picturePtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userId = Marshal.PtrToStringAnsi(userIdPtr) ?? "";
            var picture = Marshal.PtrToStringAnsi(picturePtr) ?? "";

            SetUserPicture(userId, picture);
        }

        [UnmanagedCallersOnly(EntryPoint = "userGetUserName")]
        public static IntPtr JniGetUserName(IntPtr userIdPtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userId = Marshal.PtrToStringAnsi(userIdPtr) ?? "";

            return Marshal.StringToHGlobalAnsi(GetUserName(userId));
        }

        [UnmanagedCallersOnly(EntryPoint = "userSetUserName")]
        public static void JniSetUserName(IntPtr userIdPtr, IntPtr userNamePtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userId = Marshal.PtrToStringAnsi(userIdPtr) ?? "";
            var userName = Marshal.PtrToStringAnsi(userNamePtr) ?? "";

            SetUserName(userId, userName);
        }

        [UnmanagedCallersOnly(EntryPoint = "userGetAllUsers")]
        public static IntPtr JniGetAllUsers()
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var users = GetAllUsers();

            return CreateStringArray(users.ToList());
        }

        [UnmanagedCallersOnly(EntryPoint = "userAddUser")]
        public static void JniAddUser(IntPtr userNamePtr, IntPtr picturePtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userName = Marshal.PtrToStringAnsi(userNamePtr) ?? "";
            var picture = Marshal.PtrToStringAnsi(picturePtr) ?? "";

            AddUser(userName, picture);
        }

        [UnmanagedCallersOnly(EntryPoint = "userDeleteUser")]
        public static void JniDeleteUser(IntPtr userIdPtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userId = Marshal.PtrToStringAnsi(userIdPtr) ?? "";

            DeleteUser(userId);
        }

        [UnmanagedCallersOnly(EntryPoint = "uiHandlerSetup")]
        public static void JniSetupUiHandler()
        {
            SetupUiHandler();
        }

        [UnmanagedCallersOnly(EntryPoint = "uiHandlerSetResponse")]
        public static void JniSetUiHandlerResponse(bool isOkPressed, IntPtr input)
        {
            SetUiHandlerResponse(isOkPressed, Marshal.PtrToStringAnsi(input) ?? "");
        }

        [UnmanagedCallersOnly(EntryPoint = "userOpenUser")]
        public static void JniOpenUser(IntPtr userIdPtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userId = Marshal.PtrToStringAnsi(userIdPtr) ?? "";

            OpenUser(userId);
        }

        [UnmanagedCallersOnly(EntryPoint = "userCloseUser")]
        public static void JniCloseUser(IntPtr userIdPtr)
        {
            Logger.Trace?.Print(LogClass.Application, "Jni Function Call");
            var userId = Marshal.PtrToStringAnsi(userIdPtr) ?? "";

            CloseUser(userId);
        }

        // --- Window-Handle Update (Android) ---
        [UnmanagedCallersOnly(EntryPoint = "deviceSetWindowHandle")]
        public static void JniSetWindowHandle(long handle)
        {
            _window = handle;
            Logger.Trace?.Print(Ryujinx.Common.Logging.LogClass.Application,
                $"Window handle updated: 0x{handle:X}");
        }

        // --- Surface Rotation Bridge (Android) ---
        [UnmanagedCallersOnly(EntryPoint = "deviceSetSurfaceRotation")]
        public static void JniDeviceSetSurfaceRotation(int degrees)
        {
            try
            {
                // Normieren
                degrees = degrees switch { 0 => 0, 90 => 90, 180 => 180, 270 => 270, _ => 0 };

                if (degrees == _lastRotationDegrees)
                {
                    Logger.Trace?.Print(LogClass.Application, $"[JNI] SurfaceTransform unchanged ({degrees}°), skip");
                    return;
                }

                // KORREKTES Bitmask-Mapping laut NDK:
                // 0 -> 0 (IDENTITY)
                // 90 -> 4 (ROTATE_90)
                // 180 -> 3 (H|V mirror == 180°)
                // 270 -> 7 (ROTATE_270 == ROT_90 | H|V)
                int transform = degrees switch
                {
                    0   => 0,
                    90  => 4,
                    180 => 3,
                    270 => 7,
                    _   => 0
                };

                if (_window != 0)
                {
                    setCurrentTransform(_window, transform);
                    _lastRotationDegrees = degrees;
                    Logger.Trace?.Print(LogClass.Application, $"[JNI] SurfaceTransform -> {degrees}° (t={transform}, window=0x{_window:x})");
                }
                else
                {
                    _pendingRotationDegrees = degrees; // später anwenden (siehe createSurfaceFunc)
                    Logger.Warning?.Print(LogClass.Application, $"[JNI] deviceSetSurfaceRotation: _window == 0 (pending {degrees}°)");
                }
            }
            catch (Exception ex)
            {
                Logger.Warning?.Print(LogClass.Application, $"deviceSetSurfaceRotation failed: {ex}");
            }
        }

        // --- Vulkan/GL: Swapchain-/Surface-Neukonfiguration per Size-Jiggle ---
        [UnmanagedCallersOnly(EntryPoint = "deviceRecreateSwapchain")]
        public static void JniDeviceRecreateSwapchain()
        {
            try
            {
                if (Renderer?.Window == null)
                {
                    Logger.Warning?.Print(LogClass.Application, "[JNI] deviceRecreateSwapchain: Renderer.Window == null");
                    return;
                }

                int w = _lastRenderWidth;
                int h = _lastRenderHeight;

                if (w > 0 && h > 0)
                {
                    int jiggleW = w;
                    int jiggleH = h;
                    if (w <= h) jiggleW = Math.Max(1, w - 1); else jiggleH = Math.Max(1, h - 1);

                    Logger.Trace?.Print(LogClass.Application, $"[JNI] deviceRecreateSwapchain: jiggle {jiggleW}x{jiggleH} -> {w}x{h}");
                    Renderer.Window.SetSize(jiggleW, jiggleH);
                    Renderer.Window.SetSize(w, h);
                }
                else
                {
                    Logger.Trace?.Print(LogClass.Application, "[JNI] deviceRecreateSwapchain: unknown last size -> 1x1 -> 2x2 jiggle");
                    Renderer.Window.SetSize(1, 1);
                    Renderer.Window.SetSize(2, 2);
                }
            }
            catch (Exception ex)
            {
                Logger.Error?.Print(LogClass.Application, $"deviceRecreateSwapchain failed: {ex}");
            }
        }

        // ===== Amiibo JNI Exports =====
        [UnmanagedCallersOnly(EntryPoint = "amiiboLoadBin")]
        public static bool JniAmiiboLoadBin(IntPtr dataPtr, int length)
        {
            if (dataPtr == IntPtr.Zero || length <= 0) return false;
            try
            {
                byte[] buf = new byte[length];
                Marshal.Copy(dataPtr, buf, 0, length);
                return AmiiboLoadFromBytes(buf);
            }
            catch
            {
                return false;
            }
        }

        [UnmanagedCallersOnly(EntryPoint = "amiiboClear")]
        public static void JniAmiiboClear()
        {
            AmiiboClear();
        }
        // ===== End Amiibo JNI Exports =====

    }

    internal static partial class Logcat
    {
        [LibraryImport("liblog", StringMarshalling = StringMarshalling.Utf8)]
        private static partial void __android_log_print(LogLevel level, string? tag, string format, string args, IntPtr ptr);

        internal static void AndroidLogPrint(LogLevel level, string? tag, string message) =>
            __android_log_print(level, tag, "%s", message, IntPtr.Zero);

        internal enum LogLevel
        {
            Unknown = 0x00,
            Default = 0x01,
            Verbose = 0x02,
            Debug = 0x03,
            Info = 0x04,
            Warn = 0x05,
            Error = 0x06,
            Fatal = 0x07,
            Silent = 0x08,
        }
    }
}
