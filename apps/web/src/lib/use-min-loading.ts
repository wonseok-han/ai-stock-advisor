import { useEffect, useReducer } from 'react';

const MIN_DURATION = 600;

type State = { visible: boolean; startedAt: number };
type Action = { type: 'START' } | { type: 'STOP' } | { type: 'DELAYED_STOP' };

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'START':
      return { visible: true, startedAt: Date.now() };
    case 'STOP':
    case 'DELAYED_STOP':
      return { visible: false, startedAt: 0 };
    default:
      return state;
  }
}

export function useMinLoading(isLoading: boolean): boolean {
  const [state, dispatch] = useReducer(reducer, { visible: isLoading, startedAt: 0 });

  useEffect(() => {
    if (isLoading && !state.visible) {
      dispatch({ type: 'START' });
    } else if (!isLoading && state.visible) {
      const elapsed = Date.now() - state.startedAt;
      const remaining = MIN_DURATION - elapsed;
      if (remaining > 0) {
        const id = setTimeout(() => dispatch({ type: 'DELAYED_STOP' }), remaining);
        return () => clearTimeout(id);
      }
      dispatch({ type: 'STOP' });
    }
  }, [isLoading, state.visible, state.startedAt]);

  return state.visible;
}
