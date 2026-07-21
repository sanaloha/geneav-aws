/**
 * The Nav and the Dashboard both care about session state but are siblings in
 * the tree, so neither can lift state to the other without a provider around
 * the whole app. For one boolean that changes a handful of times per session,
 * a window event is less machinery than a context.
 *
 * Without this, signing in from the Dashboard leaves the Nav showing "Sign in"
 * until the next navigation.
 */
export const AUTH_CHANGED = "geneav:auth-changed";

export function notifyAuthChanged() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(AUTH_CHANGED));
  }
}
