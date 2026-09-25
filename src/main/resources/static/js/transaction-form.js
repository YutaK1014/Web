(() => {
    const form = document.querySelector('.transaction-form');
    if (!form) return;
    const price = document.getElementById('sellingPrice');
    const shipping = document.getElementById('shippingCost');
    const purchase = document.getElementById('purchasePrice');
    const rate = document.getElementById('feeRate');
    const profit = document.getElementById('profit');
    const fee = document.getElementById('selling-fee');
    const platform = document.getElementById('marketplace');
    const customField = document.getElementById('custom-platform-field');
    const customInput = document.getElementById('customMarketplace');
    const photo = document.getElementById('photo');
    const preview = document.getElementById('photo-preview');
    const photoError = document.getElementById('photo-error');
    const rounding = form.dataset.rounding;
    const modes = { DOWN: '切り捨て', HALF_UP: '四捨五入', UP: '切り上げ' };
    document.getElementById('rounding-help').textContent = modes[rounding]
        ? '手数料の1円未満：' + modes[rounding] : '設定画面で手数料の端数処理を選択してください。';
    let photoUrl;
    let otherRate = platform.value === 'その他' ? rate.value : '';
    function updateProfit() {
        const valid = price.value !== '' && shipping.value !== '' && rate.value !== '' && modes[rounding] &&
            [price, shipping, purchase, rate].every(input => input.validity.valid);
        if (!valid) { profit.textContent = fee.textContent = '— 円'; profit.classList.remove('negative'); return; }
        // Integer arithmetic avoids floating-point rounding differences from Java BigDecimal.
        const basisPoints = Math.round(Number(rate.value) * 100);
        const numerator = Number(price.value) * basisPoints;
        let sellingFee = Math.floor(numerator / 10000);
        const remainder = numerator % 10000;
        if ((rounding === 'UP' && remainder > 0) || (rounding === 'HALF_UP' && remainder >= 5000)) sellingFee++;
        const amount = Number(price.value) - sellingFee - Number(shipping.value) - Number(purchase.value || 0);
        fee.textContent = sellingFee.toLocaleString('ja-JP') + '円';
        profit.textContent = amount.toLocaleString('ja-JP') + '円';
        profit.classList.toggle('negative', amount < 0);
    }
    function updatePlatform() {
        const other = platform.value === 'その他';
        customField.hidden = !other;
        customInput.disabled = !other;
        customInput.required = other;
        rate.readOnly = !other;
        rate.required = other;
        rate.value = other ? otherRate : (platform.selectedOptions[0]?.dataset.rate || '');
        document.getElementById('rate-help').textContent = other ? '0〜100%で入力してください。'
            : rate.value === '' ? 'このサイトの手数料率を設定画面で設定してください。' : '設定画面の手数料率を使用します。';
        updateProfit();
    }
    [price, shipping, purchase].forEach(input => input.addEventListener('input', updateProfit));
    rate.addEventListener('input', () => { if (platform.value === 'その他') otherRate = rate.value; updateProfit(); });
    platform.addEventListener('change', updatePlatform);
    photo.addEventListener('change', () => {
        if (photoUrl) URL.revokeObjectURL(photoUrl);
        preview.hidden = true; preview.removeAttribute('src'); photoError.textContent = ''; photo.setCustomValidity('');
        const file = photo.files[0];
        if (!file) return;
        if (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
            const message = '写真は5MB以下のJPEG・PNG・WebP形式を選択してください。';
            photoError.textContent = message; photo.setCustomValidity(message); return;
        }
        photoUrl = URL.createObjectURL(file); preview.src = photoUrl; preview.hidden = false;
    });
    const button = form.querySelector('button[type="submit"]');
    const label = button.textContent;
    form.addEventListener('submit', () => { button.disabled = true; button.textContent = '保存中…'; });
    window.addEventListener('pageshow', () => { button.disabled = false; button.textContent = label; updatePlatform(); });
    updatePlatform();
})();
