$ErrorActionPreference='Stop'
$root=Join-Path (Get-Location) ('verification/render-games-'+[guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Force -Path "$root/main/resources/templates","$root/main/resources/static/js","$root/main/resources/static/css","$root/test/js" | Out-Null
$base='https://mouke-memo-demo.onrender.com'
$html=(Invoke-WebRequest -UseBasicParsing -Uri "$base/" -TimeoutSec 90).Content
# Tests control the animation clock and load each deployed script exactly once.
$html=[regex]::Replace($html,'(?is)<script\b[^>]*>.*?</script>','')
[IO.File]::WriteAllText("$root/main/resources/templates/home.html",$html,[Text.Encoding]::UTF8)
foreach($name in @('pinball-rewards','pinball','highway-engine','highway')) {
    Invoke-WebRequest -UseBasicParsing -Uri "$base/js/$name.js" -OutFile "$root/main/resources/static/js/$name.js" -TimeoutSec 60
}
foreach($game in @('pinball','highway')) {
    Invoke-WebRequest -UseBasicParsing -Uri "$base/css/$game.css" -OutFile "$root/main/resources/static/css/$game.css" -TimeoutSec 60
    Copy-Item -LiteralPath "src/test/js/$game-tests.html" -Destination "$root/test/js/$game-tests.html"
    $url=([uri]"$root/test/js/$game-tests.html").AbsoluteUri
    $args=@('--headless','--disable-gpu','--no-first-run','--no-default-browser-check','--allow-file-access-from-files','--disable-background-networking','--virtual-time-budget=30000','--dump-dom',"--user-data-dir=$root/profile-$game",$url)
    $process=Start-Process -FilePath 'C:/Program Files/Google/Chrome/Application/chrome.exe' -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardOutput "$root/$game-dom.html" -RedirectStandardError "$root/$game-chrome.log"
    if(-not $process.WaitForExit(60000)) { Stop-Process -Id $process.Id; throw "$game browser timed out" }
    $dom=Get-Content -LiteralPath "$root/$game-dom.html" -Raw -Encoding UTF8
    $result=[regex]::Match($dom,'(?s)<pre id="results">(.*?)</pre>').Groups[1].Value
    $result=[Net.WebUtility]::HtmlDecode($result)
    [IO.File]::WriteAllText("$root/$game-results.txt",$result,[Text.Encoding]::UTF8)
    Write-Output "$game results: $root/$game-results.txt"
    Write-Output $result
}
