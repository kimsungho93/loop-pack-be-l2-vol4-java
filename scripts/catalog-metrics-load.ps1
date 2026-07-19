[CmdletBinding()]
param(
    [ValidateRange(1, 100000)]
    [int]$Count = 3000,

    [ValidateRange(1, 100000)]
    [int]$ProductCount = 100,

    [string]$Topic = "catalog-metrics-load-events",

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-CatalogEventJson {
    param(
        [int]$Index,
        [int]$DistinctProductCount
    )

    $productId = [long](100001 + ($Index % $DistinctProductCount))
    $userId = [long](1 + ($Index % 10000))
    $event = [ordered]@{
        eventId = [Guid]::NewGuid().ToString()
        eventType = "PRODUCT_VIEWED"
        aggregateType = "PRODUCT"
        aggregateId = $productId
        payload = [ordered]@{
            productId = $productId
            userId = $userId
            brandId = $null
            delta = $null
            orderId = $null
            quantity = $null
            unitPrice = $null
            totalPrice = $null
        }
        occurredAt = [DateTimeOffset]::Now.ToString("o")
    }

    return $event | ConvertTo-Json -Compress -Depth 4
}

function Get-ProductId {
    param(
        [int]$Index,
        [int]$DistinctProductCount
    )

    return [long](100001 + ($Index % $DistinctProductCount))
}

if ($DryRun) {
    for ($index = 0; $index -lt $Count; $index++) {
        New-CatalogEventJson -Index $index -DistinctProductCount $ProductCount
    }
    exit 0
}

$runningContainers = @(
    & docker ps --filter "name=^/kafka$" --filter "status=running" --format "{{.Names}}"
)
if ($LASTEXITCODE -ne 0 -or -not ($runningContainers -contains "kafka")) {
    throw "The kafka container is not running. Start docker/infra-compose.yml first."
}

& docker exec kafka kafka-topics.sh `
    --bootstrap-server localhost:9092 `
    --create `
    --if-not-exists `
    --topic $Topic `
    --partitions 3 `
    --replication-factor 1
if ($LASTEXITCODE -ne 0) {
    throw "Failed to create or verify Kafka topic '$Topic'."
}

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
0..($Count - 1) |
    ForEach-Object {
        $productId = Get-ProductId -Index $_ -DistinctProductCount $ProductCount
        $json = New-CatalogEventJson -Index $_ -DistinctProductCount $ProductCount
        "$productId|$json"
    } |
    & docker exec -i kafka kafka-console-producer.sh `
        --bootstrap-server localhost:9092 `
        --topic $Topic `
        --property parse.key=true `
        --property "key.separator=|"
$producerExitCode = $LASTEXITCODE
$stopwatch.Stop()

if ($producerExitCode -ne 0) {
    throw "Kafka producer failed with exit code $producerExitCode."
}

Write-Host "Published $Count PRODUCT_VIEWED events across $ProductCount products in $($stopwatch.ElapsedMilliseconds) ms."
