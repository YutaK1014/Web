(() => {
  'use strict';
  const dialog = document.getElementById('pinball');
  if (!dialog || typeof dialog.showModal !== 'function') return;
  const canvas = document.getElementById('pinball-table');
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const scoreLabel = document.getElementById('pinball-score');
  const status = document.getElementById('pinball-status');
  const launchButton = document.getElementById('pinball-launch');
  const buttons = [...dialog.querySelectorAll('[data-flipper]')];
  const command = ['ArrowUp', 'ArrowUp', 'ArrowDown', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'ArrowLeft', 'ArrowRight', 'b', 'a'];
  const codeDialog = document.getElementById('pinball-code');
  const secret = document.getElementById('pinball-secret');
  const progress = document.getElementById('pinball-code-progress');
  let entered = [], score = 0, ball, frame = 0, lastTime = 0, accumulator = 0;
  let previousFocus, scrollY = 0;
  const keys = new Set();
  const pointers = new Map();
  const bumpers = [{ x: 130, y: 180, r: 27 }, { x: 290, y: 180, r: 27 }, { x: 210, y: 290, r: 32 }];
  const rails = [[24, 390, 100, 485], [396, 390, 320, 485]];
  const flippers = [{ x: 100, y: 490, side: 'left', angle: .4 }, { x: 320, y: 490, side: 'right', angle: Math.PI - .4 }];
  const step = 1 / 240;
  const pressed = side => keys.has(side === 'left' ? 'ArrowLeft' : 'ArrowRight') || [...pointers.values()].includes(side);

  function acceptCommand(key) {
    entered.push(key);
    // Retain overlapping prefixes when the user starts again with up arrows.
    while (entered.length && !entered.every((value, index) => command[index] === value)) entered.shift();
    progress.textContent = `入力：${entered.length} / 10`;
    const matched = entered.length > 0;
    if (entered.length === command.length) { entered = []; open(); }
    return matched;
  }
  function showCode() {
    if (document.querySelector('dialog[open]')) return;
    entered = [];
    progress.textContent = '入力：0 / 10';
    codeDialog.showModal();
  }
  let holdTimer = 0, holdPointer = null;
  function cancelHold() {
    clearTimeout(holdTimer);
    holdPointer = null;
  }
  secret.addEventListener('pointerdown', event => {
    cancelHold();
    if (!event.isPrimary || event.button !== 0) return;
    holdPointer = { id: event.pointerId, x: event.clientX, y: event.clientY };
    holdTimer = setTimeout(() => { cancelHold(); showCode(); }, 700);
  });
  window.addEventListener('pointermove', event => {
    if (holdPointer && event.pointerId === holdPointer.id && Math.hypot(event.clientX - holdPointer.x, event.clientY - holdPointer.y) > 12) cancelHold();
  });
  ['pointerup', 'pointercancel'].forEach(type => window.addEventListener(type, cancelHold));
  secret.addEventListener('contextmenu', event => event.preventDefault());
  // Keyboard / assistive-technology activation is also available on the logo button.
  secret.addEventListener('click', event => { if (event.detail === 0) showCode(); });
  window.addEventListener('blur', cancelHold);
  document.addEventListener('visibilitychange', cancelHold);
  codeDialog.querySelectorAll('[data-command]').forEach(button => {
    button.addEventListener('click', () => acceptCommand(button.dataset.command));
  });
  document.getElementById('pinball-code-close').addEventListener('click', () => codeDialog.close());
  codeDialog.addEventListener('close', () => {
    if (!document.querySelector('dialog[open]')) { entered = []; secret.focus({ preventScroll: true }); }
  });

  function clearControls() {
    keys.clear();
    pointers.clear();
    buttons.forEach(button => button.setAttribute('aria-pressed', 'false'));
  }
  function addScore(points) {
    score += points;
    scoreLabel.textContent = score.toLocaleString('ja-JP');
  }
  function ready() {
    ball = { x: 365, y: 350, vx: 0, vy: 0, active: false };
    launchButton.disabled = false;
  }
  function launch() {
    if (!dialog.open || ball.active) return;
    ball.active = true;
    ball.vx = -180;
    ball.vy = -690;
    launchButton.disabled = true;
    status.textContent = 'バンパー100点 ／ 打ち返し10点';
  }
  function open() {
    previousFocus = codeDialog.open ? secret : document.activeElement;
    if (codeDialog.open) codeDialog.close();
    scrollY = window.scrollY;
    score = 0;
    scoreLabel.textContent = '0';
    clearControls();
    ready();
    status.textContent = '発射してスタート！';
    dialog.showModal();
    canvas.focus({ preventScroll: true });
    document.documentElement.classList.add('pinball-open');
    lastTime = 0;
    accumulator = 0;
    frame = requestAnimationFrame(tick);
  }
  dialog.addEventListener('close', () => {
    cancelAnimationFrame(frame);
    clearControls();
    entered = [];
    document.documentElement.classList.remove('pinball-open');
    previousFocus?.focus({ preventScroll: true });
    window.scrollTo({ top: scrollY, behavior: 'instant' });
  });
  document.getElementById('pinball-back').addEventListener('click', () => dialog.close());
  launchButton.addEventListener('click', launch);

  document.addEventListener('keydown', event => {
    if (document.getElementById('highway')?.open) { entered = []; return; }
    if (event.isComposing || event.ctrlKey || event.metaKey || event.altKey) { entered = []; return; }
    if (dialog.open) {
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        event.preventDefault();
        keys.add(event.key);
      } else if (event.code === 'Space' && event.target.tagName !== 'BUTTON') {
        event.preventDefault();
        if (!event.repeat) launch();
      }
      return;
    }
    if (event.target.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"])')) {
      entered = [];
      return;
    }
    if (event.repeat) return;
    if (codeDialog.open && event.target.tagName === 'BUTTON' && (event.key === 'Enter' || event.code === 'Space')) return;
    const key = event.key.length === 1 ? event.key.toLowerCase() : event.key;
    if (acceptCommand(key) && key.startsWith('Arrow')) event.preventDefault();
  });
  document.addEventListener('keyup', event => keys.delete(event.key));
  document.addEventListener('focusin', () => { if (!dialog.open && !codeDialog.open) entered = []; });
  window.addEventListener('blur', clearControls);
  document.addEventListener('visibilitychange', () => { clearControls(); lastTime = 0; accumulator = 0; });
  buttons.forEach(button => {
    button.addEventListener('pointerdown', event => {
      if (event.button !== 0) return;
      event.preventDefault();
      button.setPointerCapture(event.pointerId);
      pointers.set(event.pointerId, button.dataset.flipper);
    });
    ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(type => button.addEventListener(type, event => pointers.delete(event.pointerId)));
    button.addEventListener('keydown', event => {
      if (event.code === 'Space' || event.key === 'Enter') {
        event.preventDefault();
        keys.add(button.dataset.flipper === 'left' ? 'ArrowLeft' : 'ArrowRight');
      }
    });
    button.addEventListener('keyup', event => {
      if (event.code === 'Space' || event.key === 'Enter') keys.delete(button.dataset.flipper === 'left' ? 'ArrowLeft' : 'ArrowRight');
    });
    button.addEventListener('blur', clearControls);
  });

  // Reflect velocity relative to the moving contact surface, then separate the ball.
  function collide(x, y, radius, surfaceX = 0, surfaceY = 0, bounce = .85) {
    const dx = ball.x - x, dy = ball.y - y;
    const distance = Math.hypot(dx, dy), minimum = radius + 8;
    if (distance >= minimum) return false;
    const nx = distance > .001 ? dx / distance : 0;
    const ny = distance > .001 ? dy / distance : -1;
    ball.x = x + nx * (minimum + .1);
    ball.y = y + ny * (minimum + .1);
    const velocity = (ball.vx - surfaceX) * nx + (ball.vy - surfaceY) * ny;
    if (velocity >= 0) return false;
    ball.vx -= (1 + bounce) * velocity * nx;
    ball.vy -= (1 + bounce) * velocity * ny;
    return true;
  }
  function segment(x1, y1, x2, y2, radius, omega = 0) {
    const dx = x2 - x1, dy = y2 - y1;
    const t = Math.max(0, Math.min(1, ((ball.x - x1) * dx + (ball.y - y1) * dy) / (dx * dx + dy * dy)));
    return collide(x1 + t * dx, y1 + t * dy, radius, -omega * t * dy, omega * t * dx);
  }
  function update(dt) {
    if (ball.active) {
      ball.vy += 420 * dt;
      ball.x += ball.vx * dt;
      ball.y += ball.vy * dt;
      if (ball.x < 24) { ball.x = 24; ball.vx = Math.abs(ball.vx) * .9; }
      if (ball.x > 396) { ball.x = 396; ball.vx = -Math.abs(ball.vx) * .9; }
      if (ball.y < 24) { ball.y = 24; ball.vy = Math.abs(ball.vy) * .9; }
      bumpers.forEach(bumper => {
        if (collide(bumper.x, bumper.y, bumper.r, 0, 0, 1.12)) {
          addScore(100);
          bumper.glow = .16;
        }
      });
      rails.forEach(rail => segment(...rail, 6));
    }
    flippers.forEach(flipper => {
      const active = pressed(flipper.side);
      const target = flipper.side === 'left' ? (active ? -.45 : .4) : (active ? Math.PI + .45 : Math.PI - .4);
      const previous = flipper.angle;
      flipper.angle += Math.max(-14 * dt, Math.min(14 * dt, target - previous));
      const x2 = flipper.x + Math.cos(flipper.angle) * 86;
      const y2 = flipper.y + Math.sin(flipper.angle) * 86;
      if (ball.active && segment(flipper.x, flipper.y, x2, y2, 9, (flipper.angle - previous) / dt) && active) addScore(10);
    });
    bumpers.forEach(bumper => { bumper.glow = Math.max(0, (bumper.glow || 0) - dt); });
    const speed = Math.hypot(ball.vx, ball.vy);
    if (speed > 950) { ball.vx *= 950 / speed; ball.vy *= 950 / speed; }
    if (ball.y > 620) {
      ready();
      status.textContent = 'もう一度発射！ スコアはそのまま続けられます。';
    }
  }
  function line(x1, y1, x2, y2, width, color) {
    ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2);
    ctx.lineWidth = width; ctx.strokeStyle = color; ctx.lineCap = 'round'; ctx.stroke();
  }
  function circle(x, y, radius, color) {
    ctx.beginPath(); ctx.arc(x, y, radius, 0, Math.PI * 2); ctx.fillStyle = color; ctx.fill();
  }
  function draw() {
    ctx.clearRect(0, 0, 420, 600);
    ctx.fillStyle = '#10243d'; ctx.fillRect(16, 16, 388, 580);
    for (let y = 50; y < 470; y += 35) for (let x = 40; x < 400; x += 34) circle(x, y, 1, '#2b415c');
    line(16, 590, 16, 16, 4, '#4a7797'); line(16, 16, 404, 16, 4, '#4a7797'); line(404, 16, 404, 590, 4, '#4a7797');
    ctx.textAlign = 'center'; ctx.font = 'bold 21px sans-serif'; ctx.fillStyle = '#67e8db'; ctx.fillText('SECRET PINBALL', 210, 77);
    bumpers.forEach(bumper => {
      circle(bumper.x, bumper.y, bumper.r + 6, '#245c68');
      circle(bumper.x, bumper.y, bumper.r, bumper.glow > 0 ? '#fff3b0' : '#67e8db');
      circle(bumper.x, bumper.y, bumper.r - 8, '#143c50');
      ctx.font = 'bold 15px sans-serif'; ctx.fillStyle = '#fff'; ctx.fillText('100', bumper.x, bumper.y + 5);
    });
    rails.forEach(rail => line(...rail, 12, '#7a9aba'));
    flippers.forEach(flipper => {
      line(flipper.x, flipper.y, flipper.x + Math.cos(flipper.angle) * 86, flipper.y + Math.sin(flipper.angle) * 86, 18, '#ffcc70');
      circle(flipper.x, flipper.y, 5, '#94582f');
    });
    ctx.font = '13px sans-serif'; ctx.fillStyle = '#9ab0ca'; ctx.fillText('KEEP THE BALL ALIVE', 210, 575);
    circle(ball.x, ball.y, 8, '#eaf4ff'); circle(ball.x - 2, ball.y - 3, 3, '#fff');
    buttons.forEach(button => button.setAttribute('aria-pressed', String(pressed(button.dataset.flipper))));
  }
  function tick(time) {
    if (!dialog.open) return;
    if (!document.hidden) {
      if (lastTime) accumulator += Math.min((time - lastTime) / 1000, .05);
      while (accumulator >= step) { update(step); accumulator -= step; }
      draw();
    }
    lastTime = time;
    frame = requestAnimationFrame(tick);
  }
})();
