/**
 * This file is included in the TypeScript compilation via tsconfig.app.json.
 * Add any global polyfills needed by the application here.
 */

import 'zone.js';

// Some libraries (e.g. SockJS/STOMP) expect Node globals to exist.
// Provide minimal browser-friendly shims to avoid ReferenceError: global is not defined.
(window as any).global = window;

// Polyfill process object if missing
(window as any).process = (window as any).process || { env: { DEBUG: undefined } };

