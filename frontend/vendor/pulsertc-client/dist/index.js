// src/deps.ts
var defaultDeps = {
  wsFactory: (url, protocols) => new WebSocket(url, protocols),
  pcFactory: (config) => new RTCPeerConnection(config),
  getUserMedia: (c) => navigator.mediaDevices.getUserMedia(c),
  getDisplayMedia: (c) => navigator.mediaDevices.getDisplayMedia(c),
  now: () => performance.now()
};

// src/events.ts
var ConnectionState = /* @__PURE__ */ ((ConnectionState2) => {
  ConnectionState2["Connecting"] = "connecting";
  ConnectionState2["Connected"] = "connected";
  ConnectionState2["Reconnecting"] = "reconnecting";
  ConnectionState2["Disconnected"] = "disconnected";
  return ConnectionState2;
})(ConnectionState || {});
var RoomEvent = /* @__PURE__ */ ((RoomEvent2) => {
  RoomEvent2["ConnectionStateChanged"] = "connectionStateChanged";
  RoomEvent2["ParticipantConnected"] = "participantConnected";
  RoomEvent2["ParticipantDisconnected"] = "participantDisconnected";
  RoomEvent2["TrackSubscribed"] = "trackSubscribed";
  RoomEvent2["TrackUnsubscribed"] = "trackUnsubscribed";
  RoomEvent2["TrackMuted"] = "trackMuted";
  RoomEvent2["QualityChanged"] = "qualityChanged";
  RoomEvent2["SubscribeDenied"] = "subscribeDenied";
  RoomEvent2["Error"] = "error";
  RoomEvent2["Disconnected"] = "disconnected";
  return RoomEvent2;
})(RoomEvent || {});

// src/errors.ts
var PulseRTCError = class extends Error {
  constructor(message) {
    super(message);
    this.name = "PulseRTCError";
  }
};
var ConnectionError = class extends PulseRTCError {
  constructor(message) {
    super(message);
    this.name = "ConnectionError";
  }
};
var TokenError = class extends PulseRTCError {
  code;
  constructor(message, code) {
    super(message);
    this.name = "TokenError";
    this.code = code;
  }
};
var PublishError = class extends PulseRTCError {
  cause;
  constructor(message, cause) {
    super(message);
    this.name = "PublishError";
    this.cause = cause;
  }
};
var RoomOnOtherNodeError = class extends PulseRTCError {
  nodeId;
  constructor(nodeId) {
    super(`room is owned by node ${nodeId}`);
    this.name = "RoomOnOtherNodeError";
    this.nodeId = nodeId;
  }
};

// src/protocol.ts
function isServerMessage(v) {
  return typeof v === "object" && v !== null && typeof v.type === "string";
}

// src/signaling.ts
var Signaling = class {
  constructor(wsFactory) {
    this.wsFactory = wsFactory;
  }
  wsFactory;
  onMessage = () => {
  };
  onOpen = () => {
  };
  onClose = () => {
  };
  ws = null;
  queue = [];
  closedByOwner = false;
  closeReported = false;
  get connected() {
    return this.ws?.readyState === 1;
  }
  connect(url, token) {
    this.closedByOwner = false;
    this.closeReported = false;
    const ws = this.wsFactory(url, ["pulsertc", `pulsertc.token.${token}`]);
    this.ws = ws;
    ws.onopen = () => {
      for (const m of this.queue.splice(0)) ws.send(JSON.stringify(m));
      this.onOpen();
    };
    ws.onmessage = (ev) => {
      if (this.ws !== ws) return;
      let parsed;
      try {
        parsed = JSON.parse(ev.data);
      } catch {
        return;
      }
      if (isServerMessage(parsed)) this.onMessage(parsed);
    };
    ws.onclose = (ev) => {
      if (this.ws === ws) this.report(ev.code);
    };
    ws.onerror = () => {
    };
  }
  send(msg) {
    if (this.ws && this.ws.readyState === 1) this.ws.send(JSON.stringify(msg));
    else this.queue.push(msg);
  }
  close() {
    this.closedByOwner = true;
    this.queue = [];
    this.ws?.close(1e3, "client");
    this.report(1e3);
  }
  report(code) {
    if (this.closeReported) return;
    this.closeReported = true;
    this.onClose({ code, clean: this.closedByOwner || code === 1e3 });
  }
};

