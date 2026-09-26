Component({ properties: { kind: { type: String, value: 'empty' }, message: String }, methods: { retry() { this.triggerEvent('retry'); } } });
