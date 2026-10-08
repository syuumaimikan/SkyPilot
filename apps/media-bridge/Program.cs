using System.Text.Json;
using Windows.Media.Control;
using Windows.Storage.Streams;

// One JSON response per command. No network or persisted media history.
string? command;
while ((command = Console.ReadLine()) != null) {
    try {
        var manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
        GlobalSystemMediaTransportControlsSession? session=null;
        GlobalSystemMediaTransportControlsSessionMediaProperties? media=null;
        // Games may become the system's current session without publishing usable metadata.
        // Prefer an actual playing media session, then the first valid paused session.
        var sessions=manager.GetSessions().OrderByDescending(s=>s.GetPlaybackInfo()?.PlaybackStatus==GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing);
        var errors=new List<string>();
        foreach(var candidate in sessions){try{var properties=await candidate.TryGetMediaPropertiesAsync();if(!string.IsNullOrWhiteSpace(properties.Title)){session=candidate;media=properties;break;}}catch(Exception e){errors.Add(e.GetType().Name+":"+e.HResult);}}
        if (session == null || media == null) { Console.WriteLine(JsonSerializer.Serialize(new {title="",status="No session",error=string.Join(",",errors)})); continue; }
        bool? accepted = command switch {
            "toggle" => await session.TryTogglePlayPauseAsync(),
            "play" => await session.TryPlayAsync(),
            "pause" => await session.TryPauseAsync(),
            "next" => await session.TrySkipNextAsync(),
            "previous" => await session.TrySkipPreviousAsync(),
            _ => null
        };
        var timeline = session.GetTimelineProperties();
        string art="";
        try { if(media.Thumbnail != null){
            using var stream=await media.Thumbnail.OpenReadAsync();
            if(stream.Size<=2_000_000){using var reader=new DataReader(stream);await reader.LoadAsync((uint)stream.Size);var bytes=new byte[(int)stream.Size];reader.ReadBytes(bytes);art=Convert.ToBase64String(bytes);}
        }} catch { /* Some players replace the cover stream while changing tracks. */ }
        var playback = session.GetPlaybackInfo();
        var controls = playback.Controls;
        Console.WriteLine(JsonSerializer.Serialize(new {title=media.Title,artist=media.Artist,album=media.AlbumTitle,source=session.SourceAppUserModelId,status=playback.PlaybackStatus.ToString(),position=timeline.Position.TotalSeconds,duration=timeline.EndTime.TotalSeconds,art,accepted,canPlay=controls.IsPlayEnabled,canPause=controls.IsPauseEnabled,canToggle=controls.IsPlayPauseToggleEnabled,canNext=controls.IsNextEnabled,canPrevious=controls.IsPreviousEnabled}));
    } catch(Exception e) { Console.WriteLine(JsonSerializer.Serialize(new {title="",status="Unavailable",error=e.GetType().Name})); }
}