// src/negotiator.ts
var Negotiator = class {
  constructor(pcFactory, send) {
    this.pcFactory = pcFactory;
    this.send = send;
  }
  pcFactory;
  send;
  onTrack = () => {
  };
  _pc = null;
  makingOffer = false;
  ignoreOffer = false;
  remoteSet = false;
  pendingIce = [];
  /** The live peer connection; throws when there is none (internal callers). */
  get pc() {
    if (!this._pc) throw new Error("Negotiator: create() not called");
    return this._pc;
  }
  /** The live peer connection, or null before `create()` / after `close()`. */
  get pcOrNull() {
    return this._pc;
  }
  create(iceServers) {
    this._pc?.close();
    this.makingOffer = this.ignoreOffer = this.remoteSet = false;
    this.pendingIce = [];
    const pc = this.pcFactory({ iceServers });
    this._pc = pc;
    pc.onicecandidate = (ev) => {
      if (ev.candidate) this.send({ type: "sfu_ice_candidate", payload: ev.candidate.toJSON() });
    };
    pc.ontrack = (ev) => {
      const s = ev.streams[0];
      if (s) this.onTrack(ev.track, s.id, ev.track.id);
    };
    pc.onnegotiationneeded = async () => {
      try {
        this.makingOffer = true;
        await pc.setLocalDescription();
        this.send({ type: "sfu_offer", payload: { sdp: pc.localDescription.sdp } });
      } finally {
        this.makingOffer = false;
      }
    };
  }
  addLocalTrack(track, stream) {
    return this.pc.addTrack(track, stream);
  }
  removeLocalTrack(sender) {
    this.pc.removeTrack(sender);
  }
  async handleSignal(m) {
    const pc = this.pc;
    if (m.type === "sfu_ice_candidate") {
      const init = m.payload;
      if (!this.remoteSet) {
        this.pendingIce.push(init);
        return;
      }
      try {
        await pc.addIceCandidate(init);
      } catch {
        if (!this.ignoreOffer) throw new Error("addIceCandidate failed");
      }
      return;
    }
    if (m.type === "sfu_answer") {
      if (pc.signalingState !== "have-local-offer") return;
      await pc.setRemoteDescription({ type: "answer", sdp: m.payload.sdp });
      this.remoteSet = true;
      await this.flushIce();
      return;
    }
    const collision = this.makingOffer || pc.signalingState !== "stable";
    this.ignoreOffer = collision;
    if (collision) {
      await Promise.all([
        pc.setLocalDescription({ type: "rollback" }),
        pc.setRemoteDescription({ type: "offer", sdp: m.payload.sdp })
      ]);
    } else {
      await pc.setRemoteDescription({ type: "offer", sdp: m.payload.sdp });
    }
    this.remoteSet = true;
    await this.flushIce();
    await pc.setLocalDescription(await pc.createAnswer());
    this.send({ type: "sfu_answer", payload: { sdp: pc.localDescription.sdp } });
  }
  async flushIce() {
    for (const c of this.pendingIce.splice(0)) {
      try {
        await this.pc.addIceCandidate(c);
      } catch {
      }
    }
  }
  close() {
    this._pc?.close();
    this._pc = null;
  }
};

// src/qoe.ts
var s2ms = (v) => typeof v === "number" ? v * 1e3 : void 0;
var QoEReporter = class {
  constructor(o) {
    this.o = o;
  }
  o;
  timer = null;
  start() {
    if (this.timer) return;
    const tick = () => void this.collectOnce().then((s) => {
      if (s.length) this.o.send(s);
    }).catch(() => {
    });
    tick();
    this.timer = setInterval(tick, this.o.intervalMs ?? 1e3);
  }
  stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
  async collectOnce() {
    const report = await this.o.getStats();
    const byId = /* @__PURE__ */ new Map();
    report.forEach((s) => byId.set(s.id, s));
    const now = Math.round(this.o.now());
    const samples = [];
    report.forEach((raw) => {
      const s = raw;
      if (s["type"] === "outbound-rtp" && !s["isRemote"]) {
        const remote = s["remoteId"] ? byId.get(s["remoteId"]) : void 0;
        samples.push({
          publicationId: this.o.localPubId(s["kind"]) ?? "",
          kind: s["kind"],
          direction: "outbound",
          tMs: now,
          packetsSent: s["packetsSent"],
          bytesSent: s["bytesSent"],
          packetsLost: remote ? remote["packetsLost"] : void 0,
          rttMs: remote ? s2ms(remote["roundTripTime"]) : void 0,
          jitterMs: remote ? s2ms(remote["jitter"]) : void 0
        });
      } else if (s["type"] === "inbound-rtp") {
        samples.push({
          publicationId: s["trackIdentifier"] ?? "",
          kind: s["kind"],
          direction: "inbound",
          tMs: now,
          packetsReceived: s["packetsReceived"],
          packetsLost: s["packetsLost"],
          bytesReceived: s["bytesReceived"],
          framesDecoded: s["framesDecoded"],
          framesDropped: s["framesDropped"],
          jitterMs: s2ms(s["jitter"]),
          fps: s["framesPerSecond"],
          width: s["frameWidth"],
          height: s["frameHeight"]
        });
      } else if (s["type"] === "candidate-pair" && (s["selected"] || s["nominated"] && s["state"] === "succeeded")) {
        samples.push({ kind: "connection", tMs: now, rttMs: s2ms(s["currentRoundTripTime"]) });
      }
    });
    if (!samples.some((x) => x["kind"] === "connection")) samples.push({ kind: "connection", tMs: now });
    return samples;
  }
};
function parseQualityEvent(m) {
  return {
    status: m.status,
    from: m.from,
    direction: m.direction,
    mediaType: m.mediaType,
    reason: m.reason
  };
}

