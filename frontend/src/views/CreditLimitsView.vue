<template>
  <div class="page">
    <h2 class="page-title">会员额度</h2>
    <p class="page-desc">
      按会员 + 币种维护额度上限；新建义务时，付款方同币种 OPEN 义务合计加本次金额不得超过上限。未配置额度视为不限额。
    </p>

    <div class="card-panel" style="margin-bottom:16px" v-loading="saving">
      <el-form :model="form" label-width="90px" inline @submit.prevent>
        <el-form-item label="会员">
          <el-select v-model="form.memberId" filterable placeholder="选择会员" style="width:200px">
            <el-option v-for="m in members" :key="m.memberId" :label="m.name" :value="m.memberId" />
          </el-select>
        </el-form-item>
        <el-form-item label="币种">
          <el-select v-model="form.currency" style="width:120px">
            <el-option label="USD" value="USD" />
            <el-option label="CNY" value="CNY" />
            <el-option label="EUR" value="EUR" />
          </el-select>
        </el-form-item>
        <el-form-item label="额度上限">
          <el-input-number v-model="form.limitAmount" :min="0" :precision="8" :controls="false" style="width:200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!auth.isOperator" @click="saveLimit(null)">设置额度</el-button>
        </el-form-item>
        <el-form-item v-if="!auth.isOperator">
          <el-tag type="info">只读账号仅可查看</el-tag>
        </el-form-item>
      </el-form>
    </div>

    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="card-panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="会员" min-width="160">
          <template #default="{ row }">{{ row.memberName || nameOf(row.memberId) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column label="额度上限" width="220">
          <template #default="{ row }">
            <el-input-number
              v-model="row.limitAmount"
              :min="0"
              :precision="8"
              :controls="false"
              size="small"
              style="width:150px"
              :disabled="!auth.isOperator"
            />
          </template>
        </el-table-column>
        <el-table-column label="已用（OPEN）" width="160">
          <template #default="{ row }">{{ formatAmount(usage[row.key]?.usedAmount) }}</template>
        </el-table-column>
        <el-table-column label="可用额度" min-width="160">
          <template #default="{ row }">{{ formatAmount(usage[row.key]?.availableAmount) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :disabled="!auth.isOperator"
              :loading="savingKey === row.key"
              @click="saveLimit(row)"
            >保存</el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="!auth.isOperator"
              @click="removeLimit(row)"
            >删除（不限额）</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="尚未配置任何额度，默认全部不限额" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const members = ref([])
const rows = ref([])
const usage = ref({})
const loading = ref(false)
const saving = ref(false)
const savingKey = ref('')

const form = reactive({
  memberId: '',
  currency: 'USD',
  limitAmount: 100000
})

function keyOf(memberId, currency) {
  return `${memberId}#${currency}`
}

function nameOf(id) {
  return members.value.find((m) => m.memberId === id)?.name || id
}

function formatAmount(v) {
  if (v === null || v === undefined) return '-'
  return Number(v).toLocaleString('en-US', { maximumFractionDigits: 8 })
}

async function loadMembers() {
  const { data } = await api.get('/members')
  members.value = data
}

async function loadUsage() {
  const entries = await Promise.all(
    rows.value.map(async (row) => {
      const { data } = await api.get('/credit-limits/usage', {
        params: { memberId: row.memberId, currency: row.currency }
      })
      return [row.key, data]
    })
  )
  usage.value = Object.fromEntries(entries)
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/credit-limits')
    rows.value = data.map((r) => ({ ...r, key: keyOf(r.memberId, r.currency) }))
    await loadUsage()
  } finally {
    loading.value = false
  }
}

async function saveLimit(row) {
  const payload = row
    ? { memberId: row.memberId, currency: row.currency, limitAmount: row.limitAmount }
    : { memberId: form.memberId, currency: form.currency, limitAmount: form.limitAmount }
  if (!payload.memberId) {
    ElMessage.warning('请选择会员')
    return
  }
  if (payload.limitAmount === null || payload.limitAmount === undefined || payload.limitAmount < 0) {
    ElMessage.warning('额度上限需为非负数')
    return
  }
  if (row) savingKey.value = row.key
  else saving.value = true
  try {
    await api.put('/credit-limits', payload)
    ElMessage.success('额度已保存')
    await load()
  } finally {
    savingKey.value = ''
    saving.value = false
  }
}

async function removeLimit(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${nameOf(row.memberId)} 的 ${row.currency} 额度配置？删除后该会员在此币种上不限额。`,
      '删除额度',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await api.delete('/credit-limits', { params: { memberId: row.memberId, currency: row.currency } })
  ElMessage.success('额度已删除，恢复不限额')
  await load()
}

onMounted(async () => {
  await loadMembers()
  await load()
})
</script>
