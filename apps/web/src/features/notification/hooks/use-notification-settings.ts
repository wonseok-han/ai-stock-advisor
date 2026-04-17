'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  deleteNotificationSetting,
  getNotificationSettings,
  upsertNotificationSetting,
} from '@/lib/api/notifications';
import { useAuth } from '@/features/auth/auth-provider';

import type { NotificationSetting, NotificationSettingRequest } from '@/types/notification';

export function useNotificationSettings() {
  const { user } = useAuth();
  return useQuery<NotificationSetting[]>({
    queryKey: ['notification-settings'],
    queryFn: getNotificationSettings,
    enabled: !!user,
  });
}

export function useUpsertNotificationSetting() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ ticker, req }: { ticker: string; req: NotificationSettingRequest }) =>
      upsertNotificationSetting(ticker, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notification-settings'] });
    },
  });
}

export function useDeleteNotificationSetting() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ticker: string) => deleteNotificationSetting(ticker),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notification-settings'] });
    },
  });
}
