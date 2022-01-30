import { loadFile } from 'nbb';

export const trackHandler = (await loadFile('./handlers/track.cljs')).handler;
export const dashboardHandler = (await loadFile('./handlers/dashboard.cljs')).handler;