// src/reconnector.ts
var DEFAULTS = { initialDelayMs: 500, maxDelayMs: 1e4, jitter: 0.2 };
function nextReconnectDelay(attempt, hints, rnd) {
  const n = Math.max(0, attempt);
  const base = Math.min(hints.initialDelayMs * 2 ** n, hints.maxDelayMs);
  return base + (hints.jitter > 0 ? rnd() * base * hints.jitter : 0);
}
function decideRejoin(resume) {
  return resume ? { type: "resume", resume } : { type: "fresh" };
}
var Reconnector = class {
  hints;
  rnd;
  schedule;
  cancelPending = null;
  attempt = 0;
  constructor(o = {}) {
    this.hints = { ...DEFAULTS, ...o.hints };
    this.rnd = o.rnd ?? Math.random;
    this.schedule = o.schedule ?? ((fn, ms) => {
      const id = setTimeout(fn, ms);
      return () => clearTimeout(id);
    });
  }
  setHints(h) {
    this.hints = { ...this.hints, ...h };
  }
  get armed() {
    return this.cancelPending !== null;
  }
  arm(run) {
    this.cancel();
    const delay = nextReconnectDelay(this.attempt, this.hints, this.rnd);
    this.attempt += 1;
    this.cancelPending = this.schedule(() => {
      this.cancelPending = null;
      run();
    }, delay);
  }
  cancel() {
    this.cancelPending?.();
    this.cancelPending = null;
  }
  reset() {
    this.cancel();
    this.attempt = 0;
  }
};

