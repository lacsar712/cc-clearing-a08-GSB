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
        <div v-if="form.payerMemberId && form.currency" class="credit-tip">
          <el-alert
            v-if="!credit.configured"
            type="info"
            :closable="false"
            show-icon
            :title="`付款方 ${form.currency.toUpperCase()} 未设置额度上限，当前不受约束（可在「会员额度」页设置）`"
          />
          <el-alert
            v-else-if="!credit.exceeded"
            :type="credit.remaining < credit.amount ? 'warning' : 'success'"
            :closable="false"
            show-icon
            :title="`额度上限 ${fmt(credit.limit)} ｜ 已用 ${fmt(credit.used)} ｜ 本笔 ${fmt(credit.amount)} ｜ 提交后合计 ${fmt(credit.projected)} ｜ 剩余可用 ${fmt(credit.remaining)}`"
          />
          <el-alert
            v-else
            type="error"
            :closable="false"
            show-icon
            :title="`超过额度上限，已拦截：提交后合计 ${fmt(credit.projected)} > 上限 ${fmt(credit.limit)}（已用 ${fmt(credit.used)} + 本笔 ${fmt(credit.amount)}）。请调低金额或在「会员额度」页调整上限`"
          />
        </div>
        <el-button
          type="primary"
          :disabled="!auth.isOperator || credit.exceeded"
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const members = ref([])
const creditRows = ref([])
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
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

function fmt(v) {
  return Number(v || 0).toLocaleString(undefined, { maximumFractionDigits: 8 })
}

// 当前付款方 + 币种的额度状态（只约束新建义务）
const credit = computed(() => {
  const zero = { configured: false, exceeded: false, limit: 0, used: 0, amount: 0, projected: 0, remaining: 0 }
  if (!form.payerMemberId || !form.currency) return zero
  const ccy = String(form.currency).toUpperCase()
  const row = creditRows.value.find(
    (r) => r.memberId === form.payerMemberId && r.currency === ccy
  )
  if (!row) return zero
  const limit = Number(row.limitAmount)
  const used = Number(row.usedAmount || 0)
  const amount = Number(form.amount || 0)
  const projected = used + amount
  return {
    configured: true,
    limit,
    used,
    amount,
    projected,
    remaining: limit - projected,
    exceeded: projected > limit
  }
})

function nameOf(id) {
  return memberMap.value[id] || id
}

async function loadMembers() {
  const { data } = await api.get('/members')
  members.value = data
}

async function loadCreditLimits() {
  const { data } = await api.get('/members/credit-limits')
  creditRows.value = data
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
  saving.value = true
  try {
    await api.post('/obligations', { ...form })
    ElMessage.success('义务已录入')
    await Promise.all([load(), loadCreditLimits()])
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadMembers()
  await Promise.all([load(), loadCreditLimits()])
})
</script>

<style scoped>
.credit-tip {
  margin: 0 0 14px 110px;
}
</style>
