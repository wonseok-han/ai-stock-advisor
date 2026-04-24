"use client";

import { useEffect, useState } from "react";

import { cn } from "@/lib/cn";

export function PanelLoading({
  title,
  text,
}: {
  title: string;
  text: string;
}) {
  return (
    <section className="card p-5">
      <h2 className="text-sm font-semibold text-fg">{title}</h2>
      <div className="mt-4 flex items-start gap-3 rounded-xl bg-bg-muted p-4">
        <LoadingDots />
        <TypingText text={text} />
      </div>
    </section>
  );
}

function TypingText({ text }: { text: string }) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (count >= text.length) {
      const reset = setTimeout(() => setCount(0), 2000);
      return () => clearTimeout(reset);
    }
    const id = setTimeout(() => setCount((c) => c + 1), 60);
    return () => clearTimeout(id);
  }, [count, text.length]);

  return (
    <p
      className="text-sm font-medium text-fg"
      aria-label={text}
      role="status"
    >
      {text.split("").map((char, i) => (
        <span
          key={i}
          className={cn(
            "inline-block transition-all duration-150 ease-out",
            i < count ? "translate-y-0 opacity-100" : "translate-y-1.5 opacity-0",
          )}
        >
          {char === " " ? " " : char}
        </span>
      ))}
    </p>
  );
}

function LoadingDots() {
  return (
    <div
      className="flex shrink-0 items-center gap-1 pt-1.5"
      aria-hidden="true"
    >
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="h-1.5 w-1.5 animate-bounce rounded-full bg-primary"
          style={{ animationDelay: `${i * 150}ms` }}
        />
      ))}
    </div>
  );
}
