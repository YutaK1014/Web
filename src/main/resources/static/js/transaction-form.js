(() => {
    const form = document.querySelector('.transaction-form');
    const price = document.getElementById('sellingPrice');
    const shipping = document.getElementById('shippingCost');
    const purchase = document.getElementById('purchasePrice');
    const profit = document.getElementById('profit');
    const platform = document.getElementById('marketplace');
    const customField = document.getElementById('custom-platform-field');
    const customInput = document.getElementById('customMarketplace');
    const photo = document.getElementById('photo');
    const preview = document.getElementById('photo-preview');
    const photoError = document.getElementById('photo-error');
    let photoUrl;

    function updateProfit() {
        const valid = price.value !== '' && shipping.value !== '' &&
            [price, shipping, purchase].every(input => input.validity.valid);
        const amount = valid ? Number(price.value) - Number(shipping.value) - Number(purchase.value || 0) : null;
        profit.textContent = amount === null ? '— 円' : amount.toLocaleString('ja-JP') + '円';
        profit.classList.toggle('negative', amount !== null && amount < 0);
    }
    function updatePlatform() {
        const other = platform.value === 'その他';
        customField.hidden = !other;
        customInput.disabled = !other;
    }
    [price, shipping, purchase].forEach(input => input.addEventListener('input', updateProfit));
    platform.addEventListener('change', updatePlatform);
    photo.addEventListener('change', () => {
        if (photoUrl) URL.revokeObjectURL(photoUrl);
        preview.hidden = true;
        preview.removeAttribute('src');
        photoError.textContent = '';
        photo.setCustomValidity('');
        const file = photo.files[0];
        if (!file) return;
        if (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)) {
            const message = '写真は5MB以下のJPEGまたはPNG形式を選択してください。';
            photoError.textContent = message;
            photo.setCustomValidity(message);
            return;
        }
        photoUrl = URL.createObjectURL(file);
        preview.src = photoUrl;
        preview.hidden = false;
    });
    form.addEventListener('submit', () => {
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        button.textContent = '登録中…';
    });
    window.addEventListener('pageshow', () => {
        const button = form.querySelector('button[type="submit"]');
        button.disabled = false;
        button.textContent = '登録する';
        updateProfit();
        updatePlatform();
    });
    updateProfit();
    updatePlatform();
})();
