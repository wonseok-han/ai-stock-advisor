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
      <div className="rounded-md border border-green-200 bg-green-50 p-4 text-center dark:border-green-800 dark:bg-green-950">
        <p className="text-sm font-medium text-green-800 dark:text-green-200">
          계정이 복구되었습니다.
        </p>
        <p className="mt-1 text-xs text-green-600 dark:text-green-400">
          기존 이메일과 비밀번호로 로그인해 주세요.
        </p>
      </div>
    );
  }

  if (result === 'signup-success') {
    return (
      <div className="rounded-md border border-green-200 bg-green-50 p-4 text-center dark:border-green-800 dark:bg-green-950">
        <p className="text-sm font-medium text-green-800 dark:text-green-200">
          인증 메일을 발송했습니다.
        </p>
        <p className="mt-1 text-xs text-green-600 dark:text-green-400">
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
          className="block text-sm font-medium text-zinc-700 dark:text-zinc-300"
        >
          이메일
        </label>
        <input
          id="signup-email"
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
          placeholder="you@example.com"
        />
      </div>

      <div>
        <label
          htmlFor="signup-password"
          className="block text-sm font-medium text-zinc-700 dark:text-zinc-300"
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
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
          placeholder="6자 이상 입력해주세요."
        />
      </div>

      <div>
        <label
          htmlFor="signup-confirm"
          className="block text-sm font-medium text-zinc-700 dark:text-zinc-300"
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
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
          placeholder="비밀번호 재입력"
        />
      </div>

      {error && (
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
      )}

      {showReactivate && (
        <div className="rounded-md border border-amber-200 bg-amber-50 p-3 dark:border-amber-800 dark:bg-amber-950">
          <p className="text-sm text-amber-800 dark:text-amber-200">
            이전에 탈퇴한 계정이 있을 수 있습니다.
          </p>
          <button
            type="button"
            onClick={handleReactivate}
            disabled={isLoading}
            className="mt-2 w-full rounded-md bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
          >
            {isLoading ? "복구 중..." : "계정 복구하기"}
          </button>
        </div>
      )}

      <button
        type="submit"
        disabled={isLoading}
        className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600"
      >
        {isLoading ? "처리 중..." : "회원가입"}
      </button>
    </form>
  );
}
