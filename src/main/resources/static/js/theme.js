(() => {
    const button = document.getElementById('theme-toggle');
    if (!button) return;
    function apply(dark) {
        document.documentElement.classList.toggle('dark', dark);
        button.setAttribute('aria-pressed', String(dark));
        button.textContent = dark ? 'ライトモード' : 'ダークモード';
    }
    try { apply(localStorage.getItem('theme') === 'dark'); } catch (_) {}
    button.addEventListener('click', () => {
        const dark = !document.documentElement.classList.contains('dark');
        apply(dark);
        try { localStorage.setItem('theme', dark ? 'dark' : 'light'); } catch (_) {}
    });
})();
