$ErrorActionPreference='Stop'
$root=Join-Path (Get-Location) ('verification/mechanics-'+[guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory $root | Out-Null
$html=Get-Content src/main/resources/templates/home.html -Raw -Encoding UTF8
$html=[regex]::Replace($html,'(?is)<script\b[^>]*>.*?</script>','')
[IO.File]::WriteAllText("$root/home.html",$html,[Text.Encoding]::UTF8)
Copy-Item src/main/resources/static/js/pinball-rewards.js "$root/pinball-rewards.js"
Copy-Item verification/pinball-mechanics.html "$root/test.html"
$js=Get-Content src/main/resources/static/js/pinball.js -Raw -Encoding UTF8
# State access exists only in this disposable test copy, never in production.
$hook='window.__tableTest={open,launch,update,draw,ball:v=>ball=v,expireSave:()=>{saveTime=0;saveAvailable=false;},targetsReset:()=>targets.forEach(t=>{t.lit=false;t.cooldown=0;}),state:()=>({ball:{...ball},saveTime,saveAvailable,score:rewards.score,targets:targets.map(t=>({...t}))})};'
$js=$js.Insert($js.LastIndexOf('})();'),$hook)
[IO.File]::WriteAllText("$root/pinball.js",$js,[Text.Encoding]::UTF8)
$args=@('--headless','--disable-gpu','--no-first-run','--allow-file-access-from-files','--virtual-time-budget=10000','--dump-dom',"--user-data-dir=$root/profile",([uri]"$root/test.html").AbsoluteUri)
$p=Start-Process 'C:/Program Files/Google/Chrome/Application/chrome.exe' -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardOutput "$root/dom.html" -RedirectStandardError "$root/chrome.log"
if(-not $p.WaitForExit(60000)){Stop-Process -Id $p.Id;throw 'Timed out'}
$dom=Get-Content "$root/dom.html" -Raw -Encoding UTF8
$result=[Net.WebUtility]::HtmlDecode([regex]::Match($dom,'(?s)<pre id="results">(.*?)</pre>').Groups[1].Value)
$result | Set-Content "$root/results.txt" -Encoding UTF8
$result
if($result -notmatch '^ALL PASSED'){exit 1}
