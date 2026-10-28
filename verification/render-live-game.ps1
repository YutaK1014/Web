$ErrorActionPreference='Stop'
$root=Join-Path (Get-Location) ('verification/live-game-'+[guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $root | Out-Null
$port=19328
$process=Start-Process -FilePath 'C:/Program Files/Google/Chrome/Application/chrome.exe' -WindowStyle Hidden -PassThru -ArgumentList @('--headless','--disable-gpu','--no-first-run',"--remote-debugging-port=$port","--user-data-dir=$root/profile",'about:blank')
$socket=New-Object Net.WebSockets.ClientWebSocket
$script:seq=0
$script:events=New-Object System.Collections.Generic.List[object]
function Cdp($method,$params) {
    $script:seq++
    $id=$script:seq
    $payload=@{id=$id;method=$method;params=$params}|ConvertTo-Json -Depth 15 -Compress
    $bytes=[Text.Encoding]::UTF8.GetBytes($payload)
    $socket.SendAsync([ArraySegment[byte]]::new($bytes),[Net.WebSockets.WebSocketMessageType]::Text,$true,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
    do {
        $stream=New-Object IO.MemoryStream
        do {
            $buffer=New-Object byte[] 65536
            $cts=New-Object Threading.CancellationTokenSource 60000
            $chunk=$socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$cts.Token).GetAwaiter().GetResult()
            $stream.Write($buffer,0,$chunk.Count)
            $cts.Dispose()
        } while(-not $chunk.EndOfMessage)
        $message=[Text.Encoding]::UTF8.GetString($stream.ToArray())|ConvertFrom-Json
        if($message.method) {$script:events.Add($message)}
    } while($message.id -ne $id)
    if($message.error){throw ($message.error|ConvertTo-Json -Compress)}
    return $message.result
}
try {
    for($i=0;$i -lt 30;$i++) {
        try {$tabs=Invoke-RestMethod "http://127.0.0.1:$port/json";break} catch {Start-Sleep -Milliseconds 200}
    }
    $tab=$tabs|Where-Object type -eq 'page'|Select-Object -First 1
    $socket.ConnectAsync([uri]$tab.webSocketDebuggerUrl,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
    $null=Cdp 'Runtime.enable' @{}
    $null=Cdp 'Page.enable' @{}
    $null=Cdp 'Page.navigate' @{url='https://mouke-memo-demo.onrender.com/'}
    for($i=0;$i -lt 90;$i++) {
        $state=Cdp 'Runtime.evaluate' @{expression="document.readyState === 'complete' && !!document.getElementById('pinball')";returnByValue=$true}
        if($state.result.value){break}
        Start-Sleep -Milliseconds 500
    }
    $before=Cdp 'Runtime.evaluate' @{expression="JSON.stringify({url:location.href,ready:document.readyState,rewards:typeof PinballRewards,active:document.activeElement?.tagName,scripts:[...document.scripts].map(s=>s.src)})";returnByValue=$true}
    Write-Output $before.result.value
    foreach($key in @('ArrowUp','ArrowUp','ArrowDown','ArrowDown','ArrowLeft','ArrowRight','ArrowLeft','ArrowRight','b','a')) {
        $keyCode=if($key -eq 'b'){'KeyB'}elseif($key -eq 'a'){'KeyA'}else{$key}
        $null=Cdp 'Input.dispatchKeyEvent' @{type='keyDown';key=$key;code=$keyCode}
        $null=Cdp 'Input.dispatchKeyEvent' @{type='keyUp';key=$key;code=$keyCode}
    }
    $after=Cdp 'Runtime.evaluate' @{expression="JSON.stringify({open:document.getElementById('pinball')?.open,progress:document.getElementById('pinball-code-progress')?.textContent,active:document.activeElement?.id})";returnByValue=$true}
    Write-Output $after.result.value
    $launch=Cdp 'Runtime.evaluate' @{expression="document.getElementById('pinball-launch')?.click(); JSON.stringify({active:document.getElementById('pinball-launch')?.disabled,status:document.getElementById('pinball-status')?.textContent})";returnByValue=$true}
    Write-Output $launch.result.value
    $errors=@($script:events|Where-Object method -eq 'Runtime.exceptionThrown')
    Write-Output ('Runtime errors: '+($errors|ConvertTo-Json -Depth 15 -Compress))
    @($before.result.value,$after.result.value,$launch.result.value,($errors|ConvertTo-Json -Depth 15)) | Set-Content "$root/results.txt" -Encoding UTF8
    Write-Output "Results: $root/results.txt"
    $null=Cdp 'Browser.close' @{}
} finally { $socket.Dispose() }
