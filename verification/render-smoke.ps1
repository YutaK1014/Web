$ErrorActionPreference = 'Stop'
$base = 'https://mouke-memo-demo.onrender.com'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$run = 'render-test-' + [guid]::NewGuid().ToString('N').Substring(0,10)
$results = New-Object System.Collections.Generic.List[string]
$saleId = $null
$templateId = $null
function Check($name, $ok) {
    if (-not $ok) { throw "FAIL: $name" }
    $results.Add("PASS: $name")
    Write-Output "PASS: $name"
}
function Request($path, $data = $null) {
    $args = @{Uri="$base$path"; UseBasicParsing=$true; WebSession=$session; TimeoutSec=60}
    if ($null -ne $data) { $args.Method='POST'; $args.Body=$data; $args.ContentType='application/x-www-form-urlencoded; charset=utf-8' }
    Invoke-WebRequest @args
}
function Hidden($html) {
    $data = @{}
    foreach ($inputTag in [regex]::Matches($html, '<input\b[^>]*>')) {
        $attrs = @{}
        foreach ($a in [regex]::Matches($inputTag.Value, '([\w-]+)="([^"]*)"')) { $attrs[$a.Groups[1].Value]=[System.Net.WebUtility]::HtmlDecode($a.Groups[2].Value) }
        if ($attrs.type -eq 'hidden' -and $attrs.name) { $data[$attrs.name]=$attrs.value }
    }
    return $data
}
function SaleData($html, $price) {
    $data = Hidden $html
    $data.itemName=$run; $data.marketplace='その他'; $data.customMarketplace='Render Test'; $data.feeRate='10'
    $data.sellingPrice=$price; $data.shippingCost='200'; $data.purchasePrice='500'; $data.soldDate='2026-10-28'
    $data.tags=$run; $data.memo='Temporary automated deployment verification'
    return $data
}
try {
    $health=Request '/healthz'
    Check 'Health and database UP' ($health.StatusCode -eq 200 -and ($health.Content | ConvertFrom-Json).status -eq 'UP')
    foreach ($path in @('/','/sales/register','/transactions','/dashboard','/dashboard?month=2026-09','/reports','/reports/monthly?month=2026-10','/reports/monthly?month=2000-01','/settings','/settings/shipping-templates','/discount-simulator')) {
        $page=Request $path
        Check "GET $path renders HTML" ($page.StatusCode -eq 200 -and $page.Content -match '<html' -and $page.Content -notmatch 'Whitelabel Error')
    }
    $page=Request '/'
    $assets=[regex]::Matches($page.Content,'(?:src|href)="(/(?:css|js)/[^"?]+)(?:\?[^"]*)?"') | ForEach-Object {$_.Groups[1].Value} | Select-Object -Unique
    foreach ($asset in $assets) { $r=Request $asset; Check "Asset $asset" ($r.StatusCode -eq 200) }
    $page=Request '/sales/register'
    $data=SaleData $page.Content '1000'
    $invalid=$data.Clone(); $invalid.itemName=''
    $r=Request '/transactions' $invalid
    Check 'Empty item name rejected' ($r.Content -match '商品名は1〜100文字')
    $r=Request '/transactions' $data
    Check 'Sale registration' ($r.Content.Contains($run) -and $r.Content -match '取引を登録しました')
    $r=Request "/transactions?keyword=$run"
    $match=[regex]::Match($r.Content,'/transactions/(\d+)/edit')
    Check 'Search locates created sale' $match.Success
    $saleId=$match.Groups[1].Value
    $r=Request "/transactions/export?keyword=$run"
    Check 'CSV profit 200 and tag' ($r.Content.Contains($run) -and $r.Content -match ',1000,10(?:\.0+)?,100,200,500,200,2026-10-28,')
    $r=Request "/transactions?tag=$run&month=2026-10"
    Check 'Tag and month filter' ($r.Content.Contains("/transactions/$saleId/edit"))
    $r=Request "/transactions?keyword=$run&month=2026-09"
    Check 'Other month excludes sale' (-not $r.Content.Contains("/transactions/$saleId/edit"))
    $page=Request "/transactions/$saleId/edit"
    $r=Request "/transactions/$saleId/edit" (SaleData $page.Content '2000')
    Check 'Sale edit' ($r.Content -match '取引を更新しました')
    $r=Request "/transactions/export?keyword=$run"
    Check 'Edited CSV profit 1100' ($r.Content -match ',2000,10(?:\.0+)?,200,200,500,1100,2026-10-28,')
    $page=Request '/discount-simulator'
    $data=Hidden $page.Content
    $data.sellingPrice='2000'; $data.marketplace='その他'; $data.feeRate='10'; $data.shippingCost='200'; $data.purchasePrice='500'; $data.otherCost='0'; $data.desiredProfit='200'
    $r=Request '/discount-simulator' $data
    Check 'Discount calculation result and current profit' ($r.Content -match '計算結果' -and $r.Content -match '1,100円')
    foreach ($iteration in @(1,2)) {
        $page=Request "/transactions/$saleId/return"
        $data=Hidden $page.Content; $data.returnCost='300'
        $r=Request "/transactions/$saleId/return" $data
        $r=Request "/transactions/export?keyword=$run"
        Check "Return cost stays -300 (save $iteration)" ($r.Content -match ',0,10(?:\.0+)?,0,0,0,-300,2026-10-28,' -and $r.Content -match '"返品",300')
    }
    $page=Request '/settings/shipping-templates'
    $data=Hidden $page.Content; $data.name=$run; $data.shippingMethod='Test'; $data.packaging='Test'; $data.shippingCost='200'; $data.packagingCost='50'
    $r=Request '/settings/shipping-templates' $data
    $article=[regex]::Match($r.Content,'(?s)<article\b[^>]*>(?:(?!</article>).)*'+[regex]::Escape($run)+'(?:(?!</article>).)*</article>')
    $match=[regex]::Match($article.Value,'/settings/shipping-templates/(\d+)/edit')
    Check 'Shipping template create total 250' ($match.Success -and $article.Value -match '250円')
    $templateId=$match.Groups[1].Value
    $page=Request "/settings/shipping-templates/$templateId/edit"
    $data=Hidden $page.Content; $data.name=$run; $data.shippingMethod='Test'; $data.packaging='Test'; $data.shippingCost='300'; $data.packagingCost='50'
    $r=Request "/settings/shipping-templates/$templateId/edit" $data
    $page=Request '/sales/register'
    Check 'Edited template available to sale form with total 350' ($page.Content -match ('(?s)<option[^>]*value="'+$templateId+'"[^>]*data-total="350"'))
} catch {
    $results.Add("FAIL: $($_.Exception.Message)")
    Write-Output "FAIL: $($_.Exception.Message)"
} finally {
    foreach ($entry in @(@{id=$saleId;root='/transactions'},@{id=$templateId;root='/settings/shipping-templates'})) {
        if ($entry.id) {
            try {
                $path=$entry.root+'/'+$entry.id+'/delete'
                $page=Request $path
                if (-not $page.Content.Contains($run)) { throw 'Cleanup ownership check failed' }
                $r=Request $path (Hidden $page.Content)
                Check "Cleanup $($entry.root)/$($entry.id)" ($r.Content -match '削除しました')
            } catch { $results.Add("CLEANUP FAILED: $($entry.root)/$($entry.id): $($_.Exception.Message)") }
        }
    }
    $results.Insert(0,"Target: $base; Run: $run; UTC: $([DateTime]::UtcNow.ToString('s'))")
    $results.Add('NOT TESTED: browser JavaScript, drafts, visual/mobile layout, PDF rendering, image upload, purchase writes, settings writes, restart behavior.')
    $results | Set-Content -LiteralPath 'verification/render-smoke-results.txt' -Encoding UTF8
}
if ($results -match 'FAIL') { exit 1 }
