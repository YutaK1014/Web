(() => {
    const prefix = 'mouke-memo:draft:v1:';
    const read = key => {
        const raw = localStorage.getItem(prefix + key);
        if (raw === null) return null;
        const draft = JSON.parse(raw);
        if (draft?.version !== 1 || typeof draft.revision !== 'string' ||
            typeof draft.savedAt !== 'number' || !Number.isFinite(draft.savedAt) ||
            !draft.values || typeof draft.values !== 'object' || Array.isArray(draft.values) ||
            !Object.values(draft.values).every(value => typeof value === 'string' || typeof value === 'boolean')) {
            throw new Error('Invalid draft');
        }
        return draft;
    };

    document.querySelectorAll('[data-saved-draft]').forEach(marker => {
        try {
            const draft = read(marker.dataset.savedDraft);
            // A different tab may have saved a newer draft while the request was in flight.
            if (draft && draft.revision === marker.dataset.savedRevision) {
                localStorage.removeItem(prefix + marker.dataset.savedDraft);
            }
        } catch {
            const warning = document.createElement('p');
            warning.className = 'hint';
            warning.textContent = '登録は完了しましたが、ブラウザーの下書きを削除できませんでした。入力画面で削除してください。';
            marker.after(warning);
        }
    });

    document.querySelectorAll('form[data-draft-key]').forEach(form => {
        const key = form.dataset.draftKey;
        const controls = form.querySelector('.draft-controls');
        const status = controls.querySelector('[data-draft-status]');
        const restore = controls.querySelector('[data-draft-restore]');
        const discard = controls.querySelector('[data-draft-discard]');
        const fields = Array.from(form.elements).filter(field => field.name &&
            ['INPUT', 'SELECT', 'TEXTAREA'].includes(field.tagName) &&
            !['hidden', 'file', 'submit', 'button', 'reset', 'password'].includes(field.type));
        const revision = document.createElement('input');
        revision.type = 'hidden';
        revision.name = 'draftRevision';
        form.append(revision);
        controls.hidden = false;

        const fail = () => {
            status.textContent = '下書きを読み書きできません。ブラウザーの保存設定や空き容量を確認してください。現在の入力内容はそのまま登録できます。';
            discard.hidden = false;
        };
        function offer() {
            try {
                const draft = read(key);
                restore.hidden = discard.hidden = !draft;
                status.textContent = draft
                    ? '保存済みの下書きがあります（' + new Date(draft.savedAt).toLocaleString('ja-JP') + '）。復元すると入力欄を置き換えます。新しく入力すると下書きを上書きします。'
                    : '入力すると下書きを自動保存します。';
            } catch { fail(); }
        }
        function save() {
            try {
                const values = Object.fromEntries(fields.map(field => [field.name,
                    field.type === 'checkbox' ? field.checked : field.value]));
                const draft = { version: 1, savedAt: Date.now(),
                    revision: Date.now() + '-' + Math.random().toString(36).slice(2), values };
                localStorage.setItem(prefix + key, JSON.stringify(draft));
                revision.value = draft.revision;
                status.textContent = '下書きを保存しました（' + new Date(draft.savedAt).toLocaleTimeString('ja-JP') + '）。';
                restore.hidden = true;
                discard.hidden = false;
            } catch {
                revision.value = '';
                fail();
            }
        }
        restore.addEventListener('click', () => {
            try {
                const draft = read(key);
                if (!draft) { offer(); return; }
                fields.forEach(field => {
                    if (!Object.hasOwn(draft.values, field.name)) return;
                    const value = draft.values[field.name];
                    if (field.type === 'checkbox' && typeof value === 'boolean') field.checked = value;
                    else if (field.type !== 'checkbox' && typeof value === 'string') field.value = value;
                });
                revision.value = draft.revision;
                form.dispatchEvent(new Event('draftrestored'));
                status.textContent = '下書きを復元しました。内容を確認して登録してください。写真は必要に応じて選び直してください。';
                restore.hidden = true;
            } catch { fail(); }
        });
        discard.addEventListener('click', () => {
            try {
                localStorage.removeItem(prefix + key);
                revision.value = '';
                restore.hidden = discard.hidden = true;
                status.textContent = '下書きを削除しました。入力欄の内容は残しています。次に入力すると再び自動保存します。';
            } catch { fail(); }
        });
        form.addEventListener('input', save);
        form.addEventListener('change', save);
        // Keep the draft through validation, server errors and network failures.
        form.addEventListener('submit', save);
        window.addEventListener('storage', event => {
            if (event.key === prefix + key || event.key === null) offer();
        });
        offer();
    });
})();
