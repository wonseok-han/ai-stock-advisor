import Link from 'next/link';

export function SiteHeader() {
  return (
    <header className="border-b border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
      <div className="mx-auto flex w-full max-w-5xl items-center px-4 py-3 sm:px-6">
        <Link
          href="/"
          className="text-sm font-semibold text-zinc-900 hover:text-zinc-600 dark:text-zinc-50 dark:hover:text-zinc-300"
        >
          AI Stock Advisor
        </Link>
      </div>
    </header>
  );
}
