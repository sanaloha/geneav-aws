"use client";

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
    <div className="chat">
      {open && (
        <div className="chat-panel" role="dialog" aria-label="geneav assistant">
          <div className="chat-header">
            <span>geneav assistant</span>
            <button className="chat-close" onClick={() => setOpen(false)} aria-label="Close chat">
              ×
            </button>
          </div>

          <div className="chat-log" ref={scrollRef}>
            {messages.map((m, i) => (
              <div key={i} className={`chat-msg ${m.role}`}>
                {m.content}
              </div>
            ))}
            {loading && <div className="chat-msg assistant chat-typing">…</div>}
            {error && <div className="chat-error">{error}</div>}
          </div>

          <div className="chat-input">
            <input
              ref={inputRef}
              type="text"
              value={input}
              placeholder="Ask about geneav…"
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKeyDown}
              disabled={loading}
            />
            <button onClick={send} disabled={loading || !input.trim()} aria-label="Send message">
              Send
            </button>
          </div>
        </div>
      )}

      <button
        className="chat-bubble"
        onClick={() => setOpen((v) => !v)}
        aria-label={open ? "Close chat" : "Open chat"}
        aria-expanded={open}
      >
        {open ? "×" : "💬"}
      </button>
    </div>
  );
}
