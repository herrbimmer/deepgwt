// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Ícones do painel, em SVG inline. Traçado em `currentColor', então quem pinta é o elemento.
//
// Desenhados aqui em vez de virem de uma biblioteca porque o WebView do cluster não tem internet
// e o 'app.html' precisa ser um arquivo só. Todos em viewBox 24×24.

export const ICONS_JS = `
  var ICONS = {
    headlight: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M3 7c0-1.1.9-2 2-2h4c4 0 7 3 7 7s-3 7-7 7H5a2 2 0 0 1-2-2V7Z"/><path d="M19 8h3M19 12h4M19 16h3"/></svg>',
    highBeam: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M3 7c0-1.1.9-2 2-2h4c4 0 7 3 7 7s-3 7-7 7H5a2 2 0 0 1-2-2V7Z"/><path d="M19 7h4M19 12h4M19 17h4"/></svg>',
    fogFront: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M3 7c0-1.1.9-2 2-2h3c4 0 7 3 7 7s-3 7-7 7H5a2 2 0 0 1-2-2V7Z"/><path d="M18 8.5 22 7M18 12h4M18 15.5 22 17"/></svg>',
    fogRear: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M21 7c0-1.1-.9-2-2-2h-3c-4 0-7 3-7 7s3 7 7 7h3a2 2 0 0 0 2-2V7Z"/><path d="M6 8.5 2 7M6 12H2M6 15.5 2 17"/></svg>',
    handbrake: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M9 8h3.2a2.4 2.4 0 0 1 0 4.8H9V8Zm0 4.8V16"/></svg>',
    epb: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 5v3M12 16v3M5 12h3M16 12h3"/></svg>',
    seatbelt: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="5.5" r="2.5"/><path d="M8 20V13c0-2 1.5-3.5 4-3.5s4 1.5 4 3.5v7M8.5 11.5 15 18"/></svg>',
    espOff: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M5 17c2-5 5-7 7-7s5 2 7 7"/><circle cx="12" cy="6" r="2"/><path d="M4 4l16 16"/></svg>',
    tpms: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M5 16V9a7 7 0 0 1 14 0v7"/><path d="M3 18h18M12 8v5M12 15.5v.5"/></svg>',
    charging: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><rect x="3" y="7" width="14" height="10" rx="2"/><path d="M20 10v4"/><path d="M10 9.5 8 12.5h3L9 15.5"/></svg>',
    battery: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><rect x="2" y="7" width="17" height="10" rx="2"/><path d="M22 10v4"/><path d="M11 9.5 9 12.5h3l-2 3"/></svg>',
    fuel: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 20V5a2 2 0 0 1 2-2h5a2 2 0 0 1 2 2v15M3 20h11"/><path d="M13 9h3a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V9l-2.5-2.5"/></svg>',
    engine: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M6 9h3V7h5v2h2l3 3v5h-3v-2H9l-3 3V9Z"/><path d="M3 11v3"/></svg>',
    turnLeft: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M11 4 4 11l7 7v-4h9v-6h-9V4Z"/></svg>',
    turnRight: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M13 4l7 7-7 7v-4H4V8h9V4Z"/></svg>',
    hazard: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 3 3 19h18L12 3Zm0 5 5 9H7l5-9Z"/></svg>',
    wiper: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 19 12 5l8 14"/><path d="M7 15h10"/></svg>',
    door: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M4 19V8l6-4h8v15H4Z"/><path d="M14 12h1.5"/></svg>',
    key: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="8" cy="12" r="4"/><path d="M12 12h9M18 12v3M15 12v2"/></svg>',
    snowflake: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M12 3v18M4 7.5 20 16.5M20 7.5 4 16.5"/></svg>',
    ready: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>',
    eco: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M20 4c0 9-5 14-11 14H5c0-9 5-14 11-14h4Z"/><path d="M5 20c2-4 5-7 9-9"/></svg>',
    sport: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z"/></svg>',
    music: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="7" cy="17" r="3"/><circle cx="18" cy="15" r="3"/><path d="M10 17V6l11-2v11"/></svg>',
    road: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M7 21 9 3M17 21 15 3M12 5v3M12 11v3M12 17v3"/></svg>',
    clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>'
  };
`
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
