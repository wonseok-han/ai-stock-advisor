'use client';

import { useState } from 'react';
import {
  useFloating,
  autoUpdate,
  offset,
  flip,
  shift,
  useHover,
  useFocus,
  useDismiss,
  useRole,
  useInteractions,
  FloatingPortal,
} from '@floating-ui/react';
import { cn } from '@/lib/cn';

export function InfoTooltip({ text, className }: { text: string; className?: string }) {
  const [isOpen, setIsOpen] = useState(false);

  const { refs, floatingStyles, context } = useFloating({
    open: isOpen,
    onOpenChange: setIsOpen,
    placement: 'bottom-end',
    whileElementsMounted: autoUpdate,
    middleware: [
      offset(6),
      flip({ fallbackPlacements: ['top-end', 'bottom-start', 'top-start'] }),
      shift({ padding: 8 }),
    ],
  });

  const hover = useHover(context, { move: false });
  const focus = useFocus(context);
  const dismiss = useDismiss(context);
  const role = useRole(context, { role: 'tooltip' });

  const { getReferenceProps, getFloatingProps } = useInteractions([
    hover,
    focus,
    dismiss,
    role,
  ]);

  return (
    <>
      <button
        type="button"
        ref={refs.setReference}
        aria-label={text}
        className={cn(
          'cursor-help rounded-full p-0.5 text-fg-muted outline-none hover:text-fg-secondary focus-visible:ring-2 focus-visible:ring-primary',
          className,
        )}
        {...getReferenceProps()}
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 16 16"
          className="h-3.5 w-3.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
        >
          <circle cx="8" cy="8" r="6.5" />
          <path d="M8 7.25v3.5" strokeLinecap="round" />
          <circle cx="8" cy="5.25" r="0.6" fill="currentColor" stroke="none" />
        </svg>
      </button>
      {isOpen && (
        <FloatingPortal>
          <div
            ref={refs.setFloating}
            style={floatingStyles}
            className="z-50 w-72 max-w-[calc(100vw-2rem)] whitespace-normal break-keep rounded-xl border border-border bg-bg-surface px-3 py-2 text-xs leading-relaxed text-fg-secondary shadow-lg"
            {...getFloatingProps()}
          >
            {text}
          </div>
        </FloatingPortal>
      )}
    </>
  );
}
