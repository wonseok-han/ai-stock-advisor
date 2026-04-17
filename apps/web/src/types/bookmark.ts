export interface Bookmark {
  ticker: string;
  name: string;
  price: number | null;
  changePercent: number | null;
  createdAt: string;
}

export interface BookmarkListResponse {
  bookmarks: Bookmark[];
  total: number;
}

export interface BookmarkCheckResponse {
  bookmarked: boolean;
}
