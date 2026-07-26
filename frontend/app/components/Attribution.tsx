"use client";

import { useEffect } from "react";
import { captureAttribution } from "../lib/attribution";

/**
 * Records where a visitor came from, once, on their first page view.
 *
 * Mounted in the root layout and renders nothing. It lives in the layout rather
 * than on the signup page because by the time someone reaches `/login` the UTM
 * parameters and external referrer that brought them here are gone — the layout
 * is the only component guaranteed to be mounted on the page they actually
 * landed on.
 */
export default function Attribution() {
  useEffect(() => {
    captureAttribution();
  }, []);

  return null;
}
