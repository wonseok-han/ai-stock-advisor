# AI Stock Advisor — root task runner
# Wraps apps/web (Next.js + pnpm) and apps/api (Spring Boot + Gradle)

WEB_DIR := apps/web
API_DIR := apps/api

.DEFAULT_GOAL := help
.PHONY: help install \
        dev web-dev api-dev \
        build web-build api-build \
        check web-check api-check \
        lint web-lint \
        test web-test api-test \
        clean web-clean api-clean \
        infra-up infra-down infra-logs infra-clean infra-status \
        cache-keys cache-clear-ai cache-clear

help:
	@echo "AI Stock Advisor — Make targets"
	@echo ""
	@echo "Setup:"
	@echo "  install        Install FE deps (pnpm) + verify BE toolchain"
	@echo ""
	@echo "Dev:"
	@echo "  dev            Run FE + BE concurrently (Ctrl+C stops both)"
	@echo "  web-dev        FE dev server (Next.js)"
	@echo "  api-dev        BE dev server (Spring Boot bootRun)"
	@echo ""
	@echo "Build:"
	@echo "  build          Build FE + BE"
	@echo "  web-build      FE production build"
	@echo "  api-build      BE jar build (skip tests)"
	@echo ""
	@echo "Check:"
	@echo "  check          FE typecheck/lint + BE check"
	@echo "  web-check      FE typecheck + lint"
	@echo "  api-check      BE test + static analysis"
	@echo ""
	@echo "Lint:"
	@echo "  lint           Alias for web-lint"
	@echo "  web-lint       FE lint only"
	@echo ""
	@echo "Test:"
	@echo "  test           FE + BE tests"
	@echo "  web-test       FE tests"
	@echo "  api-test       BE tests"
	@echo ""
	@echo "Clean:"
	@echo "  clean          Remove build artifacts (FE + BE)"
	@echo ""
	@echo "Infra (local Docker: Postgres + Redis):"
	@echo "  infra-up       Start postgres+redis in background"
	@echo "  infra-down     Stop containers (preserve volumes)"
	@echo "  infra-logs     Tail container logs"
	@echo "  infra-status   Show container + healthcheck status"
	@echo "  infra-clean    Stop + remove data volumes (destructive)"
	@echo ""
	@echo "Cache (Redis, local docker):"
	@echo "  cache-keys     List keys (PATTERN='ai:*' to filter; default '*')"
	@echo "  cache-clear-ai Delete AI signal cache (ai:*)"
	@echo "  cache-clear    FLUSHDB on local redis (prompts confirm)"

# ---------- Setup ----------
install:
	cd $(WEB_DIR) && pnpm install --frozen-lockfile
	cd $(API_DIR) && ./gradlew --version

# ---------- Dev ----------
# apps/api/.env.local 이 있으면 자동 로드 (Spring Boot 가 dotenv 를 직접 지원하지 않아 Makefile 에서 처리).
# apps/web 은 Next.js 가 .env.local 을 자동 로드.
API_ENV_LOCAL := $(API_DIR)/.env.local

dev:
	@trap 'kill 0' INT TERM EXIT; \
	( cd $(WEB_DIR) && pnpm dev ) & \
	( if [ -f $(API_ENV_LOCAL) ]; then echo "[api-dev] loading $(API_ENV_LOCAL)"; set -a; . $(API_ENV_LOCAL); set +a; fi; \
	  cd $(API_DIR) && ./gradlew bootRun ) & \
	wait

web-dev:
	cd $(WEB_DIR) && pnpm dev

api-dev:
	@if [ -f $(API_ENV_LOCAL) ]; then \
	  echo "[api-dev] loading $(API_ENV_LOCAL)"; \
	  set -a; . $(API_ENV_LOCAL); set +a; \
	fi; \
	cd $(API_DIR) && ./gradlew bootRun

# ---------- Build ----------
build: web-build api-build

web-build:
	cd $(WEB_DIR) && pnpm build

api-build:
	cd $(API_DIR) && ./gradlew build -x test --no-daemon

# ---------- Check ----------
check: web-check api-check

web-check:
	cd $(WEB_DIR) && pnpm exec tsc --noEmit && pnpm lint

api-check:
	cd $(API_DIR) && ./gradlew check --no-daemon

# ---------- Lint ----------
lint: web-lint

web-lint:
	cd $(WEB_DIR) && pnpm lint

# ---------- Test ----------
test: web-test api-test

web-test:
	cd $(WEB_DIR) && pnpm test --if-present

api-test:
	cd $(API_DIR) && ./gradlew test --no-daemon

# ---------- Clean ----------
clean: web-clean api-clean

web-clean:
	cd $(WEB_DIR) && rm -rf .next out node_modules/.cache

api-clean:
	cd $(API_DIR) && ./gradlew clean --no-daemon

# ---------- Infra (local Docker) ----------
infra-up:
	docker compose up -d
	@echo ""
	@echo "Postgres: localhost:5432  (db=aistockadvisor user=dev pass=dev)"
	@echo "Redis:    localhost:6379"

infra-down:
	docker compose down

infra-logs:
	docker compose logs -f --tail=100

infra-status:
	docker compose ps

infra-clean:
	docker compose down -v

# ---------- Cache (Redis) ----------
# 로컬 docker-compose redis 서비스에 붙어 키를 조회/삭제. 운영(Upstash)에는 영향 없음.
#   make cache-keys                   # 전체 키
#   make cache-keys PATTERN='ai:*'    # AI 시그널만
#   make cache-clear-ai               # ai:* 일괄 삭제
#   make cache-clear                  # FLUSHDB (y/N 확인)
PATTERN ?= *

cache-keys:
	@docker compose exec -T redis redis-cli --scan --pattern '$(PATTERN)'

cache-clear-ai:
	@docker compose exec -T redis sh -c "redis-cli --scan --pattern 'ai:*' | xargs -r redis-cli DEL" \
	  && echo "[cache] AI signal keys cleared (ai:*)"

cache-clear:
	@read -p "전체 Redis DB 를 비웁니다 (로컬 한정). 계속? [y/N] " ans; \
	  if [ "$$ans" = "y" ] || [ "$$ans" = "Y" ]; then \
	    docker compose exec -T redis redis-cli FLUSHDB && echo "[cache] FLUSHDB done"; \
	  else \
	    echo "[cache] cancelled"; \
	  fi