// src/participants.ts
var defaultSource = (kind) => kind === "audio" ? "microphone" : "camera";
var RemotePublication = class {
  constructor(id, kind, source) {
    this.id = id;
    this.kind = kind;
    this.source = source ?? defaultSource(kind);
  }
  id;
  kind;
  muted = false;
  track;
  subscribed = false;
  source;
};
var LocalPublication = class {
  constructor(id, kind, track, source) {
    this.id = id;
    this.kind = kind;
    this.track = track;
    this.source = source ?? defaultSource(kind);
  }
  id;
  kind;
  track;
  muted = false;
  source;
};
var RemoteParticipant = class {
  constructor(identity, name) {
    this.identity = identity;
    this.name = name;
  }
  identity;
  name;
  publications = /* @__PURE__ */ new Map();
  getTrack(kind) {
    for (const p of this.publications.values()) if (p.kind === kind && p.track) return p.track;
    return void 0;
  }
  /**
   * The first published track of the given source that has media attached.
   * Pass `kind` to disambiguate a screen share that carries both video and
   * audio.
   */
  getTrackBySource(source, kind) {
    for (const p of this.publications.values()) {
      if (p.source === source && p.track && (kind === void 0 || p.kind === kind)) return p.track;
    }
    return void 0;
  }
};
var LocalParticipant = class {
  constructor(identity, name) {
    this.identity = identity;
    this.name = name;
  }
  identity;
  name;
  publications = /* @__PURE__ */ new Map();
  w = null;
  stream = null;
  senders = /* @__PURE__ */ new Map();
  waiters = /* @__PURE__ */ new Map();
  /** publication_added echoes that arrived before the local stream was ready */
  pendingPubs = /* @__PURE__ */ new Map();
  denied = false;
  /**
   * Screen share is a SEPARATE lane from camera/mic: its own getDisplayMedia
   * stream, its own sender(s), its own publication(s). It is never merged into
   * {@link stream} / {@link senders} so camera and screen coexist and a camera
   * mute never touches the screen. When the capture carries system/tab audio
   * (Chromium "Share audio"), that audio is published too — a second
   * publication, `kind: "audio"`, `source: "screen"`.
   */
  screen = null;
  /** screen publication_added echoes that landed before the lane was ready */
  pendingScreenPubs = {};
  /** @internal */
  _wire(w) {
    this.w = w;
  }
  /** Whether a screen share is currently being published. */
  get isScreenSharing() {
    return this.screen !== null;
  }
  /**
   * @internal
   * Reset the publish state after a socket reconnect: the media session is
   * rebuilt from scratch by the SFU, so the old publicationIds / senders /
   * waiters are stale. The captured {@link stream} is KEPT so `republish()`
   * can re-add its tracks against the fresh peer connection.
   */
  _resetForReconnect() {
    this.publications.clear();
    this.senders.clear();
    this.waiters.clear();
    this.pendingPubs.clear();
    this.denied = false;
    this._teardownScreen(false);
  }
  /**
   * @internal
   * Re-add the tracks of the captured local stream against the (freshly
   * created) peer connection and resolve once their `publication_added`
   * echoes land. No-op when nothing was published.
   */
  async _republish() {
    if (!this.stream) return;
    const w = this.wiring();
    const stream = this.stream;
    const audio = stream.getAudioTracks()[0];
    const video = stream.getVideoTracks()[0];
    const pending = [];
    if (audio) {
      this.senders.set("audio", w.addLocalTrack(audio, stream));
      pending.push(this.waitFor("audio"));
      this.flushPub("audio");
    }
    if (video) {
      this.senders.set("video", w.addLocalTrack(video, stream));
      pending.push(this.waitFor("video"));
      this.flushPub("video");
    }
    await Promise.all(pending);
  }
  /** @internal */
  _onPublicationAdded(kind, publicationId) {
    this.pendingPubs.set(kind, publicationId);
    this.flushPub(kind);
  }
  /** @internal */
  _onPublicationRemoved(publicationId) {
    this.publications.delete(publicationId);
    if (this.screen && (this.screen.videoPubId === publicationId || this.screen.audioPubId === publicationId)) {
      this._teardownScreen(false);
    }
  }
  /** @internal */
  _onScreenPublicationAdded(publicationId, kind) {
    this.pendingScreenPubs[kind] = publicationId;
    this.flushScreenPub();
  }
  flushScreenPub() {
    const sc = this.screen;
    if (!sc) return;
    const vId = this.pendingScreenPubs.video;
    if (vId && !sc.videoPubId) {
      sc.videoPubId = vId;
      this.publications.set(vId, new LocalPublication(vId, "video", sc.video, "screen"));
      delete this.pendingScreenPubs.video;
      sc.waiter?.resolve();
      sc.waiter = void 0;
    }
    const aId = this.pendingScreenPubs.audio;
    if (aId && sc.audio && !sc.audioPubId) {
      sc.audioPubId = aId;
      this.publications.set(aId, new LocalPublication(aId, "audio", sc.audio, "screen"));
      delete this.pendingScreenPubs.audio;
    }
  }
  /** @internal */
  _onPublicationMuted(publicationId, muted) {
    const p = this.publications.get(publicationId);
    if (p) p.muted = muted;
  }
  /** @internal */
  _onPublishDenied() {
    this.denied = true;
    for (const w of this.waiters.values()) w.reject(new PublishError("publish denied by server"));
    this.waiters.clear();
    if (this.screen?.waiter) this._teardownScreen(false);
  }
  flushPub(kind) {
    const publicationId = this.pendingPubs.get(kind);
    if (publicationId === void 0) return;
    const track = kind === "audio" ? this.stream?.getAudioTracks()[0] : this.stream?.getVideoTracks()[0];
    if (!track) return;
    this.publications.set(publicationId, new LocalPublication(publicationId, kind, track));
    this.pendingPubs.delete(kind);
    this.waiters.get(kind)?.resolve();
    this.waiters.delete(kind);
  }
  async enableCameraAndMicrophone() {
    const w = this.wiring();
    this.denied = false;
    let stream;
    try {
      stream = await w.getUserMedia({ audio: true, video: true });
    } catch {
      try {
        stream = await w.getUserMedia({ audio: true, video: false });
      } catch {
        try {
          stream = await w.getUserMedia({ audio: false, video: true });
        } catch (e) {
          throw new PublishError("getUserMedia failed", e);
        }
      }
    }
    this.stream = stream;
    const audio = stream.getAudioTracks()[0];
    const video = stream.getVideoTracks()[0];
    const pending = [];
    if (audio) {
      this.senders.set("audio", w.addLocalTrack(audio, stream));
      pending.push(this.waitFor("audio"));
      this.flushPub("audio");
    }
    if (video) {
      this.senders.set("video", w.addLocalTrack(video, stream));
      pending.push(this.waitFor("video"));
      this.flushPub("video");
    }
    if (this.denied) this._onPublishDenied();
    await Promise.all(pending);
  }
  async setMicrophoneEnabled(enabled) {
    this.setKind("audio", enabled);
  }
  async setCameraEnabled(enabled) {
    this.setKind("video", enabled);
  }
  setKind(kind, enabled) {
    const track = kind === "audio" ? this.stream?.getAudioTracks()[0] : this.stream?.getVideoTracks()[0];
    if (!track) return;
    track.enabled = enabled;
    for (const p of this.publications.values()) {
      if (p.kind === kind && p.source !== "screen") {
        p.muted = !enabled;
        this.wiring().send({ type: "set_mute", publicationId: p.id, muted: !enabled, kind });
      }
    }
  }
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
  async startScreenShare(constraints = { video: true, audio: true }) {
    if (this.screen) return;
    const w = this.wiring();
    let stream;
    try {
      stream = await w.getDisplayMedia(constraints);
    } catch (e) {
      throw new PublishError("getDisplayMedia failed", e);
    }
    const video = stream.getVideoTracks()[0];
    if (!video) {
      stream.getTracks().forEach((t) => t.stop());
      throw new PublishError("no screen video track");
    }
    const audio = stream.getAudioTracks()[0];
    const onended = () => {
      void this.stopScreenShare().catch(() => {
      });
    };
    video.addEventListener("ended", onended);
    const sc = { stream, video, audio, onended };
    this.screen = sc;
    w.send({ type: "publish", trackId: video.id, kind: "video", source: "screen" });
    if (audio) w.send({ type: "publish", trackId: audio.id, kind: "audio", source: "screen" });
    try {
      sc.videoSender = w.addLocalTrack(video, stream);
      if (audio) sc.audioSender = w.addLocalTrack(audio, stream);
    } catch (e) {
      this._teardownScreen(false);
      throw new PublishError("failed to add screen track", e);
    }
    const done = new Promise((resolve, reject) => {
      sc.waiter = { resolve, reject };
    });
    this.flushScreenPub();
    await done;
  }
  /** Stop the current screen share (unpublish + release the capture). No-op if not sharing. */
  async stopScreenShare() {
    this._teardownScreen(true);
  }
  /** @internal */
  _teardownScreen(notify) {
    this.pendingScreenPubs = {};
    const sc = this.screen;
    if (!sc) return;
    this.screen = null;
    sc.video.removeEventListener("ended", sc.onended);
    sc.stream.getTracks().forEach((t) => t.stop());
    for (const sender of [sc.videoSender, sc.audioSender]) {
      if (sender && this.w) {
        try {
          this.w.removeLocalTrack(sender);
        } catch {
        }
      }
    }
    for (const id of [sc.videoPubId, sc.audioPubId]) {
      if (!id) continue;
      this.publications.delete(id);
      if (notify && this.w) this.w.send({ type: "unpublish", publicationId: id });
    }
    sc.waiter?.reject(new PublishError("screen share stopped"));
    sc.waiter = void 0;
  }
  waitFor(kind) {
    return new Promise((resolve, reject) => this.waiters.set(kind, { resolve, reject }));
  }
  wiring() {
    if (!this.w) throw new PublishError("not connected");
    return this.w;
  }
};
var ParticipantStore = class {
  remote = /* @__PURE__ */ new Map();
  localIdentity;
  /**
   * Tracks whose `ontrack` fired before we knew the matching publication,
   * keyed by stream id (the publisher's participant id). Matched to a
   * publication by the receiver track id when the browser carries the SFU's
   * msid track id (`pub-…`), otherwise by (streamId, kind) in arrival order.
   */
  pendingTracks = /* @__PURE__ */ new Map();
  applyWelcome(m) {
    this.localIdentity = m.participantId;
  }
  /**
   * Reconcile the store against the authoritative membership carried by
   * `room_joined`. `room_joined` is the *full* membership list, both on a fresh
   * connect and on a reconnect (including a node-failure rejoin, where the
   * participant ids change wholesale) — so anything the server did not list is
   * gone and must be reported as a departure rather than silently kept.
   *
   * @returns the participants created by this message (`added`) and the ones
   * dropped because the server no longer lists them (`removed`).
   */
  applyRoomJoined(m) {
    const desired = /* @__PURE__ */ new Map();
    for (const p of m.participants) desired.set(p.participantId, { name: p.name });
    for (const p of m.snapshot?.participants ?? []) {
      const prev = desired.get(p.participantId);
      desired.set(p.participantId, { name: p.name ?? prev?.name, tracks: p.tracks });
    }
    const removed = [];
    for (const [id, rp] of [...this.remote]) {
      if (desired.has(id)) continue;
      this.remote.delete(id);
      removed.push(rp);
    }
    const added = [];
    for (const [id, info] of desired) {
      const existed = this.remote.has(id);
      const rp = this.ensure(id, info.name);
      if (!existed) added.push(rp);
      if (!info.tracks) continue;
      const keep = new Set(info.tracks.map((t) => t.publicationId));
      for (const pubId of [...rp.publications.keys()]) if (!keep.has(pubId)) rp.publications.delete(pubId);
      for (const t of info.tracks) {
        let pub = rp.publications.get(t.publicationId);
        if (!pub) {
          pub = new RemotePublication(t.publicationId, t.kind, t.source);
          rp.publications.set(t.publicationId, pub);
          pub.track = this.takeBufferedTrack(id, t.kind);
        }
        pub.muted = t.muted;
      }
    }
    return { added, removed };
  }
  /**
   * Drop every remote track handle: they belong to a peer connection that is
   * being closed (reconnect or teardown) and are dead once it goes away. The
   * publications themselves survive — `room_joined`/`publication_added` on the
   * new session re-attach fresh tracks.
   */
  clearTracks() {
    for (const rp of this.remote.values()) {
      for (const pub of rp.publications.values()) {
        pub.track = void 0;
        pub.subscribed = false;
      }
    }
    this.pendingTracks.clear();
  }
  /** Forget everything: used when a Room is disconnected and may be reused. */
  clear() {
    this.remote.clear();
    this.pendingTracks.clear();
    this.localIdentity = void 0;
  }
  applyParticipantJoined(m) {
    return this.ensure(m.participantId, m.name);
  }
  applyParticipantLeft(m) {
    const rp = this.remote.get(m.participantId);
    if (rp) this.remote.delete(m.participantId);
    return rp;
  }
  applyPublicationEvent(m) {
    if (m.participantId === this.localIdentity) return void 0;
    const rp = this.ensure(m.participantId);
    if (m.type === "publication_removed") {
      const pub2 = rp.publications.get(m.publicationId);
      if (!pub2) return void 0;
      rp.publications.delete(m.publicationId);
      return { participant: rp, publication: pub2, removed: true };
    }
    let pub = rp.publications.get(m.publicationId);
    if (!pub) {
      pub = new RemotePublication(m.publicationId, m.kind, m.source);
      rp.publications.set(m.publicationId, pub);
      pub.track = this.takeBufferedTrack(m.participantId, m.kind);
    }
    pub.muted = m.muted;
    return { participant: rp, publication: pub, removed: false };
  }
  applySubscriptionEvent(m) {
    const pub = this.remote.get(m.participantId)?.publications.get(m.publicationId);
    if (pub) pub.subscribed = m.type === "subscription_added";
  }
  attachTrack(streamId, track, trackId) {
    const kind = track.kind === "audio" ? "audio" : "video";
    const rp = this.remote.get(streamId);
    if (!rp) {
      this.bufferTrack(streamId, track);
      return void 0;
    }
    let pub = trackId ? rp.publications.get(trackId) : void 0;
    if (pub?.track) pub = void 0;
    if (!pub) pub = this.freePublication(rp, kind);
    if (!pub) {
      this.bufferTrack(streamId, track);
      return void 0;
    }
    pub.track = track;
    return { participant: rp, publication: pub };
  }
  /** First publication of `kind` on `rp` that has no track yet. */
  freePublication(rp, kind) {
    for (const p of rp.publications.values()) if (p.kind === kind && !p.track) return p;
    return void 0;
  }
  bufferTrack(streamId, track) {
    const list = this.pendingTracks.get(streamId);
    if (list) list.push(track);
    else this.pendingTracks.set(streamId, [track]);
  }
  /** Remove and return a buffered track for (streamId, kind), if any. */
  takeBufferedTrack(streamId, kind) {
    const list = this.pendingTracks.get(streamId);
    if (!list) return void 0;
    const i = list.findIndex((t2) => (t2.kind === "audio" ? "audio" : "video") === kind);
    if (i < 0) return void 0;
    const [t] = list.splice(i, 1);
    if (list.length === 0) this.pendingTracks.delete(streamId);
    return t;
  }
  ensure(identity, name) {
    let rp = this.remote.get(identity);
    if (!rp) {
      rp = new RemoteParticipant(identity, name);
      this.remote.set(identity, rp);
    } else if (name && !rp.name) rp.name = name;
    return rp;
  }
};

