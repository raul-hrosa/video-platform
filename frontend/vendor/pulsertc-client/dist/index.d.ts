interface WebSocketLike {
    readonly readyState: number;
    send(data: string): void;
    close(code?: number, reason?: string): void;
    onopen: ((ev: unknown) => void) | null;
    onmessage: ((ev: {
        data: string;
    }) => void) | null;
    onclose: ((ev: {
        code: number;
        reason: string;
    }) => void) | null;
    onerror: ((ev: unknown) => void) | null;
}
interface RoomDeps {
    wsFactory(url: string, protocols: string[]): WebSocketLike;
    pcFactory(config: RTCConfiguration): RTCPeerConnection;
    getUserMedia(constraints: MediaStreamConstraints): Promise<MediaStream>;
    getDisplayMedia(constraints: MediaStreamConstraints): Promise<MediaStream>;
    now(): number;
}

type MediaSource = "camera" | "microphone" | "screen";

interface ParticipantQuality {
    status: string;
    from?: string;
    direction?: "inbound" | "outbound";
    mediaType?: string;
    reason?: string;
}

interface Participant {
    readonly identity: string;
    readonly name?: string;
}
declare class RemotePublication {
    readonly id: string;
    readonly kind: "audio" | "video";
    muted: boolean;
    track?: MediaStreamTrack;
    subscribed: boolean;
    readonly source: MediaSource;
    constructor(id: string, kind: "audio" | "video", source?: MediaSource);
}
declare class LocalPublication {
    readonly id: string;
    readonly kind: "audio" | "video";
    readonly track: MediaStreamTrack;
    muted: boolean;
    readonly source: MediaSource;
    constructor(id: string, kind: "audio" | "video", track: MediaStreamTrack, source?: MediaSource);
}
declare class RemoteParticipant implements Participant {
    readonly identity: string;
    name?: string | undefined;
    readonly publications: Map<string, RemotePublication>;
    constructor(identity: string, name?: string | undefined);
    getTrack(kind: "audio" | "video"): MediaStreamTrack | undefined;
    /**
     * The first published track of the given source that has media attached.
     * Pass `kind` to disambiguate a screen share that carries both video and
     * audio.
     */
    getTrackBySource(source: MediaSource, kind?: "audio" | "video"): MediaStreamTrack | undefined;
}
declare class LocalParticipant implements Participant {
    identity: string;
    name?: string | undefined;
    readonly publications: Map<string, LocalPublication>;
    private w;
    private stream;
    private senders;
    private waiters;
    /** publication_added echoes that arrived before the local stream was ready */
    private pendingPubs;
    private denied;
    /**
     * Screen share is a SEPARATE lane from camera/mic: its own getDisplayMedia
     * stream, its own sender(s), its own publication(s). It is never merged into
     * {@link stream} / {@link senders} so camera and screen coexist and a camera
     * mute never touches the screen. When the capture carries system/tab audio
     * (Chromium "Share audio"), that audio is published too — a second
     * publication, `kind: "audio"`, `source: "screen"`.
     */
    private screen;
    /** screen publication_added echoes that landed before the lane was ready */
    private pendingScreenPubs;
    constructor(identity: string, name?: string | undefined);
    /** Whether a screen share is currently being published. */
    get isScreenSharing(): boolean;
    private flushScreenPub;
    private flushPub;
    enableCameraAndMicrophone(): Promise<void>;
    setMicrophoneEnabled(enabled: boolean): Promise<void>;
    setCameraEnabled(enabled: boolean): Promise<void>;
    private setKind;
    /**
     * Start sharing a screen / window / tab. Prompts the browser
     * (`getDisplayMedia`), publishes the capture as an additional `video`
     * publication with `source === "screen"` — the camera keeps running — plus,
     * when the capture carries audio (Chromium "Share audio" on a tab / full
     * screen), a second `audio` publication with the same source. Resolves once
     * the SFU has acknowledged the video publication. If the user ends the share
     * from the browser UI (`track.onended`), the SDK unpublishes automatically.
     * Calling it while already sharing is a no-op.
     *
     * `constraints` defaults to `{ video: true, audio: true }`; pass
     * `{ video: true }` to never request audio.
     */
    startScreenShare(constraints?: MediaStreamConstraints): Promise<void>;
    /** Stop the current screen share (unpublish + release the capture). No-op if not sharing. */
    stopScreenShare(): Promise<void>;
    private waitFor;
    private wiring;
}

