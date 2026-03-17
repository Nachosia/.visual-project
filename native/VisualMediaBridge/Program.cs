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
                dto.ArtworkPath = TryExtractArtworkPath(
                    media?.Thumbnail,
                    debug,
                    source,
                    isCurrent
                );
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
        if (string.IsNullOrWhiteSpace(action))
        {
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: control invalid-args source-query='{sourceQuery ?? string.Empty}' action='{action ?? string.Empty}'"
                );
            }
            return false;
        }

        var actionNormalized = action.Trim().ToLowerInvariant();
        var manager = GlobalSystemMediaTransportControlsSessionManager
            .RequestAsync()
            .AsTask()
            .GetAwaiter()
            .GetResult();
        var sessions = manager.GetSessions();
        var currentSession = manager.GetCurrentSession();

        if (debug)
        {
            Console.Error.WriteLine(
                $"bridge-debug: control request source-query='{NormalizeForSingleLine(sourceQuery)}' action='{NormalizeForSingleLine(actionNormalized)}' sessions-found={sessions.Count} thread={Thread.CurrentThread.ManagedThreadId} apartment={Thread.CurrentThread.GetApartmentState()} current='{NormalizeForSingleLine(currentSession?.SourceAppUserModelId ?? string.Empty)}'"
            );
            foreach (var candidate in sessions)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: control candidate source='{NormalizeForSingleLine(candidate.SourceAppUserModelId ?? string.Empty)}'"
                );
            }
        }

        GlobalSystemMediaTransportControlsSession? session = null;
        var matchStrategy = "none";
        switch (actionNormalized)
        {
            case "previous":
            case "next":
                if (currentSession is not null)
                {
                    session = currentSession;
                    matchStrategy = "current";
                }
                else
                {
                    session = MatchSessionBySourceQuery(sessions, sourceQuery, out matchStrategy);
                }
                break;
            case "toggle":
                session = MatchSessionBySourceQuery(sessions, sourceQuery, out matchStrategy);
                if (session is null && currentSession is not null)
                {
                    session = currentSession;
                    matchStrategy = "current-fallback";
                }
                break;
            default:
                if (debug)
                {
                    Console.Error.WriteLine(
                        $"bridge-debug: control invalid-action action='{NormalizeForSingleLine(actionNormalized)}'"
                    );
                }
                return false;
        }

        if (session is null)
        {
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: control no-session source-query='{sourceQuery}' action='{actionNormalized}' strategy='{matchStrategy}'"
                );
            }
            return false;
        }

        if (debug)
        {
            Console.Error.WriteLine(
                $"bridge-debug: control target source='{NormalizeForSingleLine(session.SourceAppUserModelId ?? string.Empty)}' action='{NormalizeForSingleLine(actionNormalized)}' strategy='{matchStrategy}'"
            );
        }

        bool ok;
        var gsmtcCall = string.Empty;
        try
        {
            ok = actionNormalized switch
            {
                "previous" => ExecuteControlCall(session, "TrySkipPreviousAsync", () => session.TrySkipPreviousAsync().AsTask().GetAwaiter().GetResult(), out gsmtcCall),
                "toggle" => ExecuteControlCall(session, "TryTogglePlayPauseAsync", () => session.TryTogglePlayPauseAsync().AsTask().GetAwaiter().GetResult(), out gsmtcCall),
                "next" => ExecuteControlCall(session, "TrySkipNextAsync", () => session.TrySkipNextAsync().AsTask().GetAwaiter().GetResult(), out gsmtcCall),
                _ => false,
            };
        }
        catch (Exception ex)
        {
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: control error source='{NormalizeForSingleLine(session.SourceAppUserModelId ?? string.Empty)}' action='{NormalizeForSingleLine(actionNormalized)}' call='{gsmtcCall}' type='{ex.GetType().FullName}' hresult='0x{ex.HResult:X8}' message='{NormalizeForSingleLine(ex.Message)}'"
                );
                Console.Error.WriteLine(
                    $"bridge-debug: control stack='{NormalizeForSingleLine(ex.StackTrace ?? "<no-stack>")}'"
                );
            }
            return false;
        }

        if (debug)
        {
            Console.Error.WriteLine(
                $"bridge-debug: control result source='{NormalizeForSingleLine(session.SourceAppUserModelId ?? string.Empty)}' action='{NormalizeForSingleLine(actionNormalized)}' call='{gsmtcCall}' ok={ok}"
            );
            if (!ok)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: control failure-reason source='{NormalizeForSingleLine(session.SourceAppUserModelId ?? string.Empty)}' action='{NormalizeForSingleLine(actionNormalized)}' call='{gsmtcCall}' reason='GSMTC call returned false'"
                );
            }
        }

        return ok;
    }

    private static GlobalSystemMediaTransportControlsSession? MatchSessionBySourceQuery(
        IReadOnlyList<GlobalSystemMediaTransportControlsSession> sessions,
        string sourceQuery,
        out string strategy
    )
    {
        strategy = "none";
        if (string.IsNullOrWhiteSpace(sourceQuery)) return null;

        var exact = sessions.FirstOrDefault(candidate =>
            string.Equals(candidate.SourceAppUserModelId ?? string.Empty, sourceQuery, StringComparison.OrdinalIgnoreCase));
        if (exact is not null)
        {
            strategy = "query-exact";
            return exact;
        }

        var contains = sessions.FirstOrDefault(candidate =>
        {
            var source = candidate.SourceAppUserModelId;
            return !string.IsNullOrWhiteSpace(source) &&
                   source.Contains(sourceQuery, StringComparison.OrdinalIgnoreCase);
        });
        if (contains is not null)
        {
            strategy = "query-contains";
            return contains;
        }

        return null;
    }

    private static bool ExecuteControlCall(
        GlobalSystemMediaTransportControlsSession session,
        string callName,
        Func<bool> invocation,
        out string executedCall
    )
    {
        executedCall = callName;
        return invocation();
    }

    private static string? TryExtractArtworkPath(
        IRandomAccessStreamReference? thumbnail,
        bool debug,
        string source,
        bool isCurrent
    )
    {
        if (thumbnail is null)
        {
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' current={isCurrent} thumbnail-null=true"
                );
            }
            return null;
        }

        if (debug)
        {
            Console.Error.WriteLine(
                $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' current={isCurrent} thumbnail-null=false"
            );
        }

        try
        {
            using var stream = thumbnail.OpenReadAsync().AsTask().GetAwaiter().GetResult();
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' stream-open=true size={stream.Size}"
                );
            }

            using var netStream = stream.AsStreamForRead();
            if (!netStream.CanRead)
            {
                if (debug)
                {
                    Console.Error.WriteLine(
                        $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' stream-readable=false"
                    );
                }
                return null;
            }

            using var memory = new MemoryStream();
            netStream.CopyTo(memory);
            var bytes = memory.ToArray();
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' copied-bytes={bytes.Length}"
                );
            }
            if (bytes.Length == 0)
            {
                if (debug)
                {
                    Console.Error.WriteLine(
                        $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' copied-bytes=0 (skip save)"
                    );
                }
                return null;
            }

            var hash = Convert.ToHexString(SHA1.HashData(bytes));
            var signatureHex = GetSignatureHex(bytes, 8);
            var imageFormat = DetectImageFormat(bytes);
            var extension = imageFormat.Extension;
            var formatName = imageFormat.Name;

            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' signature='{signatureHex}' detected-format='{formatName}' extension='{extension}'"
                );
            }

            var artworkDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "VisualClient",
                "artwork"
            );
            Directory.CreateDirectory(artworkDir);

            var hashedPath = Path.Combine(artworkDir, hash + extension);
            var hashedTempPath = Path.Combine(artworkDir, $"{hash}.{Guid.NewGuid():N}{extension}.tmp");
            File.WriteAllBytes(hashedTempPath, bytes);
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' temp-write=true temp-path='{NormalizeForSingleLine(hashedTempPath)}' bytes={bytes.Length}"
                );
            }

            var savedNew = false;
            try
            {
                if (File.Exists(hashedPath))
                {
                    File.Delete(hashedTempPath);
                }
                else
                {
                    File.Move(hashedTempPath, hashedPath, true);
                    savedNew = true;
                }
            }
            catch (IOException ioEx) when (File.Exists(hashedPath))
            {
                if (File.Exists(hashedTempPath))
                {
                    File.Delete(hashedTempPath);
                }
                if (debug)
                {
                    Console.Error.WriteLine(
                        $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' temp-move-race=true path='{NormalizeForSingleLine(hashedPath)}' hresult='0x{ioEx.HResult:X8}' message='{NormalizeForSingleLine(ioEx.Message)}'"
                    );
                }
            }

            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' saved-new={savedNew} path='{NormalizeForSingleLine(hashedPath)}' bytes={bytes.Length}"
                );
            }

            string livePath = hashedPath;
            if (isCurrent)
            {
                var bufferDir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "VisualClient",
                    "buffer"
                );
                Directory.CreateDirectory(bufferDir);

                var bufferTempPath = Path.Combine(bufferDir, "current_cover.tmp.png");
                var bufferFinalPath = Path.Combine(bufferDir, "current_cover.png");

                try
                {
                    File.WriteAllBytes(bufferTempPath, bytes);
                    File.Move(bufferTempPath, bufferFinalPath, true);
                    livePath = bufferFinalPath;

                    if (debug)
                    {
                        var bufferExists = File.Exists(bufferFinalPath);
                        var bufferSize = bufferExists ? new FileInfo(bufferFinalPath).Length : -1L;
                        var bufferModified = bufferExists
                            ? File.GetLastWriteTimeUtc(bufferFinalPath).ToString("O")
                            : "<missing>";
                        Console.Error.WriteLine(
                            $"bridge-debug: artwork buffer-write=true temp='{NormalizeForSingleLine(bufferTempPath)}' final='{NormalizeForSingleLine(bufferFinalPath)}'"
                        );
                        Console.Error.WriteLine(
                            $"bridge-debug: artwork buffer-final exists={bufferExists} size={bufferSize} modifiedUtc='{NormalizeForSingleLine(bufferModified)}'"
                        );
                    }
                }
                catch (Exception ex)
                {
                    if (debug)
                    {
                        Console.Error.WriteLine(
                            $"bridge-debug: artwork buffer-write-failed type='{ex.GetType().FullName}' hresult='0x{ex.HResult:X8}' message='{NormalizeForSingleLine(ex.Message)}'"
                        );
                        Console.Error.WriteLine(
                            $"bridge-debug: artwork buffer-write-stack='{NormalizeForSingleLine(ex.StackTrace ?? "<no-stack>")}'"
                        );
                    }
                }
            }

            if (debug)
            {
                var finalExists = File.Exists(livePath);
                var finalSize = finalExists ? new FileInfo(livePath).Length : -1L;
                var finalModified = finalExists
                    ? File.GetLastWriteTimeUtc(livePath).ToString("O")
                    : "<missing>";
                Console.Error.WriteLine(
                    $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' final-artwork-file exists={finalExists} size={finalSize} modifiedUtc='{NormalizeForSingleLine(finalModified)}'"
                );
                Console.Error.WriteLine(
                    $"bridge-debug: artwork source='{NormalizeForSingleLine(source)}' final-artwork-path='{NormalizeForSingleLine(livePath)}'"
                );
            }
            return livePath;
        }
        catch (Exception ex)
        {
            if (debug)
            {
                Console.Error.WriteLine(
                    $"bridge-debug: artwork error source='{NormalizeForSingleLine(source)}' type='{ex.GetType().FullName}' hresult='0x{ex.HResult:X8}' message='{NormalizeForSingleLine(ex.Message)}'"
                );
                Console.Error.WriteLine(
                    $"bridge-debug: artwork stack='{NormalizeForSingleLine(ex.StackTrace ?? "<no-stack>")}'"
                );
            }
            return null;
        }
    }

    private static (string Name, string Extension) DetectImageFormat(byte[] bytes)
    {
        if (bytes.Length >= 8 &&
            bytes[0] == 0x89 &&
            bytes[1] == 0x50 &&
            bytes[2] == 0x4E &&
            bytes[3] == 0x47 &&
            bytes[4] == 0x0D &&
            bytes[5] == 0x0A &&
            bytes[6] == 0x1A &&
            bytes[7] == 0x0A)
        {
            return ("png", ".png");
        }

        if (bytes.Length >= 3 &&
            bytes[0] == 0xFF &&
            bytes[1] == 0xD8 &&
            bytes[2] == 0xFF)
        {
            return ("jpeg", ".jpg");
        }

        // Keep unknown payload decodable by content readers, but avoid .img.
        return ("unknown", ".png");
    }

    private static string GetSignatureHex(byte[] bytes, int count)
    {
        if (bytes.Length == 0 || count <= 0) return "<empty>";
        var length = Math.Min(bytes.Length, count);
        return BitConverter
            .ToString(bytes, 0, length)
            .Replace("-", " ")
            .ToUpperInvariant();
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
