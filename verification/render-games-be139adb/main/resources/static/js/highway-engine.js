(() => {
  'use strict';
  // Arcade distances and links, not a navigable real-world driving itinerary.
  const stops = [
    ['東京IC', 0, '関東'], ['宇都宮IC', 140, '関東'], ['仙台宮城IC', 220, '東北'],
    ['盛岡IC', 180, '東北'], ['青森IC', 180, '東北'], ['大沼公園IC', 180, '北海道'],
    ['札幌南IC', 260, '北海道'], ['旭川鷹栖IC', 160, '北海道'], ['帯広音更IC', 220, '北海道'],
    ['苫小牧東IC', 200, '北海道'], ['八戸IC', 280, '東北'], ['秋田中央IC', 230, '東北'],
    ['新潟中央IC', 280, '北陸'], ['富山IC', 250, '北陸'], ['金沢東IC', 70, '北陸'],
    ['京都南IC', 250, '近畿'], ['吹田IC', 50, '近畿'], ['松原IC', 40, '近畿'],
    ['郡山IC', 30, '近畿'], ['天理IC', 20, '近畿'], ['和歌山IC', 110, '近畿'],
    ['南紀田辺IC', 80, '近畿'], ['神戸西IC', 170, '近畿'],
    ['徳島IC', 110, '四国'], ['高松中央IC', 70, '四国'], ['高知IC', 130, '四国'],
    ['松山IC', 150, '四国'], ['広島IC', 170, '中国'], ['下関IC', 200, '中国'],
    ['福岡IC', 90, '九州'], ['長崎IC', 160, '九州'], ['熊本IC', 200, '九州'],
    ['鹿児島IC', 180, '九州'], ['那覇IC', 500, '沖縄'], ['許田IC', 60, '沖縄'],
    ['宮崎IC', 500, '九州'], ['大分IC', 200, '九州'], ['門司IC', 170, '九州'],
    ['岡山IC', 350, '中国'], ['大津IC', 220, '近畿'], ['彦根IC', 60, '近畿'],
    ['米原IC', 20, '近畿'], ['名古屋IC', 90, '東海'],
    ['浜松IC', 100, '東海'], ['静岡IC', 80, '東海'], ['御殿場IC', 90, '東海'], ['東京IC', 90, '東京・ゴール']
  ];
  let km = 0, difficulty = 1;
  const route = Object.freeze(stops.map(([name, length, region], index) => {
    // Count each regional leg, including return visits, but not the final goal.
    if (index > 0 && index < stops.length - 1 && region !== stops[index - 1][2]) difficulty++;
    return Object.freeze({ name, km: km += length, region, difficulty });
  }));
  const lanes = [110, 210, 310];
  class Run {
    constructor(random = Math.random) { this.random = random; this.reset(); }
    reset() {
      this.distance = 0; this.lane = 1; this.x = lanes[1]; this.cars = [];
      this.phase = 'ready'; this.spawnIn = .8; this.checkpoint = 0;
    }
    start() { this.reset(); this.phase = 'running'; }
    get completedLaps() { return Math.floor(this.distance / route.at(-1).km); }
    get lapDistance() { return this.distance % route.at(-1).km; }
    get difficulty() { return this.completedLaps > 0 ? 14 : route[this.checkpoint].difficulty; }
    get trafficSpeed() { return 255 + (this.difficulty - 1) * 10; }
    get spawnInterval() { return Math.max(1.4, 1.8 - (this.difficulty - 1) * .025); }
    get doubleCarChance() { return Math.min(.75, .25 + (this.difficulty - 1) * .035); }
    move(direction) { if (this.phase === 'running') this.lane = Math.max(0, Math.min(2, this.lane + direction)); }
    pause() { if (this.phase === 'running') this.phase = 'paused'; }
    resume() { if (this.phase === 'paused') this.phase = 'running'; }
    update(dt) {
      if (this.phase !== 'running' || !Number.isFinite(dt) || dt <= 0) return;
      // Substeps prevent tunnelling and keep collision checks ahead of goal detection.
      let remaining = Math.min(dt, .1);
      while (remaining > 0 && this.phase === 'running') {
        const step = Math.min(remaining, 1 / 120);
        remaining -= step;
        this.step(step);
      }
    }
    step(dt) {
      const delta = lanes[this.lane] - this.x;
      this.x += Math.sign(delta) * Math.min(Math.abs(delta), 680 * dt);
      const speed = this.trafficSpeed;
      this.cars.forEach(car => { car.y += speed * dt; });
      if (this.cars.some(car => Math.abs(car.x - this.x) < 37 && Math.abs(car.y - 460) < 65)) {
        this.phase = 'crashed';
        return;
      }
      this.cars = this.cars.filter(car => car.y < 650);
      const previousLaps = this.completedLaps;
      this.distance += 8 * dt;
      // Keep traffic, lane position and spawn timing continuous across Tokyo.
      if (this.completedLaps !== previousLaps) this.checkpoint = 0;
      while (this.checkpoint + 1 < route.length && this.lapDistance >= route[this.checkpoint + 1].km) this.checkpoint++;
      this.spawnIn -= dt;
      if (this.spawnIn <= 0) {
        // Each wave has at least one open lane and enough spacing to cross two lanes.
        const safe = Math.floor(this.random() * 3);
        const blocked = [0, 1, 2].filter(lane => lane !== safe);
        if (this.random() >= this.doubleCarChance) blocked.splice(1, 1);
        blocked.forEach(lane => this.cars.push({ x: lanes[lane], y: -70, color: this.random() < .5 ? '#e9a563' : '#d8e5f1' }));
        this.spawnIn = this.spawnInterval;
      }
    }
  }
  window.HighwayGame = Object.freeze({ Run, route });
})();