declare enum ConnectionState {
    Connecting = "connecting",
    Connected = "connected",
    Reconnecting = "reconnecting",
    Disconnected = "disconnected"
}
type DisconnectReason = "client" | "room_closed" | "replaced" | "token_expired" | "error";
declare enum RoomEvent {
    ConnectionStateChanged = "connectionStateChanged",
    ParticipantConnected = "participantConnected",
    ParticipantDisconnected = "participantDisconnected",
    TrackSubscribed = "trackSubscribed",
    TrackUnsubscribed = "trackUnsubscribed",
    TrackMuted = "trackMuted",
    QualityChanged = "qualityChanged",
    SubscribeDenied = "subscribeDenied",
    /** A non-fatal failure the SDK could not attribute to a pending `connect()`. */
    Error = "error",
    Disconnected = "disconnected"
}
interface RoomEventMap {
    [RoomEvent.ConnectionStateChanged]: [state: ConnectionState];
    [RoomEvent.ParticipantConnected]: [participant: RemoteParticipant];
    [RoomEvent.ParticipantDisconnected]: [participant: RemoteParticipant];
    [RoomEvent.TrackSubscribed]: [track: MediaStreamTrack, publication: RemotePublication, participant: RemoteParticipant];
    [RoomEvent.TrackUnsubscribed]: [publication: RemotePublication, participant: RemoteParticipant];
    [RoomEvent.TrackMuted]: [publication: RemotePublication, participant: RemoteParticipant];
    [RoomEvent.QualityChanged]: [quality: ParticipantQuality, participant: Participant];
    [RoomEvent.SubscribeDenied]: [info: {
        publicationId?: string;
    }];
    [RoomEvent.Error]: [error: Error];
    [RoomEvent.Disconnected]: [reason: DisconnectReason];
}

interface RoomOptions {
    qoe?: boolean;
}
declare class Room extends EventTarget {
    private readonly deps;
    private readonly qoeEnabled;
    private readonly sig;
    private readonly neg;
    private readonly store;
    private readonly reconnector;
    private qoe;
    private _state;
    private _local;
    private serverUrl;
    private token;
    private roomId;
    private nodeRedirects;
    private resume;
    private connecting;
    private wantConnected;
    /**
     * Server-message serialization. Only the SDP cases of {@link onMessage} are
     * asynchronous; while one of those is in flight (`busy`) every later message
     * waits in `pending`. Without this, two `sfu_offer`s arriving back to back
     * interleave — the second reads `signalingState` while the first is awaiting
     * `createAnswer()` and issues a concurrent rollback (`InvalidStateError` in
     * Chromium). Messages that resolve synchronously are still handled inline, so
     * observers see the same ordering the socket delivered.
     */
    private busy;
    private pending;
    constructor(options?: RoomOptions, deps?: Partial<RoomDeps>);
    /**
     * listener -> event name -> the wrappers registered for it. Keyed by event as
     * well as by function so that the same callback can be attached to several
     * events (or to one event twice) and `off()` removes only the intended
     * registration instead of orphaning the others.
     */
    private eventWrappers;
    on<E extends RoomEvent>(event: E, listener: (...args: RoomEventMap[E]) => void): void;
    off<E extends RoomEvent>(event: E, listener: (...args: RoomEventMap[E]) => void): void;
    private emit;
    get state(): ConnectionState;
    get localParticipant(): LocalParticipant;
    get remoteParticipants(): ReadonlyMap<string, RemoteParticipant>;
    /** Read-only escape hatch for diagnostics. `pc` is null before `welcome`. */
    get engine(): {
        pc: RTCPeerConnection | null;
    };
    connect(serverUrl: string, token: string, roomId: string): Promise<void>;
    disconnect(): Promise<void>;
    /** Forget the per-session state so this Room can be `connect()`ed again. */
    private resetSession;
    /** Close the peer connection and drop the remote track handles it owned. */
    private closeMedia;
    private setState;
    private sendJoin;
    private failConnect;
    /** Drain {@link pending}, pausing while an asynchronous handler is in flight. */
    private pump;
    /**
     * Last-resort error sink for anything thrown out of message handling. Nothing
     * `void`s a rejected promise into the void: a failure before CONNECTED fails
     * the pending `connect()`, and afterwards it surfaces as {@link RoomEvent.Error}.
     */
    private onFatal;
    /**
     * Handle one server message. Returns a promise ONLY for the cases that really
     * are asynchronous (SDP), so the common synchronous cases keep their inline
     * ordering; see {@link busy}.
     */
    private onMessage;
    private handleError;
    private teardown;
    private onClose;
    private onRemoteTrack;
    private startQoE;
}

declare class PulseRTCError extends Error {
    constructor(message: string);
}
declare class ConnectionError extends PulseRTCError {
    constructor(message: string);
}
declare class TokenError extends PulseRTCError {
    code: string;
    constructor(message: string, code: string);
}
declare class PublishError extends PulseRTCError {
    cause?: unknown;
    constructor(message: string, cause?: unknown);
}
declare class RoomOnOtherNodeError extends PulseRTCError {
    nodeId: string;
    constructor(nodeId: string);
}

declare const SDK_VERSION = "0.1.0";

export { ConnectionError, ConnectionState, type DisconnectReason, LocalParticipant, LocalPublication, type MediaSource, type Participant, type ParticipantQuality, PublishError, PulseRTCError, RemoteParticipant, RemotePublication, Room, type RoomDeps, RoomEvent, type RoomEventMap, RoomOnOtherNodeError, type RoomOptions, SDK_VERSION, TokenError, type WebSocketLike };
