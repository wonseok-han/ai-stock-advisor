export interface NotificationSetting {
  ticker: string;
  priceChangeThreshold: number | null;
  onNewNews: boolean;
  onSignalChange: boolean;
  enabled: boolean;
}

export interface NotificationSettingRequest {
  priceChangeThreshold: number | null;
  onNewNews: boolean;
  onSignalChange: boolean;
  enabled: boolean;
}
