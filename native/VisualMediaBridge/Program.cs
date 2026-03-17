using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Media.Control;
using Windows.Storage.Streams;
using System.Threading;

internal static class Program
{
    private const string DebugEnvVar = "VISUAL_MEDIA_BRIDGE_DEBUG";

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented = false,
    };

    public static int Main(string[] args)
    {
        var debug = HasFlag(args, "--debug") || IsDebugEnabledFromEnvironment();
        var staResult = RunOnStaThread(() => ExecuteOnSta(args, debug));

        if (staResult.Exception is not null)
        {
            Console.Error.WriteLine(staResult.Exception.ToString());
            return 17;
        }

        return staResult.ExitCode;
    }

    private static int ExecuteOnSta(string[] args, bool debug)
    {
        try
        {
            if (args.Length == 0 || string.Equals(args[0], "query", StringComparison.OrdinalIgnoreCase))
            {
                var sessions = QuerySessions(debug);
                Console.Out.Write(JsonSerializer.Serialize(sessions, JsonOptions));
                return 0;
            }

            if (string.Equals(args[0], "control", StringComparison.OrdinalIgnoreCase))
            {
                var sourceQuery = GetArg(args, "--source");
                var action = GetArg(args, "--action");
                var ok = Control(sourceQuery, action, debug);
                Console.Out.Write(JsonSerializer.Serialize(new { ok }, JsonOptions));
                return ok ? 0 : 2;
            }

            Console.Error.WriteLine("Unknown command. Use: query | control --source <query> --action <previous|toggle|next>");
            return 64;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex.ToString());
            return 17;
        }
    }

    private static StaExecutionResult RunOnStaThread(Func<int> work)
    {
        var result = new StaExecutionResult();

        var thread = new Thread(() =>
        {
            try
            {
                result.ExitCode = work();
            }
            catch (Exception ex)
            {
                result.Exception = ex;
                result.ExitCode = 17;
            }
        });

        thread.Name = "visual-media-bridge-sta";
        thread.IsBackground = false;
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();

        return result;
    }

    private static bool HasFlag(string[] args, string flag)
    {
        return args.Any(arg => string.Equals(arg, flag, StringComparison.OrdinalIgnoreCase));
    }

    private static bool IsDebugEnabledFromEnvironment()
    {
        var raw = Environment.GetEnvironmentVariable(DebugEnvVar);
        if (string.IsNullOrWhiteSpace(raw)) return false;
        return raw.Equals("1", StringComparison.OrdinalIgnoreCase) ||
               raw.Equals("true", StringComparison.OrdinalIgnoreCase) ||
               raw.Equals("yes", StringComparison.OrdinalIgnoreCase);
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

    private static List<SessionDto> QuerySessions(bool debug)
    {
        var manager = GlobalSystemMediaTransportControlsSessionManager
            .RequestAsync()
            .AsTask()
            .GetAwaiter()
            .GetResult();

        var currentSession = manager.GetCurrentSession();
        var allSessions = manager.GetSessions();

        if (debug)
        {
            Console.Error.WriteLine(
                $"bridge-debug: query sessions-found={allSessions.Count} current-source='{currentSession?.SourceAppUserModelId ?? string.Empty}' thread={Thread.CurrentThread.ManagedThreadId} apartment={Thread.CurrentThread.GetApartmentState()}"
            );
        }

        var sessions = new List<SessionDto>();
        foreach (var session in allSessions)
        {
            var source = session.SourceAppUserModelId ?? string.Empty;
            var isCurrent = IsCurrentSession(session, currentSession);
            var dto = new SessionDto
            {
                Source = source,
                SourceAppId = source,
                IsCurrent = isCurrent,
                Title = string.Empty,
                Artist = string.Empty,
                Album = string.Empty,
                Subtitle = string.Empty,
                Status = "Unknown",
                Position = 0f,
                Duration = 0f,
                ArtworkPath = null,
            };

            try
            {
                var playback = session.GetPlaybackInfo();
                dto.Status = playback.PlaybackStatus.ToString();
            }
            catch (Exception ex)
            {
                dto.PlaybackError = BuildStepError("GetPlaybackInfo", ex);
                LogStepFailure(debug, source, "GetPlaybackInfo", ex);
            }

            try
            {
                var timeline = session.GetTimelineProperties();
                dto.Position = (float)timeline.Position.TotalSeconds;
                dto.Duration = (float)timeline.EndTime.TotalSeconds;
            }
            catch (Exception ex)
            {
                dto.TimelineError = BuildStepError("GetTimelineProperties", ex);
                LogStepFailure(debug, source, "GetTimelineProperties", ex);
            }

            GlobalSystemMediaTransportControlsSessionMediaProperties? media = null;
            try
            {
                if (debug)
                {
                    Console.Error.WriteLine(
                        $"bridge-debug: apartment-check source='{NormalizeForSingleLine(source)}' step='TryGetMediaPropertiesAsync' thread={Thread.CurrentThread.ManagedThreadId} apartment={Thread.CurrentThread.GetApartmentState()}"
                    );
                }

                media = session.TryGetMediaPropertiesAsync().AsTask().GetAwaiter().GetResult();
                dto.Title = media?.Title ?? string.Empty;
                dto.Artist = media?.Artist ?? string.Empty;
                dto.Album = media?.AlbumTitle ?? string.Empty;
                dto.Subtitle = media?.Subtitle ?? string.Empty;
                dto.ArtworkPath = TryExtractArtworkPath(media?.Thumbnail);
            }
            catch (Exception ex)
            {
                dto.MediaError = BuildStepError("TryGetMediaPropertiesAsync", ex);
                LogStepFailure(debug, source, "TryGetMediaPropertiesAsync", ex);
            }

            dto.Error = BuildErrorSummary(dto);
            sessions.Add(dto);

            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: session {(dto.Error is null ? "OK" : "PARTIAL")} source='{NormalizeForSingleLine(source)}' current={isCurrent} status='{dto.Status}' title='{NormalizeForSingleLine(dto.Title)}' artist='{NormalizeForSingleLine(dto.Artist)}' album='{NormalizeForSingleLine(dto.Album)}' pos={dto.Position:F2}s dur={dto.Duration:F2}s"
                );

                if (dto.Error is not null)
                {
                    Console.Error.WriteLine(
                        $"bridge-debug: session ERRORS source='{NormalizeForSingleLine(source)}' errors='{NormalizeForSingleLine(dto.Error)}'"
                    );
                }
            }
        }

        return sessions;
    }

    private static string BuildStepError(string step, Exception ex)
    {
        var hResultHex = $"0x{ex.HResult:X8}";
        var message = string.IsNullOrWhiteSpace(ex.Message) ? "<empty>" : ex.Message;
        return $"{step} failed: {ex.GetType().Name} (HRESULT={hResultHex}) {NormalizeForSingleLine(message)}";
    }

    private static string? BuildErrorSummary(SessionDto dto)
    {
        var parts = new List<string>();
        if (!string.IsNullOrWhiteSpace(dto.PlaybackError)) parts.Add(dto.PlaybackError);
        if (!string.IsNullOrWhiteSpace(dto.TimelineError)) parts.Add(dto.TimelineError);
        if (!string.IsNullOrWhiteSpace(dto.MediaError)) parts.Add(dto.MediaError);
        return parts.Count == 0 ? null : string.Join(" | ", parts);
    }

    private static void LogStepFailure(bool debug, string source, string step, Exception ex)
    {
        if (!debug) return;

        var hResultHex = $"0x{ex.HResult:X8}";
        var message = string.IsNullOrWhiteSpace(ex.Message) ? "<empty>" : ex.Message;
        var stack = string.IsNullOrWhiteSpace(ex.StackTrace) ? "<no-stack>" : ex.StackTrace;

        Console.Error.WriteLine(
            $"bridge-debug: session STEP-ERROR source='{NormalizeForSingleLine(source)}' step='{step}' type='{ex.GetType().FullName}' hresult='{hResultHex}' message='{NormalizeForSingleLine(message)}'"
        );
        Console.Error.WriteLine(
            $"bridge-debug: session STEP-STACK source='{NormalizeForSingleLine(source)}' step='{step}' stack='{NormalizeForSingleLine(stack)}'"
        );
    }

    private static string NormalizeForSingleLine(string value)
    {
        if (string.IsNullOrEmpty(value)) return string.Empty;
        return value
            .Replace('\r', ' ')
            .Replace('\n', ' ')
            .Replace('\'', '`');
    }

    private static bool IsCurrentSession(
        GlobalSystemMediaTransportControlsSession session,
        GlobalSystemMediaTransportControlsSession? currentSession
    )
    {
        if (currentSession is null) return false;
        if (ReferenceEquals(session, currentSession)) return true;

        var source = session.SourceAppUserModelId ?? string.Empty;
        var currentSource = currentSession.SourceAppUserModelId ?? string.Empty;
        return !string.IsNullOrWhiteSpace(source) &&
               source.Equals(currentSource, StringComparison.OrdinalIgnoreCase);
    }

    private static bool Control(string? sourceQuery, string? action, bool debug)
    {
        if (string.IsNullOrWhiteSpace(sourceQuery) || string.IsNullOrWhiteSpace(action))
        {
            return false;
        }

        var manager = GlobalSystemMediaTransportControlsSessionManager
            .RequestAsync()
            .AsTask()
            .GetAwaiter()
            .GetResult();

        var session = manager.GetSessions().FirstOrDefault(candidate =>
        {
            var source = candidate.SourceAppUserModelId;
            return !string.IsNullOrWhiteSpace(source) &&
                   source.Contains(sourceQuery, StringComparison.OrdinalIgnoreCase);
        });

        if (session is null)
        {
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: control no-session source-query='{sourceQuery}' action='{action}'"
                );
            }
            return false;
        }

        var ok = action.ToLowerInvariant() switch
        {
            "previous" => session.TrySkipPreviousAsync().AsTask().GetAwaiter().GetResult(),
            "toggle" => session.TryTogglePlayPauseAsync().AsTask().GetAwaiter().GetResult(),
            "next" => session.TrySkipNextAsync().AsTask().GetAwaiter().GetResult(),
            _ => false,
        };

        if (debug)
        {
            Console.Error.WriteLine(
                $"bridge-debug: control source='{session.SourceAppUserModelId ?? string.Empty}' action='{action}' ok={ok}"
            );
        }

        return ok;
    }

    private static string? TryExtractArtworkPath(IRandomAccessStreamReference? thumbnail)
    {
        if (thumbnail is null) return null;

        try
        {
            using var stream = thumbnail.OpenReadAsync().AsTask().GetAwaiter().GetResult();
            using var netStream = stream.AsStreamForRead();
            if (!netStream.CanRead) return null;

            using var memory = new MemoryStream();
            netStream.CopyTo(memory);
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
                File.WriteAllBytes(path, bytes);
            }

            return path;
        }
        catch
        {
            return null;
        }
    }

    private sealed class StaExecutionResult
    {
        public int ExitCode { get; set; } = 17;
        public Exception? Exception { get; set; }
    }

    private sealed class SessionDto
    {
        public string Source { get; set; } = string.Empty;
        public string SourceAppId { get; set; } = string.Empty;
        public bool IsCurrent { get; set; }
        public string Title { get; set; } = string.Empty;
        public string Artist { get; set; } = string.Empty;
        public string Album { get; set; } = string.Empty;
        public string Subtitle { get; set; } = string.Empty;
        public string Status { get; set; } = string.Empty;
        public float Position { get; set; }
        public float Duration { get; set; }
        public string? ArtworkPath { get; set; }
        public string? PlaybackError { get; set; }
        public string? TimelineError { get; set; }
        public string? MediaError { get; set; }
        public string? Error { get; set; }
    }
}
