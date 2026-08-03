"use client";

import clsx from "clsx";
import { MessageCircle, Send, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

/**
 * Same-origin proxy rather than the API directly: /api/v1/chat now requires an API
 * key, and the widget answers visitors who have no account. The routes attach a
 * demo key on the server, where it stays. See app/lib/siteApi.ts.
 */
const CHAT_BASE = "/site-api/chat";

type Role = "user" | "assistant";
type Message = { role: Role; content: string };
type Suggestion = { id: string; question: string; answer: string };

const GREETING: Message = {
  role: "assistant",
  content:
    "Hi! I'm the geneav assistant. Ask me about how geneav scans documents, the file types it supports, or the protection it provides.",
};

// Suggestions only change on deploy, so fetch them once per page and share the
// result across opens (and across widget instances, if there is ever more than one).
let suggestionsCache: Promise<Suggestion[]> | null = null;

function loadSuggestions(): Promise<Suggestion[]> {
  suggestionsCache ??= fetch(`${CHAT_BASE}/suggestions`)
    .then((res) => (res.ok ? res.json() : []))
    // Chips are a convenience, never a requirement: if they cannot be fetched the
    // widget silently falls back to being the plain text box it was before.
    .catch(() => []);
  return suggestionsCache;
}

let assistantEnabledCache: Promise<boolean> | null = null;

function loadAssistantEnabled(): Promise<boolean> {
  // An unreachable health check is treated as available: the send path already
  // reports its own errors, and wrongly hiding the input is the worse failure.
  assistantEnabledCache ??= fetch(`${CHAT_BASE}/health`)
    .then((res) => (res.ok ? res.json() : { enabled: true }))
    .then((data: { enabled?: boolean }) => data.enabled !== false)
    .catch(() => true);
  return assistantEnabledCache;
}

export default function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([GREETING]);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  // Null until /chat/health answers. Assume available until told otherwise, so a
  // slow health check never blinks the input away from someone mid-question.
  const [assistantEnabled, setAssistantEnabled] = useState<boolean | null>(null);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Keep the transcript scrolled to the latest message.
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, loading]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  // Fetched on first open rather than on page load: most visitors never open the
  // chat, and every /api/* call spends from their per-IP budget at the proxy.
  useEffect(() => {
    if (!open) return;
    let active = true;
    loadSuggestions().then((list) => {
      if (active) setSuggestions(list);
    });
    loadAssistantEnabled().then((enabled) => {
      if (active) setAssistantEnabled(enabled);
    });
    return () => {
      active = false;
    };
  }, [open]);

  // With no API key configured the model is unreachable, so offering a text box
  // would just produce a 503. The canned answers cost nothing and still work.
  const assistantOffline = assistantEnabled === false;

  /**
   * Answers a predefined question from the payload the server already sent —
   * no request, no model call, no tokens. That is the entire reason these chips
   * exist, so this must never be routed through send().
   */
  const pick = (suggestion: Suggestion) => {
    if (loading) return;
    setError(null);
    setMessages((prev) => [
      ...prev,
      { role: "user", content: suggestion.question },
      { role: "assistant", content: suggestion.answer },
    ]);
    inputRef.current?.focus();
  };

  const send = async () => {
    const text = input.trim();
    if (!text || loading) return;

    const next = [...messages, { role: "user" as const, content: text }];
    setMessages(next);
    setInput("");
    setError(null);
    setLoading(true);

    try {
      // Send the conversation without the canned greeting. Answers that came from
      // a suggestion chip *are* kept: they are real context, and a follow-up like
      // "and what about ZIP files?" is meaningless to the model without them.
      const history = next.filter((m) => m !== GREETING);
      const res = await fetch(CHAT_BASE, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: history }),
      });

      if (!res.ok) {
        let message = `Chat failed (HTTP ${res.status}).`;
        try {
          const err = await res.json();
          if (err?.message) message = err.message;
        } catch {
          /* non-JSON error body */
        }
        setError(message);
        return;
      }

      const data: { reply: string } = await res.json();
      setMessages((prev) => [...prev, { role: "assistant", content: data.reply }]);
    } catch {
      // Same-origin now, so a network failure here means the site is unreachable
      // rather than a misconfigured API URL.
      setError("Could not reach the assistant. Please check your connection and try again.");
    } finally {
      setLoading(false);
    }
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  return (
    <div className="fixed bottom-5 right-5 z-50">
      {open && (
        <div
          className="mb-3 flex h-[min(520px,calc(100vh-120px))] w-[min(380px,calc(100vw-40px))] flex-col overflow-hidden rounded-card border border-line bg-surface shadow-lg"
          role="dialog"
          aria-label="geneav assistant"
        >
          <div className="flex items-center justify-between border-b border-line bg-surface-subtle px-4 py-3 font-bold text-ink">
            <span>geneav assistant</span>
            <button
              className="rounded-md p-1 leading-none text-ink-muted hover:bg-surface-sunken hover:text-ink"
              onClick={() => setOpen(false)}
              aria-label="Close chat"
            >
              <X size={18} aria-hidden />
            </button>
          </div>

          <div className="flex flex-1 flex-col gap-2.5 overflow-y-auto p-3.5" ref={scrollRef}>
            {messages.map((m, i) => (
              <div
                key={i}
                className={clsx(
                  "max-w-[85%] whitespace-pre-wrap break-words rounded-xl px-3 py-2.5 text-sm",
                  m.role === "user"
                    ? "self-end rounded-br-[4px] bg-brand text-white"
                    : "self-start rounded-bl-[4px] border border-line bg-surface-sunken text-ink"
                )}
              >
                {m.content}
              </div>
            ))}
            {loading && (
              <div className="max-w-[85%] self-start rounded-xl rounded-bl-[4px] border border-line bg-surface-sunken px-3 py-2.5 text-sm tracking-[2px] text-ink-muted">
                …
              </div>
            )}
            {error && <div className="self-center text-center text-[13px] text-danger">{error}</div>}
          </div>

          {/* Sits between the transcript and the input, outside the scroll area, so
              it stays reachable for the whole conversation rather than only at the
              greeting. A canned answer is free at any point, not just the first
              turn — and when the assistant is offline these are the only way to
              ask anything. One line, scrolled horizontally, so the panel keeps
              nearly all its height for the transcript. */}
          {suggestions.length > 0 && (
            <div
              className="flex shrink-0 gap-2 overflow-x-auto border-t border-line px-3 py-2"
              role="group"
              aria-label="Suggested questions"
            >
              {suggestions.map((s) => (
                <button
                  key={s.id}
                  onClick={() => pick(s)}
                  className="whitespace-nowrap rounded-full border border-line bg-surface px-3 py-1.5 text-[13px] font-medium text-ink-muted transition-colors hover:border-brand hover:text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                >
                  {s.question}
                </button>
              ))}
            </div>
          )}

          {assistantOffline ? (
            <div className="border-t border-line bg-surface-subtle px-4 py-3 text-center text-[13px] text-ink-muted">
              Live chat is offline right now. The questions above are still answered.
            </div>
          ) : (
            <div className="flex gap-2 border-t border-line bg-surface-subtle p-3">
              <input
                ref={inputRef}
                type="text"
                value={input}
                placeholder="Ask about geneav…"
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={onKeyDown}
                disabled={loading}
                className="flex-1 rounded-[10px] border border-line-control bg-surface px-3 py-2.5 text-sm text-ink placeholder:text-ink-subtle focus-visible:border-brand focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-brand/15"
              />
              <button
                onClick={send}
                disabled={loading || !input.trim()}
                aria-label="Send message"
                className="rounded-[10px] bg-brand px-4 font-semibold text-white transition-colors hover:bg-brand-hover disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Send size={16} aria-hidden />
              </button>
            </div>
          )}
        </div>
      )}

      <button
        className="ml-auto flex h-14 w-14 items-center justify-center rounded-full bg-brand text-white shadow-lg transition-colors hover:bg-brand-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
        onClick={() => setOpen((v) => !v)}
        aria-label={open ? "Close chat" : "Open chat"}
        aria-expanded={open}
      >
        {open ? <X size={24} aria-hidden /> : <MessageCircle size={24} aria-hidden />}
      </button>
    </div>
  );
}
