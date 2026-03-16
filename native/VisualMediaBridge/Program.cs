using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Media.Control;
using Windows.Storage.Streams;

internal static class Program
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented = false,
    };

    public static async Task<int> Main(string[] args)
    {
        try
        {
            if (args.Length == 0 || string.Equals(args[0], "query", StringComparison.OrdinalIgnoreCase))
            {
                var sessions = await QuerySessionsAsync().ConfigureAwait(false);
                await Console.Out.WriteAsync(JsonSerializer.Serialize(sessions, JsonOptions)).ConfigureAwait(false);
                return 0;
            }

            if (string.Equals(args[0], "control", StringComparison.OrdinalIgnoreCase))
            {
                var sourceQuery = GetArg(args, "--source");
                var action = GetArg(args, "--action");
                var ok = await ControlAsync(sourceQuery, action).ConfigureAwait(false);
                await Console.Out.WriteAsync(JsonSerializer.Serialize(new { ok }, JsonOptions)).ConfigureAwait(false);
                return ok ? 0 : 2;
            }

            await Console.Error.WriteLineAsync("Unknown command. Use: query | control --source <query> --action <previous|toggle|next>").ConfigureAwait(false);
            return 64;
        }
        catch (Exception ex)
        {
            await Console.Error.WriteLineAsync(ex.ToString()).ConfigureAwait(false);
            return 17;
        }
    }

    private static string? GetArg(string[] args, string key)
    {
        for (var i = 0; i < args.Length - 1; i++)
        {
            if (string.Equals(args[i], key, StringComparison.OrdinalIgnoreCase))
            {
                return args[i + 1];
            }
        }

        return null;
    }

    private static async Task<List<SessionDto>> QuerySessionsAsync()
    {
        var manager = await GlobalSystemMediaTransportControlsSessionManager
            .RequestAsync()
            .AsTask()
            .ConfigureAwait(false);

        var sessions = new List<SessionDto>();
        foreach (var session in manager.GetSessions())
        {
            var source = session.SourceAppUserModelId ?? string.Empty;
            try
            {
                var media = await session.TryGetMediaPropertiesAsync().AsTask().ConfigureAwait(false);
                var playback = session.GetPlaybackInfo();
                var timeline = session.GetTimelineProperties();

                sessions.Add(new SessionDto
                {
                    Source = source,
                    Title = media?.Title ?? string.Empty,
                    Artist = media?.Artist ?? string.Empty,
                    Album = media?.AlbumTitle ?? string.Empty,
                    Subtitle = media?.Subtitle ?? string.Empty,
                    Status = playback.PlaybackStatus.ToString(),
                    Position = (float)timeline.Position.TotalSeconds,
                    Duration = (float)timeline.EndTime.TotalSeconds,
                    ArtworkPath = await TryExtractArtworkPathAsync(media?.Thumbnail).ConfigureAwait(false),
                });
            }
            catch (Exception ex)
            {
                sessions.Add(new SessionDto
                {
                    Source = source,
                    Title = string.Empty,
                    Artist = string.Empty,
                    Album = string.Empty,
                    Subtitle = string.Empty,
                    Status = "Error",
                    Position = 0f,
                    Duration = 0f,
                    ArtworkPath = null,
                    Error = ex.GetType().Name + ": " + ex.Message,
                });
            }
        }

        return sessions;
    }

    private static async Task<bool> ControlAsync(string? sourceQuery, string? action)
    {
        if (string.IsNullOrWhiteSpace(sourceQuery) || string.IsNullOrWhiteSpace(action))
        {
            return false;
        }

        var manager = await GlobalSystemMediaTransportControlsSessionManager
            .RequestAsync()
            .AsTask()
            .ConfigureAwait(false);

        var session = manager.GetSessions().FirstOrDefault(candidate =>
        {
            var source = candidate.SourceAppUserModelId;
            return !string.IsNullOrWhiteSpace(source) &&
                   source.Contains(sourceQuery, StringComparison.OrdinalIgnoreCase);
        });

        if (session is null)
        {
            return false;
        }

        return action.ToLowerInvariant() switch
        {
            "previous" => await session.TrySkipPreviousAsync().AsTask().ConfigureAwait(false),
            "toggle" => await session.TryTogglePlayPauseAsync().AsTask().ConfigureAwait(false),
            "next" => await session.TrySkipNextAsync().AsTask().ConfigureAwait(false),
            _ => false,
        };
    }

    private static async Task<string?> TryExtractArtworkPathAsync(IRandomAccessStreamReference? thumbnail)
    {
        if (thumbnail is null) return null;

        try
        {
            using var stream = await thumbnail.OpenReadAsync().AsTask().ConfigureAwait(false);
            using var netStream = stream.AsStreamForRead();
            if (!netStream.CanRead) return null;

            using var memory = new MemoryStream();
            await netStream.CopyToAsync(memory).ConfigureAwait(false);
            var bytes = memory.ToArray();
            if (bytes.Length == 0) return null;

            var hash = Convert.ToHexString(SHA1.HashData(bytes));
            var artworkDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "VisualClient",
                "artwork"
            );
            Directory.CreateDirectory(artworkDir);

            var path = Path.Combine(artworkDir, hash + ".img");
            if (!File.Exists(path))
            {
                await File.WriteAllBytesAsync(path, bytes).ConfigureAwait(false);
            }

            return path;
        }
        catch
        {
            return null;
        }
    }

    private sealed class SessionDto
    {
        public string Source { get; init; } = string.Empty;
        public string Title { get; init; } = string.Empty;
        public string Artist { get; init; } = string.Empty;
        public string Album { get; init; } = string.Empty;
        public string Subtitle { get; init; } = string.Empty;
        public string Status { get; init; } = string.Empty;
        public float Position { get; init; }
        public float Duration { get; init; }
        public string? ArtworkPath { get; init; }
        public string? Error { get; init; }
    }
}
