"use client";

import clsx from "clsx";
import { MessageCircle, Send, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

type Role = "user" | "assistant";
type Message = { role: Role; content: string };

const GREETING: Message = {
  role: "assistant",
  content:
    "Hi! I'm the geneav assistant. Ask me about how geneav scans documents, the file types it supports, or the protection it provides.",
};

export default function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([GREETING]);
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

  const send = async () => {
    const text = input.trim();
    if (!text || loading) return;

    const next = [...messages, { role: "user" as const, content: text }];
    setMessages(next);
    setInput("");
    setError(null);
    setLoading(true);

    try {
      // Send the conversation without the canned greeting.
      const history = next.filter((m) => m !== GREETING);
      const res = await fetch(`${API_BASE}/api/v1/chat`, {
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
      setError(`Could not reach the assistant. Make sure the API is running at ${API_BASE}.`);
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
