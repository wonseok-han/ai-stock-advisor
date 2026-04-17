package com.aistockadvisor.auth.service;

import com.aistockadvisor.auth.infra.DeletedAccountEntity;
import com.aistockadvisor.auth.infra.DeletedAccountRepository;
import com.aistockadvisor.bookmark.infra.BookmarkRepository;
import com.aistockadvisor.notification.infra.NotificationSettingRepository;
import com.aistockadvisor.notification.infra.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * 회원 탈퇴 (soft delete).
 * 1) deleted_accounts 에 기록
 * 2) 로컬 사용자 데이터 삭제 (bookmarks, notification_settings, push_subscriptions)
 * 3) Supabase Auth 유저 ban 처리 (로그인 차단, 데이터는 보관)
 *
 * 개인정보 보관 정책(2년)에 따라 Supabase Auth 유저 실제 삭제는 별도 배치로 처리.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final DeletedAccountRepository deletedAccountRepo;
    private final BookmarkRepository bookmarkRepo;
    private final NotificationSettingRepository notificationSettingRepo;
    private final PushSubscriptionRepository pushSubscriptionRepo;
    private final RestClient restClient;
    private final String supabaseUrl;
    private final String serviceRoleKey;

    public AccountService(
            DeletedAccountRepository deletedAccountRepo,
            BookmarkRepository bookmarkRepo,
            NotificationSettingRepository notificationSettingRepo,
            PushSubscriptionRepository pushSubscriptionRepo,
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.service-role-key:}") String serviceRoleKey) {
        this.deletedAccountRepo = deletedAccountRepo;
        this.bookmarkRepo = bookmarkRepo;
        this.notificationSettingRepo = notificationSettingRepo;
        this.pushSubscriptionRepo = pushSubscriptionRepo;
        this.supabaseUrl = supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
        this.restClient = RestClient.create();
    }

    @Transactional
    public void deleteAccount(UUID userId, String email, String reason) {
        // 1. 탈퇴 기록
        deletedAccountRepo.save(new DeletedAccountEntity(userId, email, reason));

        // 2. 로컬 데이터 삭제
        pushSubscriptionRepo.deleteByUserId(userId);
        notificationSettingRepo.deleteByUserId(userId);
        bookmarkRepo.deleteByUserId(userId);
        log.info("account soft-delete: local data removed for user {}", userId);

        // 3. Supabase Auth 유저 ban (로그인 차단)
        banSupabaseUser(userId);
    }

    /**
     * 탈퇴 계정 복구: Supabase ban 해제 + deleted_accounts 레코드 삭제.
     * @return true if reactivated, false if email not found in deleted_accounts.
     */
    @Transactional
    public boolean reactivateAccount(String email) {
        return deletedAccountRepo.findByEmail(email)
                .map(entity -> {
                    unbanSupabaseUser(entity.getUserId());
                    deletedAccountRepo.delete(entity);
                    log.info("account reactivated for email {}", email);
                    return true;
                })
                .orElse(false);
    }

    private void unbanSupabaseUser(UUID userId) {
        if (supabaseUrl.isBlank() || serviceRoleKey.isBlank()) {
            log.warn("account reactivate: Supabase credentials not configured, skipping unban");
            return;
        }

        try {
            restClient.put()
                    .uri(supabaseUrl + "/auth/v1/admin/users/{id}", userId)
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", "application/json")
                    .body("{\"ban_duration\":\"none\"}")
                    .retrieve()
                    .toBodilessEntity();
            log.info("account reactivate: Supabase user {} unbanned", userId);
        } catch (Exception ex) {
            log.error("account reactivate: Supabase unban failed for user {}: {}", userId, ex.getMessage());
        }
    }

    private void banSupabaseUser(UUID userId) {
        if (supabaseUrl.isBlank() || serviceRoleKey.isBlank()) {
            log.warn("account delete: Supabase credentials not configured, skipping ban");
            return;
        }

        try {
            restClient.put()
                    .uri(supabaseUrl + "/auth/v1/admin/users/{id}", userId)
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", "application/json")
                    .body("{\"ban_duration\":\"876000h\"}")
                    .retrieve()
                    .toBodilessEntity();
            log.info("account soft-delete: Supabase user {} banned", userId);
        } catch (Exception ex) {
            log.error("account soft-delete: Supabase ban failed for user {}: {}", userId, ex.getMessage());
        }
    }
}
