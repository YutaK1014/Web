(() => {
  'use strict';
  class PinballRewards {
    constructor() { this.reset(); }
    reset() { this.score = 0; this.combo = 0; this.comboTime = 0; this.charge = 0; this.feverTime = 0; }
    get multiplier() { return Math.min(5, 1 + Math.floor(this.combo / 3)) * (this.feverTime > 0 ? 2 : 1); }
    hit(bumper) {
      let feverStarted = false;
      if (bumper) {
        this.combo++; this.comboTime = 4;
        if (this.feverTime <= 0 && ++this.charge >= 6) {
          this.charge = 0; this.feverTime = 12; feverStarted = true;
        }
      }
      const points = (bumper ? 1000 : 100) * this.multiplier;
      this.score += points;
      return { points, feverStarted };
    }
    update(dt) {
      this.comboTime = Math.max(0, this.comboTime - dt);
      if (this.comboTime === 0) this.combo = 0;
      this.feverTime = Math.max(0, this.feverTime - dt);
    }
    drain() { this.combo = 0; this.comboTime = 0; }
  }
  window.PinballRewards = PinballRewards;
})();
