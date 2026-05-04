import { useEffect, useRef, useState } from 'react';

const MIN_DURATION = 600;

export function useMinLoading(isLoading: boolean): boolean {
  const [show, setShow] = useState(isLoading);
  const startRef = useRef<number | null>(null);

  useEffect(() => {
    if (isLoading) {
      startRef.current = Date.now();
      setShow(true);
    } else if (startRef.current !== null) {
      const elapsed = Date.now() - startRef.current;
      const remaining = MIN_DURATION - elapsed;
      if (remaining > 0) {
        const id = setTimeout(() => setShow(false), remaining);
        return () => clearTimeout(id);
      }
      setShow(false);
    }
  }, [isLoading]);

  return show;
}
