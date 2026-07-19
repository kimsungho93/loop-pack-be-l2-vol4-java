# Catalog Metrics 실시간 모니터링

## 1. 인프라 실행

프로젝트 루트에서 MySQL, Redis와 Kafka를 실행한다.

```powershell
docker-compose -f .\docker\infra-compose.yml up -d
```

## 2. Commerce Streamer 실행

기존 `loopers` DB를 건드리지 않도록 모니터링 전용 DB를 먼저 만든다.

```powershell
docker-compose -f .\docker\infra-compose.yml exec -T mysql mysql -uroot -proot -e "create database if not exists loopers_metrics_monitor character set utf8mb4 collate utf8mb4_general_ci; grant all privileges on loopers_metrics_monitor.* to 'application'@'%'; flush privileges"
```

Metrics Consumer만 측정하려면 Ranking Consumer를 끄고 전용 DB와 Consumer Group으로 Streamer를 실행한다. `server.port=8082`는 Commerce API와의 포트 충돌을 피하기 위한 값이며 관리 포트는 그대로 `8081`을 사용한다.

```powershell
.\gradlew :apps:commerce-streamer:bootRun --args="--server.port=8082 --datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/loopers_metrics_monitor --spring.jpa.hibernate.ddl-auto=create --commerce.metrics.catalog.topic-name=catalog-metrics-load-events --commerce.metrics.catalog.group-id=catalog-metrics-monitor --commerce.ranking.catalog.auto-startup=false"
```

## 3. Prometheus와 Grafana 실행

```powershell
docker-compose -f .\docker\monitoring-compose.yml up -d
```

- Prometheus Target: http://localhost:9090/targets
- Streamer Metrics: http://localhost:8081/actuator/prometheus
- Kafka UI: http://localhost:9099
- Grafana: http://localhost:3000
- 계정: `admin` / `admin`
- Dashboard: `Loopers / Catalog Metrics Batch`

Grafana Dashboard는 5초마다 자동 갱신된다. Streamer가 실행 중이면 `Streamer Up` 패널이 `1`로 표시된다.

## 4. Kafka 부하 이벤트 발행

다음 명령은 전용 `catalog-metrics-load-events` 토픽에 100개 상품의 조회 이벤트 3,000건을 분산해 발행한다. 모든 eventId는 새 UUID이므로 Metrics Consumer에서 신규 이벤트로 처리된다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\catalog-metrics-load.ps1 -Count 3000 -ProductCount 100
```

Docker와 Kafka 없이 생성될 JSON만 확인하려면 `DryRun`을 사용한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\catalog-metrics-load.ps1 -Count 3 -ProductCount 2 -DryRun
```

Batch 크기별 비교는 같은 조건에서 순서대로 실행한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\catalog-metrics-load.ps1 -Count 100 -ProductCount 100
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\catalog-metrics-load.ps1 -Count 500 -ProductCount 100
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\catalog-metrics-load.ps1 -Count 1000 -ProductCount 100
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\catalog-metrics-load.ps1 -Count 3000 -ProductCount 100
```

## 5. Dashboard 해석

- `Record Throughput`: 초당 처리한 Kafka Record 수
- `Average Batch Size`: 실제 Poll 한 번에 전달된 평균 Record 수
- `Batch Processing Time`: Consumer 처리 시간 p50, p95와 p99
- `New vs Duplicate Events`: 신규 및 중복 이벤트 처리량
- `Aggregation Compression Ratio`: 신규 이벤트 대비 DB UPSERT 그룹 비율. 낮을수록 선집계 효과가 크다.
- `Kafka Poll Rate`: 초당 실행된 Kafka Poll 처리 횟수

Kafka Consumer Lag은 Kafka UI의 `Consumers / catalog-metrics-monitor` 화면에서 실시간으로 확인한다. 현재 공용 Kafka Consumer Factory에는 Kafka Client Micrometer Listener가 연결되어 있지 않아 Lag은 Prometheus에 노출되지 않는다.

`max.poll.records=3000`은 Poll의 상한이다. 이벤트 3,000건을 발행해도 실제 Batch 크기는 더 작을 수 있으므로 `Average Batch Size`와 `catalog.metrics.batch.records`를 기준으로 판단한다.

## 6. 실측 기록

- [Catalog Metrics Batch 3,000건 실측 기록](./catalog-metrics-performance-test.md)

자동화 테스트 결과, Kafka 파티션별 처리량, 최종 Lag, DB 정합성, Grafana 캡처와 해석 시 주의점을 함께 기록했다.
