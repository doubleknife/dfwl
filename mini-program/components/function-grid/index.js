Component({ properties: { actions: { type: Array, value: [] } }, methods: { open(e) { this.triggerEvent('open', { url: e.currentTarget.dataset.url }); } } });
