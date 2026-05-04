import { useRef, useSyncExternalStore } from 'react';

const MIN_DURATION = 600;

export function useMinLoading(isLoading: boolean): boolean {
  const ref = useRef({
    loading: isLoading,
    start: isLoading ? Date.now() : 0,
    timer: null as ReturnType<typeof setTimeout> | null,
    listeners: new Set<() => void>(),
  });

  const store = ref.current;

  if (isLoading && !store.loading) {
    store.loading = true;
    store.start = Date.now();
  } else if (!isLoading && store.loading) {
    const elapsed = Date.now() - store.start;
    const remaining = MIN_DURATION - elapsed;
    if (remaining > 0) {
      if (!store.timer) {
        store.timer = setTimeout(() => {
          store.loading = false;
          store.timer = null;
          store.listeners.forEach((l) => l());
        }, remaining);
      }
    } else {
      store.loading = false;
    }
  }

  return useSyncExternalStore(
    (cb) => {
      store.listeners.add(cb);
      return () => { store.listeners.delete(cb); };
    },
    () => store.loading,
  );
}
