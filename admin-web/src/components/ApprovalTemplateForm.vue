<script setup>
import {
  attributionLabels, attributionTypes, categoryLabels, evidenceLabels, evidenceRequirements,
  expenseLabels, expenseTypes, inputModes, modeLabels, templateCategories
} from '../views/approvalTemplateModel.js';

const props = defineProps({
  form: { type: Object, required: true },
  roles: { type: Array, required: true },
  users: { type: Array, required: true },
  creating: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  submitLabel: { type: String, required: true }
});
const emit = defineEmits(['submit', 'cancel']);

function categoryChanged() {
  if (props.form.category === 'TIRE') props.form.templateCode = 'TIRE_REQUEST';
  else if (props.form.templateCode === 'TIRE_REQUEST') props.form.templateCode = '';
}

function addNode() {
  props.form.nodes.push({ nodeOrder: props.form.nodes.length + 1, nodeName: '',
    positionId: '', approverUserId: '', allowReturn: true });
}

function removeNode(index) {
  props.form.nodes.splice(index, 1);
  props.form.nodes.forEach((node, nodeIndex) => { node.nodeOrder = nodeIndex + 1; });
}
</script>

<template>
  <form class="template-form" @submit.prevent="emit('submit')">
    <fieldset v-if="creating" class="template-fieldset">
      <legend>模板身份</legend>
      <div class="template-fields">
        <label>模板类别
          <select v-model="form.category" aria-label="模板类别" @change="categoryChanged">
            <option v-for="category in templateCategories" :key="category" :value="category">
              {{ categoryLabels[category] }} · {{ category }}
            </option>
          </select>
        </label>
        <label>模板代码
          <input v-model.trim="form.templateCode" aria-label="模板代码" required
            :readonly="form.category === 'TIRE'" placeholder="例如 EXPENSE_WATER" />
        </label>
      </div>
      <p class="muted">模板代码创建后保持稳定；换胎模板固定为 TIRE_REQUEST。</p>
    </fieldset>

    <fieldset class="template-fieldset">
      <legend>版本基本配置</legend>
      <div class="template-fields">
        <label>流程名称
          <input v-model.trim="form.flowName" aria-label="流程名称" required placeholder="供管理员识别的版本名称" />
        </label>
        <label>申请依据
          <select v-model="form.evidenceRequirement" aria-label="申请依据">
            <option v-for="requirement in evidenceRequirements" :key="requirement" :value="requirement">
              {{ evidenceLabels[requirement] }} · {{ requirement }}
            </option>
          </select>
        </label>
      </div>
    </fieldset>

    <fieldset v-if="form.category === 'EXPENSE'" class="template-fieldset">
      <legend>费用版本配置</legend>
      <div class="template-fields">
        <label>费用类型
          <select v-model="form.expenseType" aria-label="费用类型" required>
            <option value="">请选择</option>
            <option v-for="expenseType in expenseTypes" :key="expenseType" :value="expenseType">
              {{ expenseLabels[expenseType] || expenseType }} · {{ expenseType }}
            </option>
          </select>
        </label>
      </div>
      <div class="template-fields">
        <div><strong>录入方式</strong>
          <label v-for="mode in inputModes" :key="mode" class="template-check">
            <input v-model="form.inputModes" type="checkbox" :value="mode" />{{ modeLabels[mode] }} · {{ mode }}
          </label>
          <small class="muted">这里只配置允许方式；Excel 文件解析属于后续工作。</small>
        </div>
        <div><strong>费用归属</strong>
          <label v-for="attribution in attributionTypes" :key="attribution" class="template-check">
            <input v-model="form.attributionTypes" type="checkbox" :value="attribution" />
            {{ attributionLabels[attribution] }} · {{ attribution }}
          </label>
        </div>
      </div>
    </fieldset>

    <fieldset class="template-fieldset">
      <legend>允许发起角色</legend>
      <p v-if="form.category === 'TIRE'" class="muted">
        换胎使用专用入口；实际资格仍由 tire:request 与 TireAccessPolicy 判断，此处角色不是授权开关。
      </p>
      <div v-if="roles.length" class="template-options">
        <label v-for="role in roles" :key="role.id" class="template-check">
          <input v-model="form.initiatorRoleIds" type="checkbox" :value="role.id" />
          {{ role.roleName }} · {{ role.roleCode }}
        </label>
      </div>
      <p v-else class="empty">未取得角色列表，无法配置发起角色。</p>
    </fieldset>

    <fieldset class="template-fieldset">
      <legend>审批节点</legend>
      <div v-for="(node, index) in form.nodes" :key="index" class="template-node">
        <strong>第 {{ index + 1 }} 节点</strong>
        <label>节点名称<input v-model.trim="node.nodeName" :aria-label="`第 ${index + 1} 节点名称`" required /></label>
        <label>审批人
          <select v-model.number="node.approverUserId" :aria-label="`第 ${index + 1} 节点审批人`" required>
            <option value="">请选择用户</option>
            <option v-if="node.approverUserId && !users.some(user => user.id === Number(node.approverUserId))"
              :value="Number(node.approverUserId)">用户 #{{ node.approverUserId }}</option>
            <option v-for="user in users" :key="user.id" :value="user.id">
              {{ user.phone }} · {{ user.roleName || user.roleCode }}
            </option>
          </select>
        </label>
        <label>岗位 ID（可选）<input v-model="node.positionId" type="number" min="1" :aria-label="`第 ${index + 1} 岗位 ID`" /></label>
        <label class="template-check"><input v-model="node.allowReturn" type="checkbox" />允许打回</label>
        <button type="button" :disabled="form.nodes.length === 1" @click="removeNode(index)">移除节点</button>
      </div>
      <button type="button" @click="addNode">增加节点</button>
    </fieldset>

    <div class="row-actions">
      <button class="primary" type="submit" :disabled="busy">{{ submitLabel }}</button>
      <button type="button" @click="emit('cancel')">取消</button>
    </div>
  </form>
</template>
