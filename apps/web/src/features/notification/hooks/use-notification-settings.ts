'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getNotificationSettings, upsertNotificationSetting } from '@/lib/api/notifications';
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
