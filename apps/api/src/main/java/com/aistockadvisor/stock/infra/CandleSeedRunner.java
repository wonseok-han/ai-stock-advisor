package com.aistockadvisor.stock.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * candles-seed.csv → candles 테이블 벌크 적재.
 * Profile "seed" 활성화 시에만 실행 (--spring.profiles.active=seed).
 * <p>
 * CSV 형식: ticker,trade_date,open,high,low,close,adj_close,volume
 * classpath:data/candles-seed.csv 에 배치.
 * <p>
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §5.4
 */
@Component
@Profile("seed")
public class CandleSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CandleSeedRunner.class);
    private static final int BATCH_SIZE = 1000;

    private final CandleRepository candleRepo;

    public CandleSeedRunner(CandleRepository candleRepo) {
        this.candleRepo = candleRepo;
    }

    @Override
    public void run(String... args) throws Exception {
        Resource csv = new ClassPathResource("data/candles-seed.csv");
        if (!csv.exists()) {
            log.info("candles-seed.csv not found, skipping seed");
            return;
        }

        log.info("candle seed: starting bulk load...");
        int total = 0;
        List<CandleEntity> batch = new ArrayList<>(BATCH_SIZE);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",");
                if (parts.length < 8) continue;

                try {
                    CandleEntity entity = new CandleEntity(
                            parts[0].trim(),
                            LocalDate.parse(parts[1].trim()),
                            new BigDecimal(parts[2].trim()),
                            new BigDecimal(parts[3].trim()),
                            new BigDecimal(parts[4].trim()),
                            new BigDecimal(parts[5].trim()),
                            new BigDecimal(parts[6].trim()),
                            Long.parseLong(parts[7].trim())
                    );
                    batch.add(entity);

                    if (batch.size() >= BATCH_SIZE) {
                        candleRepo.saveAll(batch);
                        total += batch.size();
                        batch.clear();
                    }
                } catch (Exception ex) {
                    log.debug("candle seed skip line: {} ({})", line, ex.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                candleRepo.saveAll(batch);
                total += batch.size();
            }
        }

        log.info("candle seed: loaded {} rows", total);
    }
}
