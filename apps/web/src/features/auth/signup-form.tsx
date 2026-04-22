"use client";

import { useState } from "react";

import { createClient } from "@/lib/supabase/client";
import { reactivateAccount } from "@/lib/api/auth";

/**
 * 이메일/비밀번호 회원가입 폼.
 * 탈퇴 계정 재가입 시 복구 플로우 지원.
 */
export function SignupForm() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [result, setResult] = useState<'idle' | 'signup-success' | 'reactivated'>('idle');
  const [showReactivate, setShowReactivate] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setShowReactivate(false);

    if (password !== confirmPassword) {
      setError("비밀번호가 일치하지 않습니다.");
      return;
    }

    setIsLoading(true);

    const supabase = createClient();
    if (!supabase) {
      setError("인증 서비스가 설정되지 않았습니다.");
      setIsLoading(false);
      return;
    }
    const { error: signUpError } = await supabase.auth.signUp({
      email,
      password,
      options: {
        emailRedirectTo: `${window.location.origin}/auth/callback`,
      },
    });

    if (signUpError) {
      if (signUpError.message.toLowerCase().includes("already registered")) {
        setShowReactivate(true);
        setError("이미 등록된 이메일입니다.");
      } else {
        setError(signUpError.message);
      }
      setIsLoading(false);
      return;
    }

    setResult('signup-success');
    setIsLoading(false);
  };

  const handleReactivate = async () => {
    setIsLoading(true);
    setError(null);

    const reactivated = await reactivateAccount(email);
    if (reactivated) {
      setShowReactivate(false);
      setResult('reactivated');
      setError(null);
    } else {
      setShowReactivate(false);
      setError("이미 등록된 이메일입니다. 로그인을 시도해 주세요.");
    }

    setIsLoading(false);
  };

  if (result === 'reactivated') {
    return (
      <div className="rounded-xl bg-emerald-500/10 p-4 text-center">
        <p className="text-sm font-medium text-success">
          계정이 복구되었습니다.
        </p>
        <p className="mt-1 text-xs text-fg-secondary">
          기존 이메일과 비밀번호로 로그인해 주세요.
        </p>
      </div>
    );
  }

  if (result === 'signup-success') {
    return (
      <div className="rounded-xl bg-emerald-500/10 p-4 text-center">
        <p className="text-sm font-medium text-success">
          인증 메일을 발송했습니다.
        </p>
        <p className="mt-1 text-xs text-fg-secondary">
          이메일을 확인하고 인증 링크를 클릭해 주세요.
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label
          htmlFor="signup-email"
          className="block text-sm font-medium text-fg-secondary"
        >
          이메일
        </label>
        <input
          id="signup-email"
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mt-1 block w-full rounded-xl border border-border bg-bg-surface px-3 py-2.5 text-sm outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary"
          placeholder="you@example.com"
        />
      </div>

      <div>
        <label
          htmlFor="signup-password"
          className="block text-sm font-medium text-fg-secondary"
        >
          비밀번호
        </label>
        <input
          id="signup-password"
          type="password"
          required
          minLength={6}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mt-1 block w-full rounded-xl border border-border bg-bg-surface px-3 py-2.5 text-sm outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary"
          placeholder="6자 이상 입력해주세요."
        />
      </div>

      <div>
        <label
          htmlFor="signup-confirm"
          className="block text-sm font-medium text-fg-secondary"
        >
          비밀번호 확인
        </label>
        <input
          id="signup-confirm"
          type="password"
          required
          minLength={6}
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          className="mt-1 block w-full rounded-xl border border-border bg-bg-surface px-3 py-2.5 text-sm outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary"
          placeholder="비밀번호 재입력"
        />
      </div>

      {error && (
        <p className="text-sm text-danger">{error}</p>
      )}

      {showReactivate && (
        <div className="rounded-xl bg-amber-500/10 p-3">
          <p className="text-sm text-fg-secondary">
            이전에 탈퇴한 계정이 있을 수 있습니다.
          </p>
          <button
            type="button"
            onClick={handleReactivate}
            disabled={isLoading}
            className="mt-2 w-full cursor-pointer rounded-xl bg-amber-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-amber-700 disabled:opacity-50"
          >
            {isLoading ? "복구 중..." : "계정 복구하기"}
          </button>
        </div>
      )}

      <button
        type="submit"
        disabled={isLoading}
        className="w-full cursor-pointer rounded-xl bg-primary px-4 py-2.5 text-sm font-medium text-primary-fg transition-colors hover:bg-primary-hover disabled:opacity-50"
      >
        {isLoading ? "처리 중..." : "회원가입"}
      </button>
    </form>
  );
}
