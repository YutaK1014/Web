(() => {
  'use strict';
  const dialog = document.getElementById('highway');
  if (!dialog || !window.HighwayGame || typeof dialog.showModal !== 'function') return;
  const canvas = document.getElementById('highway-road');
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const { Run, route } = window.HighwayGame;
  const run = new Run();
  const total = route.at(-1).km;
  const distance = document.getElementById('highway-distance');
  const remaining = document.getElementById('highway-remaining');
  const progress = document.getElementById('highway-progress');
  const next = document.getElementById('highway-next');
  const level = document.getElementById('highway-level');
  const lap = document.getElementById('highway-lap');
  const status = document.getElementById('highway-status');
  const start = document.getElementById('highway-start');
  const pause = document.getElementById('highway-pause');
  const left = document.getElementById('highway-left');
  const right = document.getElementById('highway-right');
  const codeDialog = document.getElementById('pinball-code');
  const input = document.getElementById('highway-code-input');
  const codeStatus = document.getElementById('highway-code-status');
  let typed = '', frame = 0, lastTime = null, lastPhase = '', lastCheckpoint = -1, lastDifficulty = 0, lastLaps = 0, previousFocus, scrollY;
  const rows = route.map((stop, index) => {
    const row = document.createElement('li');
    row.textContent = `${index === 0 ? 'START ' : index === route.length - 1 ? 'GOAL ' : ''}${stop.name} · ${stop.region} · ${stop.km.toLocaleString('ja-JP')} km`;
    document.getElementById('highway-route').append(row);
    return row;
  });
  progress.max = total;
  const km = value => `${Math.floor(value).toLocaleString('ja-JP')} km`;

  function refresh() {
    distance.textContent = km(run.distance);
    remaining.textContent = km(Math.ceil(total - run.lapDistance));
    progress.value = run.lapDistance;
    lap.textContent = `${run.completedLaps + 1}周目 · ${run.completedLaps}周完走`;
    level.textContent = run.completedLaps > 0 ? '難易度 Lv.14 固定 · エンドレス走行中' : `難易度 Lv.${run.difficulty} ／ 14 · 地方を通過するごとにアップ`;
    const lapChanged = lastLaps !== run.completedLaps;
    const checkpointChanged = lastCheckpoint !== run.checkpoint || lapChanged;
    if (checkpointChanged) {
      rows.forEach((row, index) => {
        row.dataset.passed = String(index <= run.checkpoint);
        if (index === run.checkpoint) row.setAttribute('aria-current', 'step');
        else row.removeAttribute('aria-current');
      });
    }
    const destination = route[run.checkpoint + 1];
    next.textContent = `${route[run.checkpoint].region} ｜ 次：${destination.name} まで ${km(Math.ceil(destination.km - run.lapDistance))}`;
    if (lastPhase !== run.phase || checkpointChanged) {
      if (run.phase === 'ready') status.textContent = '東京から日本一周へ。出発ボタンでスタート。';
      if (run.phase === 'running') status.textContent = run.checkpoint === 0 ? '東京ICを出発。対向車をよけて日本一周を目指そう！' : `${route[run.checkpoint].name} 通過！ 次は${destination.name}。`;
      if (run.phase === 'running' && !lapChanged && lastDifficulty > 0 && run.difficulty > lastDifficulty) status.textContent = `${route[run.checkpoint - 1].region}区間クリア！ ${route[run.checkpoint].region}へ。難易度 Lv.${run.difficulty} にアップ！`;
      if (run.phase === 'running' && lapChanged) status.textContent = `${run.completedLaps === 1 ? '日本一周クリア！' : `${run.completedLaps}周完走！`} 東京に帰還。Lv.14固定で${run.completedLaps + 1}周目へ！`;
      if (run.phase === 'paused') status.textContent = '一時停止中。「再開」で走行を続けます。';
      if (run.phase === 'crashed') status.textContent = `衝突！ 累計 ${km(run.distance)}・${run.completedLaps}周完走。${run.completedLaps + 1}周目の${route[run.checkpoint].name}まで通過。東京から再挑戦できます。`;
    }
    start.disabled = run.phase === 'running' || run.phase === 'paused';
    start.textContent = ({ ready: '出発', running: '走行中', paused: '一時停止中', crashed: '東京から再挑戦' })[run.phase];
    pause.disabled = !['running', 'paused'].includes(run.phase);
    pause.textContent = run.phase === 'paused' ? '再開' : '一時停止';
    left.disabled = right.disabled = run.phase !== 'running';
    lastPhase = run.phase;
    lastCheckpoint = run.checkpoint;
    lastDifficulty = run.difficulty;
    lastLaps = run.completedLaps;
  }
  function open() {
    if (dialog.open || document.getElementById('pinball').open) return;
    previousFocus = codeDialog.open ? document.getElementById('pinball-secret') : document.activeElement;
    scrollY = window.scrollY;
    if (codeDialog.open) codeDialog.close();
    input.value = ''; codeStatus.textContent = ''; typed = '';
    run.reset(); lastPhase = ''; lastCheckpoint = -1; lastDifficulty = 0; lastLaps = 0; lastTime = null;
    dialog.showModal();
    canvas.focus({ preventScroll: true });
    document.documentElement.classList.add('highway-open');
    refresh(); draw();
    frame = requestAnimationFrame(tick);
  }
  dialog.addEventListener('close', () => {
    if (dialog.open) return;
    cancelAnimationFrame(frame); run.pause(); typed = '';
    document.documentElement.classList.remove('highway-open');
    previousFocus?.focus({ preventScroll: true });
    window.scrollTo({ top: scrollY, behavior: 'instant' });
  });
  document.getElementById('highway-back').addEventListener('click', () => dialog.close());
  document.getElementById('highway-code-form').addEventListener('submit', event => {
    event.preventDefault();
    if (input.value.trim().toUpperCase() === 'PAINKILLER') open();
    else codeStatus.textContent = 'コマンドが違います。もう一度入力してください。';
  });
  codeDialog.addEventListener('close', () => { input.value = ''; codeStatus.textContent = ''; typed = ''; });
  start.addEventListener('click', () => { run.start(); lastTime = null; refresh(); canvas.focus({ preventScroll: true }); });
  function togglePause() {
    if (run.phase === 'paused') run.resume(); else run.pause();
    lastTime = null; refresh();
  }
  pause.addEventListener('click', togglePause);
  left.addEventListener('click', () => run.move(-1));
  right.addEventListener('click', () => run.move(1));
  document.addEventListener('keydown', event => {
    if (event.isComposing || event.ctrlKey || event.altKey || event.metaKey) { typed = ''; return; }
    if (dialog.open) {
      if (['ArrowLeft', 'ArrowRight'].includes(event.key)) {
        event.preventDefault();
        if (!event.repeat) run.move(event.key === 'ArrowLeft' ? -1 : 1);
      }
      return;
    }
    if (document.querySelector('dialog[open]') || event.target.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"])')) { typed = ''; return; }
    if (event.repeat) return;
    typed = (typed + event.key.toUpperCase()).slice(-10);
    if (typed === 'PAINKILLER') open();
  });
  document.addEventListener('focusin', () => { typed = ''; });
  function suspend() {
    typed = ''; run.pause(); lastTime = null;
    if (dialog.open) refresh();
  }
  window.addEventListener('blur', suspend);
  document.addEventListener('visibilitychange', () => { if (document.hidden) suspend(); });

  function car(x, y, color, oncoming) {
    ctx.save(); ctx.translate(x, y);
    ctx.fillStyle = '#0005'; ctx.fillRect(-23, -34, 48, 80);
    ctx.fillStyle = '#101923'; ctx.fillRect(-24, -23, 48, 12); ctx.fillRect(-24, 19, 48, 12);
    ctx.fillStyle = color; ctx.fillRect(-20, -37, 40, 74);
    ctx.fillStyle = '#193548'; ctx.fillRect(-15, oncoming ? 8 : -22, 30, 16);
    ctx.fillStyle = '#8aaac1'; ctx.fillRect(-14, oncoming ? -23 : 12, 28, 9);
    ctx.fillStyle = '#fff3bc'; ctx.fillRect(-17, oncoming ? 30 : -36, 9, 5); ctx.fillRect(8, oncoming ? 30 : -36, 9, 5);
    ctx.fillStyle = '#ef766b'; ctx.fillRect(-17, oncoming ? -36 : 31, 8, 4); ctx.fillRect(9, oncoming ? -36 : 31, 8, 4);
    ctx.restore();
  }
  function draw() {
    const region = route[run.checkpoint].region;
    const landscape = region === '北海道' ? '#9dbaa9' : region === '沖縄' ? '#147f8d' : '#21483d';
    ctx.fillStyle = landscape; ctx.fillRect(0, 0, 420, 560);
    ctx.fillStyle = '#283341'; ctx.fillRect(55, 0, 310, 560);
    ctx.fillStyle = '#b6c3bd'; ctx.fillRect(45, 0, 5, 560); ctx.fillRect(370, 0, 5, 560);
    ctx.fillStyle = '#edd392'; ctx.fillRect(61, 0, 3, 560); ctx.fillRect(356, 0, 3, 560);
    const offset = (run.distance * 36) % 90;
    ctx.fillStyle = '#d4dfdf';
    for (let y = offset - 90; y < 560; y += 90) { ctx.fillRect(158, y, 4, 40); ctx.fillRect(258, y, 4, 40); }
    ctx.fillStyle = '#ffffff45'; ctx.font = 'bold 30px sans-serif'; ctx.textAlign = 'center';
    for (const x of [110, 210, 310]) ctx.fillText('↓', x, (run.distance * 36) % 420 + 30);
    run.cars.forEach(vehicle => car(vehicle.x, vehicle.y, vehicle.color, true));
    car(run.x, 460, run.phase === 'crashed' ? '#e16863' : '#78f0cb', false);
    ctx.fillStyle = '#083f31'; ctx.fillRect(83, 12, 254, 43);
    ctx.strokeStyle = '#d5f3e6'; ctx.lineWidth = 1; ctx.strokeRect(87, 16, 246, 35);
    ctx.font = 'bold 14px sans-serif'; ctx.fillStyle = '#fff';
    ctx.fillText(route[Math.min(run.checkpoint + 1, route.length - 1)].name + ' ↑', 210, 39);
    if (run.phase !== 'running') {
      ctx.fillStyle = '#07101dcc'; ctx.fillRect(55, 190, 310, 118);
      ctx.font = 'bold 25px sans-serif'; ctx.fillStyle = run.phase === 'crashed' ? '#ffb5a5' : '#9bf4d8';
      const titles = { ready: 'TOKYO → JAPAN', paused: 'PAUSED', crashed: 'CRASH' };
      ctx.fillText(titles[run.phase], 210, 237);
      ctx.font = '15px sans-serif'; ctx.fillStyle = '#fff';
      ctx.fillText(run.phase === 'ready' ? `${km(total)} の旅へ` : km(run.distance), 210, 274);
    }
  }
  function tick(time) {
    if (!dialog.open) return;
    if (!document.hidden) {
      if (lastTime !== null) run.update(Math.min((time - lastTime) / 1000, .05));
      refresh(); draw();
    }
    lastTime = time;
    frame = requestAnimationFrame(tick);
  }
})();
