#nullable enable
using System;
using System.Runtime.CompilerServices;

namespace Ryujinx.HLE.HOS.Services.Nfc.Nfp
{
    /// <summary>
    /// Kleiner statischer Puffer + API, die wir aus der Android-Seite per Reflection ansprechen.
    /// </summary>
    public static class KenjinxAmiiboShim
    {
        private static byte[]? s_tag;

        [MethodImpl(MethodImplOptions.NoInlining)]
        public static bool InjectAmiibo(byte[] tagBytes)
        {
            if (tagBytes is null || tagBytes.Length == 0) return false;
            s_tag = (byte[])tagBytes.Clone();
            System.Diagnostics.Debug.WriteLine($"[Kenjinx] KenjinxAmiiboShim.InjectAmiibo bytes={tagBytes.Length}");
            return true;
        }

        [MethodImpl(MethodImplOptions.NoInlining)]
        public static void ClearAmiibo()
        {
            s_tag = null;
            System.Diagnostics.Debug.WriteLine("[Kenjinx] KenjinxAmiiboShim.ClearAmiibo");
        }

        // ▼ NEU: von INfp genutzt – holt den Tag genau einmal ab und leert den Puffer
        public static bool TryConsume(out byte[] data)
        {
            if (s_tag is null)
            {
                data = Array.Empty<byte>();
                return false;
            }
            data = s_tag;
            s_tag = null;
            return true;
        }

        // ▼ NEU: bequemer Alias
        public static void Clear() => ClearAmiibo();

        public static bool HasInjectedAmiibo => s_tag is not null;

        public static ReadOnlySpan<byte> PeekInjectedAmiibo()
            => s_tag is null ? ReadOnlySpan<byte>.Empty : new ReadOnlySpan<byte>(s_tag);
    }
}
