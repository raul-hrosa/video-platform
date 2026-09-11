# `@pulsertc/client` — vendored build

Cópia local (só `dist/`) do SDK oficial do PulseRTC, para o frontend não depender
de um caminho fora do repo (`file:../../../PulseRTC/sdk`) — o build Docker
(`context: video-platform/`) não enxerga esse caminho.

## Como atualizar

```bash
cd D:/Documentos/Projetos/PulseRTC/sdk
npm install && npm run build
cp dist/index.js dist/index.d.ts dist/index.global.js \
   D:/Documentos/Projetos/tele/video-platform/frontend/vendor/pulsertc-client/dist/
```

Depois, no frontend: `npm install` (recopia para `node_modules`) e
`npx tsc --noEmit && npm run build`.

Origem: `PulseRTC` @ `95b2e06` (`sdk/` v0.1.0).
