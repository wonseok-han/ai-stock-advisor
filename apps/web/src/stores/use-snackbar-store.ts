import { create } from 'zustand';

interface SnackbarState {
  message: string | null;
  show: (message: string) => void;
  hide: () => void;
}

export const useSnackbarStore = create<SnackbarState>((set) => ({
  message: null,
  show: (message) => set({ message }),
  hide: () => set({ message: null }),
}));