// src/room.ts
var TOKEN_ERROR_CODES = /* @__PURE__ */ new Set([
  "INVALID_TOKEN",
  "EXPIRED_TOKEN",
  "INVALID_ISSUER",
  "INVALID_AUDIENCE",
  "ROOM_ACCESS_DENIED",
  "JOIN_NOT_ALLOWED",
  "UNAUTHENTICATED"
]);
var MAX_NODE_REDIRECTS = 5;
var Room = class extends EventTarget {
  deps;
  qoeEnabled;
  sig;
  neg;
  store = new ParticipantStore();
  reconnector = new Reconnector();
  qoe = null;
  _state = "disconnected" /* Disconnected */;
  _local = new LocalParticipant("");
  serverUrl = "";
  token = "";
  roomId = "";
  nodeRedirects = 0;
  resume = null;
  connecting = null;
  wantConnected = false;
  /**
   * Server-message serialization. Only the SDP cases of {@link onMessage} are
   * asynchronous; while one of those is in flight (`busy`) every later message
   * waits in `pending`. Without this, two `sfu_offer`s arriving back to back
   * interleave — the second reads `signalingState` while the first is awaiting
   * `createAnswer()` and issues a concurrent rollback (`InvalidStateError` in
   * Chromium). Messages that resolve synchronously are still handled inline, so
   * observers see the same ordering the socket delivered.
   */
  busy = false;
  pending = [];
  constructor(options = {}, deps = {}) {
    super();
    this.deps = { ...defaultDeps, ...deps };
    this.qoeEnabled = options.qoe !== false;
    this.sig = new Signaling(this.deps.wsFactory);
    this.neg = new Negotiator(this.deps.pcFactory, (m) => this.sig.send(m));
    this.sig.onOpen = () => this.sendJoin();
    this.sig.onMessage = (m) => {
      this.pending.push(m);
      this.pump();
    };
    this.sig.onClose = (info) => this.onClose(info);
    this.neg.onTrack = (track, streamId, trackId) => this.onRemoteTrack(track, streamId, trackId);
  }
  /**
   * listener -> event name -> the wrappers registered for it. Keyed by event as
   * well as by function so that the same callback can be attached to several
   * events (or to one event twice) and `off()` removes only the intended
   * registration instead of orphaning the others.
   */
  eventWrappers = /* @__PURE__ */ new WeakMap();
  on(event, listener) {
    const wrapper = (ev) => listener(...ev.detail);
    let byEvent = this.eventWrappers.get(listener);
    if (!byEvent) {
      byEvent = /* @__PURE__ */ new Map();
      this.eventWrappers.set(listener, byEvent);
    }
    const wrappers = byEvent.get(event);
    if (wrappers) wrappers.push(wrapper);
    else byEvent.set(event, [wrapper]);
    this.addEventListener(event, wrapper);
  }
  off(event, listener) {
    const byEvent = this.eventWrappers.get(listener);
    const wrappers = byEvent?.get(event);
    const wrapper = wrappers?.pop();
    if (!wrapper) return;
    this.removeEventListener(event, wrapper);
    if (wrappers && wrappers.length === 0) byEvent?.delete(event);
  }
  emit(event, ...args) {
    this.dispatchEvent(new CustomEvent(event, { detail: args }));
  }
  get state() {
    return this._state;
  }
  get localParticipant() {
    return this._local;
  }
  get remoteParticipants() {
    return this.store.remote;
  }
  /** Read-only escape hatch for diagnostics. `pc` is null before `welcome`. */
  get engine() {
    return { pc: this.neg.pcOrNull };
  }
  connect(serverUrl, token, roomId) {
    this.serverUrl = serverUrl;
    this.token = token;
    this.roomId = roomId;
    this.wantConnected = true;
    this.setState("connecting" /* Connecting */);
    return new Promise((resolve, reject) => {
      this.connecting = { resolve, reject };
      this.sig.connect(serverUrl, token);
    });
  }
  async disconnect() {
    this.wantConnected = false;
    this.reconnector.reset();
    this.qoe?.stop();
    this.qoe = null;
    this.sig.close();
    this.closeMedia();
    this.connecting?.reject(new ConnectionError("disconnected"));
    this.connecting = null;
    this.resetSession();
    this.setState("disconnected" /* Disconnected */);
    this.emit("disconnected" /* Disconnected */, "client");
  }
  /** Forget the per-session state so this Room can be `connect()`ed again. */
  resetSession() {
    this._local = new LocalParticipant("");
    this.store.clear();
    this.resume = null;
    this.nodeRedirects = 0;
    this.pending = [];
  }
  /** Close the peer connection and drop the remote track handles it owned. */
  closeMedia() {
    this.neg.close();
    this.store.clearTracks();
  }
  setState(s) {
    if (this._state === s) return;
    this._state = s;
    this.emit("connectionStateChanged" /* ConnectionStateChanged */, s);
  }
  sendJoin() {
    const j = { type: "join", roomId: this.roomId };
    const d = decideRejoin(this.resume);
    if (d.type === "resume") j.resume = d.resume;
    this.sig.send(j);
  }
  failConnect(err) {
    this.connecting?.reject(err);
    this.connecting = null;
    this.wantConnected = false;
    this.setState("disconnected" /* Disconnected */);
  }
  /** Drain {@link pending}, pausing while an asynchronous handler is in flight. */
  pump() {
    while (!this.busy) {
      const m = this.pending.shift();
      if (m === void 0) return;
      let r;
      try {
        r = this.onMessage(m);
      } catch (e) {
        this.onFatal(e);
        continue;
      }
      if (!r) continue;
      this.busy = true;
      r.then(void 0, (e) => this.onFatal(e)).then(() => {
        this.busy = false;
        this.pump();
      });
    }
  }
  /**
   * Last-resort error sink for anything thrown out of message handling. Nothing
   * `void`s a rejected promise into the void: a failure before CONNECTED fails
   * the pending `connect()`, and afterwards it surfaces as {@link RoomEvent.Error}.
   */
  onFatal(e) {
    const err = e instanceof Error ? e : new Error(String(e));
    if (this.connecting) {
      this.failConnect(err);
      return;
    }
    this.emit("error" /* Error */, err);
  }
  /**
   * Handle one server message. Returns a promise ONLY for the cases that really
   * are asynchronous (SDP), so the common synchronous cases keep their inline
   * ordering; see {@link busy}.
   */
  onMessage(m) {
    switch (m.type) {
      case "welcome":
        this.store.applyWelcome(m);
        if (this._local.identity === "") this._local = new LocalParticipant(m.participantId, m.name);
        else this._local._resetForReconnect();
        this._local._wire({
          getUserMedia: this.deps.getUserMedia,
          getDisplayMedia: this.deps.getDisplayMedia,
          addLocalTrack: (t, s) => this.neg.addLocalTrack(t, s),
          removeLocalTrack: (s) => this.neg.removeLocalTrack(s),
          send: (m2) => this.sig.send(m2)
        });
        this.neg.create(m.iceServers ?? []);
        break;
      case "room_joined": {
        this.roomId = m.roomId;
        this.nodeRedirects = 0;
        if (m.reconnect) this.reconnector.setHints(m.reconnect);
        if (m.sessionId) this.resume = { sessionId: m.sessionId, generation: m.generation ?? 0 };
        const { added, removed } = this.store.applyRoomJoined(m);
        for (const rp of removed) {
          for (const pub of rp.publications.values()) {
            if (pub.track) this.emit("trackUnsubscribed" /* TrackUnsubscribed */, pub, rp);
          }
          this.emit("participantDisconnected" /* ParticipantDisconnected */, rp);
        }
        for (const rp of added) this.emit("participantConnected" /* ParticipantConnected */, rp);
        this.reconnector.reset();
        this.setState("connected" /* Connected */);
        this.connecting?.resolve();
        this.connecting = null;
        if (m.reconnected) {
          void this._local._republish().catch((e) => this.onFatal(e));
        }
        if (this.qoeEnabled) this.startQoE();
        break;
      }
      case "participant_joined":
        this.emit("participantConnected" /* ParticipantConnected */, this.store.applyParticipantJoined(m));
        break;
      case "participant_left": {
        const rp = this.store.applyParticipantLeft(m);
        if (rp) this.emit("participantDisconnected" /* ParticipantDisconnected */, rp);
        break;
      }
      case "sfu_offer":
      case "sfu_answer":
      case "sfu_ice_candidate":
        return this.neg.handleSignal(m);
      case "publication_added":
      case "publication_removed":
      case "publication_muted": {
        if (m.participantId === this._local.identity) {
          if (m.type === "publication_added") {
            if (m.source === "screen") this._local._onScreenPublicationAdded(m.publicationId, m.kind);
            else this._local._onPublicationAdded(m.kind, m.publicationId);
          } else if (m.type === "publication_removed") this._local._onPublicationRemoved(m.publicationId);
          else this._local._onPublicationMuted(m.publicationId, m.muted);
          break;
        }
        const r = this.store.applyPublicationEvent(m);
        if (!r) break;
        if (r.removed) this.emit("trackUnsubscribed" /* TrackUnsubscribed */, r.publication, r.participant);
        else if (m.type === "publication_muted") this.emit("trackMuted" /* TrackMuted */, r.publication, r.participant);
        else if (r.publication.track) this.emit("trackSubscribed" /* TrackSubscribed */, r.publication.track, r.publication, r.participant);
        break;
      }
      case "subscription_added":
      case "subscription_removed":
        this.store.applySubscriptionEvent(m);
        break;
      case "quality_degraded":
      case "quality_recovered":
      case "quality_changed": {
        const who = m.participantId === this._local.identity ? this._local : this.store.remote.get(m.participantId) ?? { identity: m.participantId };
        this.emit("qualityChanged" /* QualityChanged */, parseQualityEvent(m), who);
        break;
      }
      case "publish_denied":
        this._local._onPublishDenied();
        break;
      case "subscribe_denied":
        this.emit("subscribeDenied" /* SubscribeDenied */, { publicationId: m.publicationId });
        break;
      case "room_closed":
        this.teardown("room_closed");
        break;
      case "session.stale":
        this.resume = null;
        this.sendJoin();
        break;
      case "session.reconnected":
        break;
      case "session.recovery":
        break;
      case "session.recovery_failed":
        this.resume = null;
        break;
      case "session.replaced":
        this.teardown("replaced");
        break;
      case "error":
        this.handleError(m.code, m.message, m.nodeId);
        break;
      case "room.snapshot":
        break;
      default: {
        const unhandled = m;
        console.warn("[pulsertc] unhandled server message", unhandled.type);
        break;
      }
    }
  }
  handleError(code, message, nodeId) {
    if (this.connecting) {
      if (code && TOKEN_ERROR_CODES.has(code)) this.failConnect(new TokenError(message ?? code, code));
      else if (code === "ROOM_ON_OTHER_NODE") this.failConnect(new RoomOnOtherNodeError(nodeId ?? "?"));
      else this.failConnect(new ConnectionError(message ?? code ?? "connection error"));
      return;
    }
    if (code === "EXPIRED_TOKEN") {
      this.teardown("token_expired");
      return;
    }
    if (code === "ROOM_ON_OTHER_NODE") {
      this.nodeRedirects += 1;
      if (this.nodeRedirects > MAX_NODE_REDIRECTS) {
        this.teardown("error");
        return;
      }
      this.sig.close();
      this.setState("reconnecting" /* Reconnecting */);
      this.reconnector.arm(() => this.sig.connect(this.serverUrl, this.token));
      return;
    }
    this.emit("error" /* Error */, new ConnectionError(message ?? code ?? "server error"));
  }
  teardown(reason) {
    this.wantConnected = false;
    this.reconnector.reset();
    this.qoe?.stop();
    this.qoe = null;
    this.sig.close();
    this.closeMedia();
    this.connecting?.reject(new ConnectionError(`disconnected (${reason})`));
    this.connecting = null;
    this.resetSession();
    this.setState("disconnected" /* Disconnected */);
    this.emit("disconnected" /* Disconnected */, reason);
  }
  onClose(info) {
    if (!this.wantConnected) return;
    if (this.connecting) {
      this.failConnect(new ConnectionError(`socket closed (${info.code})`));
      return;
    }
    this.setState("reconnecting" /* Reconnecting */);
    this.closeMedia();
    this.reconnector.arm(() => this.sig.connect(this.serverUrl, this.token));
  }
  onRemoteTrack(track, streamId, trackId) {
    const r = this.store.attachTrack(streamId, track, trackId);
    if (r) this.emit("trackSubscribed" /* TrackSubscribed */, track, r.publication, r.participant);
  }
  startQoE() {
    this.qoe?.stop();
    this.qoe = new QoEReporter({
      getStats: () => this.neg.pc.getStats(),
      send: (samples) => this.sig.send({ type: "quality_report", samples }),
      localPubId: (kind) => {
        for (const p of this._local.publications.values()) if (p.kind === kind) return p.id;
        return void 0;
      },
      now: this.deps.now
    });
    this.qoe.start();
  }
};

// src/index.ts
var SDK_VERSION = "0.1.0";
export {
  ConnectionError,
  ConnectionState,
  LocalParticipant,
  LocalPublication,
  PublishError,
  PulseRTCError,
  RemoteParticipant,
  RemotePublication,
  Room,
  RoomEvent,
  RoomOnOtherNodeError,
  SDK_VERSION,
  TokenError
};
