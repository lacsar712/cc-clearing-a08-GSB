<template>
  <div class="page">
    <h2 class="page-title">义务录入</h2>
    <p class="page-desc">录入应付义务并按币种/交割日/状态筛选</p>

    <div class="card-panel" style="margin-bottom:16px">
      <el-form :model="form" label-width="110px" @submit.prevent>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="付款方">
              <el-select v-model="form.payerMemberId" filterable style="width:100%" :disabled="!auth.isOperator">
                <el-option v-for="m in activeMembers" :key="m.memberId" :label="m.name" :value="m.memberId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收款方">
              <el-select v-model="form.payeeMemberId" filterable style="width:100%" :disabled="!auth.isOperator">
                <el-option v-for="m in activeMembers" :key="m.memberId" :label="m.name" :value="m.memberId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种">
              <el-input v-model="form.currency" :disabled="!auth.isOperator" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="金额">
              <el-input-number v-model="form.amount" :min="0.00000001" :precision="8" :controls="false" style="width:100%" :disabled="!auth.isOperator" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="交易日">
              <el-date-picker v-model="form.tradeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="!auth.isOperator" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="交割日">
              <el-date-picker v-model="form.settleDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="!auth.isOperator" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="limitExceeded"
          type="error"
          :closable="false"
          show-icon
          style="margin:0 0 12px"
          title="额度超限，无法新建义务"
          :description="limitMsg"
        />
        <el-alert
          v-else-if="limitInfo"
          type="info"
          :closable="false"
          show-icon
          style="margin:0 0 12px"
          :title="limitInfo"
        />
        <el-button
          type="primary"
          :disabled="!auth.isOperator || limitExceeded"
          :loading="saving"
          @click="create"
        >提交义务</el-button>
      </el-form>
    </div>

    <div class="toolbar">
      <el-select v-model="filters.currency" clearable placeholder="币种" style="width:120px">
        <el-option label="USD" value="USD" />
        <el-option label="CNY" value="CNY" />
        <el-option label="EUR" value="EUR" />
      </el-select>
      <el-date-picker v-model="filters.settleDate" type="date" value-format="YYYY-MM-DD" placeholder="交割日" />
      <el-select v-model="filters.status" clearable placeholder="状态" style="width:140px">
        <el-option v-for="s in ['OPEN','NETTED','SETTLED','CANCELLED']" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <div class="card-panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="obligationId" label="义务 ID" min-width="200">
          <template #default="{ row }"><span class="mono">{{ row.obligationId }}</span></template>
        </el-table-column>
        <el-table-column label="付款方" min-width="120">
          <template #default="{ row }">{{ nameOf(row.payerMemberId) }}</template>
        </el-table-column>
        <el-table-column label="收款方" min-width="120">
          <template #default="{ row }">{{ nameOf(row.payeeMemberId) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column prop="amount" label="金额" width="140" />
        <el-table-column prop="settleDate" label="交割日" width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const members = ref([])
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const usage = ref(null)
const today = new Date().toISOString().slice(0, 10)

const form = reactive({
  payerMemberId: '',
  payeeMemberId: '',
  currency: 'USD',
  amount: 10000,
  tradeDate: today,
  settleDate: today
})

const filters = reactive({
  currency: 'USD',
  settleDate: today,
  status: 'OPEN'
})

const activeMembers = computed(() => members.value.filter((m) => m.status === 'ACTIVE'))
const memberMap = computed(() => Object.fromEntries(members.value.map((m) => [m.memberId, m.name])))

const limitExceeded = computed(() => {
  if (!usage.value || usage.value.limitAmount === null || usage.value.limitAmount === undefined) return false
  const amount = Number(form.amount) || 0
  return Number(usage.value.usedAmount) + amount > Number(usage.value.limitAmount)
})

const limitMsg = computed(() => {
  if (!usage.value) return ''
  const u = usage.value
  return `付款方 ${u.currency} 额度上限 ${fmt(u.limitAmount)}，OPEN 义务已占用 ${fmt(u.usedAmount)}，本次申请 ${fmt(form.amount)}，合计超出 ${fmt(Number(u.usedAmount) + (Number(form.amount) || 0) - Number(u.limitAmount))}。请调低金额或前往「会员额度」页调整上限。`
})

const limitInfo = computed(() => {
  if (!form.payerMemberId || !form.currency) return ''
  if (!usage.value || usage.value.limitAmount === null || usage.value.limitAmount === undefined) {
    return `${form.currency} 未配置额度，当前不限额`
  }
  return `付款方 ${usage.value.currency} 额度：上限 ${fmt(usage.value.limitAmount)} / 已用 ${fmt(usage.value.usedAmount)} / 提交后可用 ${fmt(Number(usage.value.availableAmount) - (Number(form.amount) || 0))}`
})

function fmt(v) {
  if (v === null || v === undefined || Number.isNaN(Number(v))) return '-'
  return Number(v).toLocaleString('en-US', { maximumFractionDigits: 8 })
}

async function loadUsage() {
  usage.value = null
  if (!form.payerMemberId || !form.currency) return
  try {
    const { data } = await api.get('/credit-limits/usage', {
      params: { memberId: form.payerMemberId, currency: form.currency }
    })
    usage.value = data
  } catch {
    // 静默：全局拦截器已提示错误
  }
}

watch(
  () => [form.payerMemberId, form.currency],
  () => loadUsage()
)

function nameOf(id) {
  return memberMap.value[id] || id
}

async function loadMembers() {
  const { data } = await api.get('/members')
  members.value = data
}

async function load() {
  loading.value = true
  try {
    const params = {}
    if (filters.currency) params.currency = filters.currency
    if (filters.settleDate) params.settleDate = filters.settleDate
    if (filters.status) params.status = filters.status
    const { data } = await api.get('/obligations', { params })
    rows.value = data
  } finally {
    loading.value = false
  }
}

async function create() {
  if (limitExceeded.value) {
    ElMessage.error('额度超限，已拦截本次新建义务')
    return
  }
  saving.value = true
  try {
    await api.post('/obligations', { ...form })
    ElMessage.success('义务已录入')
    await load()
    await loadUsage()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadMembers()
  await load()
})
</script>
